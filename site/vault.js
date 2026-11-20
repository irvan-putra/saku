"use strict";
/* Local-only KDBX vault page. Crypto is kdbxweb (WebCrypto) and hash-wasm Argon2; nothing here implements a primitive. */
const $ = id => document.getElementById(id);
const kw = window.kdbxweb;
const LIMITS = {softMemKiB: 256 * 1024, memKiB: 1024 * 1024, iterations: 100, parallelism: 16}; /* hostile-KDF guards; values are a proposal for review */
let lastKey = null; /* copy of the last Argon2 output, used only for the file-ending check below */
kw.CryptoEngine.setArgon2Impl(async (password, salt, memory, iterations, length, parallelism, type) => {
  if (memory > LIMITS.memKiB || iterations > LIMITS.iterations || parallelism > LIMITS.parallelism) throw new Error("This file asks for more key-stretching work than this page allows (memory " + Math.round(memory / 1024) + " MiB, " + iterations + " rounds). It was not opened.");
  if (memory > LIMITS.softMemKiB && !confirm("This vault needs " + Math.round(memory / 1024) + " MiB of memory to unlock. That can crash a phone or a small browser tab. Continue?")) throw new Error("Cancelled: the vault needs " + Math.round(memory / 1024) + " MiB to unlock.");
  const fn = type === 2 ? window.hashwasm.argon2id : window.hashwasm.argon2d;
  let out;
  try { out = await fn({password: new Uint8Array(password), salt: new Uint8Array(salt), parallelism, iterations, memorySize: memory, hashLength: length, outputType: "binary"}); }
  catch (x) { throw new Error("The browser could not allocate the memory this vault needs (" + Math.round(memory / 1024) + " MiB)."); }
  lastKey = out.slice();
  return out.buffer.slice(out.byteOffset, out.byteOffset + out.byteLength);
});
/* kdbxweb 2.1.1 reads the closing (empty) HMAC block of a KDBX 4 file but never checks it, nor what follows it.
   This re-checks it with the same keys and WebCrypto, so a cut or altered file ending is refused. */
async function checkFileEnd(buf, key) {
  const u = new Uint8Array(buf), d = new DataView(buf);
  if (u.length < 64 || d.getUint16(10, true) !== 4) return "skipped";
  let p = 12, seed = null;
  for (;;) { if (p + 5 > u.length) throw new Error("bad header"); const id = u[p], len = d.getUint32(p + 1, true); p += 5; if (p + len > u.length) throw new Error("bad header"); if (id === 4) seed = u.slice(p, p + len); p += len; if (id === 0) break; }
  if (!seed || seed.length !== 32 || !key) return "skipped";
  const cat = (...a) => { const o = new Uint8Array(a.reduce((n, x) => n + x.length, 0)); let q = 0; for (const x of a) { o.set(x, q); q += x.length; } return o; };
  const sha512 = async x => new Uint8Array(await crypto.subtle.digest("SHA-512", x));
  const hmacBase = await sha512(cat(seed, new Uint8Array(key), Uint8Array.of(1)));
  p += 64; let n = 0;
  for (;;) {
    if (p + 36 > u.length) throw new Error("truncated");
    const mac = u.slice(p, p + 32), len = d.getUint32(p + 32, true); p += 36;
    if (len === 0) {
      const idx = new Uint8Array(8); new DataView(idx.buffer).setUint32(0, n, true);
      const bk = await sha512(cat(idx, hmacBase)), k = await crypto.subtle.importKey("raw", bk, {name: "HMAC", hash: "SHA-256"}, false, ["verify"]);
      const ok = await crypto.subtle.verify("HMAC", k, mac, cat(idx, new Uint8Array(4)));
      if (!ok || p !== u.length) throw new Error("bad ending");
      return "ok";
    }
    p += len; n++;
  }
}
let db = null, fileBuf = null, cur = null, dirty = false, idle = null, clipT = null;
const cred = pw => new kw.Credentials(kw.ProtectedValue.fromString(pw));
const toAB = b => b instanceof ArrayBuffer ? b : b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength);
function setErr(m) { $("err").textContent = m || ""; }
function chk() { $("open").disabled = !(fileBuf && $("pw").value); }
$("file").onchange = async e => { const f = e.target.files[0]; fileBuf = null; $("fname").textContent = ""; if (f) { fileBuf = await f.arrayBuffer(); $("fname").textContent = " " + f.name; } chk(); };
$("pw").oninput = chk;
async function unlock(buf, pw) {
  setErr(""); $("open").disabled = true; $("demo").disabled = true; setErr("Unlocking...");
  try {
    lastKey = null; const ab = toAB(buf); db = await kw.Kdbx.load(ab, cred(pw));
    let note = "";
    try { if ((await checkFileEnd(ab, lastKey)) === "skipped") note = "The end of this file could not be integrity-checked (older file type)."; }
    catch (x) { db = null; throw new Error("The end of this file failed its integrity check. It may be damaged or altered, so it was not opened."); }
    finally { lastKey = null; }
    setErr(""); show(); $("integ").textContent = note; }
  catch (x) { db = null; lastKey = null; setErr(x && x.code === "InvalidKey" ? "Wrong password, or not a KeePass file." : (x && x.message) || "Could not open that file."); }
  $("pw").value = ""; $("demo").disabled = false; chk();
}
$("open").onclick = () => unlock(fileBuf, $("pw").value);
$("demo").onclick = async () => { try { const r = await fetch("demo.kdbx"); await unlock(await r.arrayBuffer(), "demo-not-real"); } catch (x) { setErr("Could not load the demo vault."); } };
$("create").onclick = async () => {
  const a = $("npw").value, b = $("npw2").value;
  if (a.length < 8) return setErr("Use at least 8 characters."); if (a !== b) return setErr("The two passwords differ.");
  try { db = kw.Kdbx.create(cred(a), $("nname").value || "My vault"); db.upgrade(); dirty = true; $("npw").value = $("npw2").value = ""; setErr(""); show(); } catch (x) { setErr(x.message); }
};
function entries(group, path, out) {
  const rb = db.meta.recycleBinUuid;
  for (const e of group.entries) out.push({e, path});
  for (const g of group.groups) { if (rb && g.uuid.equals(rb)) continue; entries(g, path ? path + " / " + g.name : g.name, out); }
  return out;
}
const fld = (e, k) => { const v = e.fields.get(k); return v == null ? "" : (typeof v === "string" ? v : v.getText()); };
function show() {
  $("locked").hidden = true; $("unlocked").hidden = false; $("vname").textContent = db.meta.name || "Vault"; render(); bump();
}
function render() {
  const q = $("q").value.toLowerCase(), ul = $("list"); ul.replaceChildren();
  $("dirty").textContent = dirty ? "Unsaved changes" : "";
  for (const {e, path} of entries(db.getDefaultGroup(), "", [])) {
    const t = fld(e, "Title"), u = fld(e, "UserName");
    if (q && !(t + " " + u + " " + fld(e, "URL")).toLowerCase().includes(q)) continue;
    const li = document.createElement("li"), s = document.createElement("small"); li.textContent = t || "(no title)"; s.textContent = [path, u].filter(Boolean).join(" · "); li.append(s);
    li.onclick = () => openEntry(e); ul.append(li);
  }
  if (!ul.children.length) { const li = document.createElement("li"); li.textContent = "No entries."; li.style.cursor = "default"; ul.append(li); }
}
function openEntry(e) {
  cur = e; $("detail").hidden = false; $("dtitle").textContent = e ? "Edit entry" : "New entry";
  $("f_t").value = e ? fld(e, "Title") : ""; $("f_u").value = e ? fld(e, "UserName") : ""; $("f_p").value = e ? fld(e, "Password") : ""; $("f_p").type = "password"; $("show").textContent = "Show";
  $("f_l").value = e ? fld(e, "URL") : ""; $("f_n").value = e ? fld(e, "Notes") : ""; $("dmsg").textContent = ""; $("del").hidden = !e; bump();
}
$("add").onclick = () => { cur = null; openEntry(null); };
$("show").onclick = () => { const p = $("f_p").type === "password"; $("f_p").type = p ? "text" : "password"; $("show").textContent = p ? "Hide" : "Show"; };
$("copy").onclick = async () => { try { await navigator.clipboard.writeText($("f_p").value); $("dmsg").textContent = "Copied. Clipboard clears in 30 s if the browser allows it."; clearTimeout(clipT); clipT = setTimeout(() => navigator.clipboard.writeText("").catch(() => {}), 30000); } catch { $("dmsg").textContent = "The browser blocked copying."; } };
$("gen").onclick = () => { /* random characters from the browser's CSPRNG, rejection-sampled to avoid bias */
  const set = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789-_.!@#%", lim = 256 - (256 % set.length); let s = "";
  while (s.length < 20) { const b = crypto.getRandomValues(new Uint8Array(32)); for (const x of b) if (x < lim && s.length < 20) s += set[x % set.length]; }
  $("f_p").value = s; $("f_p").type = "text"; $("show").textContent = "Hide";
};
$("apply").onclick = () => {
  let e = cur; if (!e) e = db.createEntry(db.getDefaultGroup());
  e.fields.set("Title", $("f_t").value); e.fields.set("UserName", $("f_u").value); e.fields.set("Password", kw.ProtectedValue.fromString($("f_p").value)); e.fields.set("URL", $("f_l").value); e.fields.set("Notes", $("f_n").value);
  e.times.update(); dirty = true; cur = e; $("dtitle").textContent = "Edit entry"; $("del").hidden = false; $("dmsg").textContent = "Applied. Use Save to download the updated file."; render(); bump();
};
$("del").onclick = () => { if (!cur || !confirm("Delete this entry? It moves to the vault's recycle bin.")) return; db.remove(cur); cur = null; dirty = true; $("detail").hidden = true; render(); bump(); };
$("q").oninput = render;
$("save").onclick = async () => {
  try { /* kdbxweb writes an empty value for icon, expanded and search flags on items that had none; KeePassXC rejects that. "null" is the KeePass default value. */
    const fixE = e => { if (e.icon === undefined) e.icon = 0; (e.history || []).forEach(fixE); };
    const fixG = g => { if (g.enableSearching === undefined) g.enableSearching = null; if (g.enableAutoType === undefined) g.enableAutoType = null; if (g.icon === undefined) g.icon = 48; if (g.expanded === undefined) g.expanded = true; g.entries.forEach(fixE); g.groups.forEach(fixG); }; db.groups.forEach(fixG);
    const buf = await db.save(), a = document.createElement("a"), u = URL.createObjectURL(new Blob([buf], {type: "application/octet-stream"}));
    a.href = u; a.download = ((db.meta.name || "vault").replace(/[^\w.-]+/g, "_") || "vault") + ".kdbx"; document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(u), 5000); dirty = false; render();
  } catch (x) { $("dmsg").textContent = "Could not save: " + x.message; }
};
function lock(force) {
  lastKey = null;
  if (dirty && !force && !confirm("You have unsaved changes. Lock anyway and lose them?")) return;
  db = null; cur = null; dirty = false; fileBuf = null; $("fname").textContent = ""; $("detail").hidden = true; $("list").replaceChildren(); $("q").value = ""; ["f_t", "f_u", "f_p", "f_l", "f_n"].forEach(i => $(i).value = "");
  $("unlocked").hidden = true; $("locked").hidden = false; clearTimeout(idle); chk();
}
$("lock").onclick = () => lock(false);
function bump() { clearTimeout(idle); idle = setTimeout(() => lock(true), 5 * 60 * 1000); }
["click", "keydown", "input"].forEach(ev => document.addEventListener(ev, () => { if (db) bump(); }));
addEventListener("beforeunload", e => { if (dirty) { e.preventDefault(); e.returnValue = ""; } });
