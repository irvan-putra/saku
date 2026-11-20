"use strict";
/* Local-only KDBX vault page. Crypto is kdbxweb (WebCrypto) and hash-wasm Argon2; nothing here implements a primitive. */
const $ = id => document.getElementById(id);
const kw = window.kdbxweb;
const LIMITS = {memKiB: 1024 * 1024, iterations: 100, parallelism: 16}; /* hostile-KDF guard; values are a proposal for review */
kw.CryptoEngine.setArgon2Impl(async (password, salt, memory, iterations, length, parallelism, type) => {
  if (memory > LIMITS.memKiB || iterations > LIMITS.iterations || parallelism > LIMITS.parallelism) throw new Error("This file asks for more key-stretching work than this page allows (memory " + Math.round(memory / 1024) + " MiB, " + iterations + " rounds). It was not opened.");
  const fn = type === 2 ? window.hashwasm.argon2id : window.hashwasm.argon2d;
  const out = await fn({password: new Uint8Array(password), salt: new Uint8Array(salt), parallelism, iterations, memorySize: memory, hashLength: length, outputType: "binary"});
  return out.buffer.slice(out.byteOffset, out.byteOffset + out.byteLength);
});
let db = null, fileBuf = null, cur = null, dirty = false, idle = null, clipT = null;
const cred = pw => new kw.Credentials(kw.ProtectedValue.fromString(pw));
const toAB = b => b instanceof ArrayBuffer ? b : b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength);
function setErr(m) { $("err").textContent = m || ""; }
function chk() { $("open").disabled = !(fileBuf && $("pw").value); }
$("file").onchange = async e => { const f = e.target.files[0]; fileBuf = null; $("fname").textContent = ""; if (f) { fileBuf = await f.arrayBuffer(); $("fname").textContent = " " + f.name; } chk(); };
$("pw").oninput = chk;
async function unlock(buf, pw) {
  setErr(""); $("open").disabled = true; $("demo").disabled = true; setErr("Unlocking...");
  try { db = await kw.Kdbx.load(toAB(buf), cred(pw)); setErr(""); show(); }
  catch (x) { db = null; setErr(x && x.code === "InvalidKey" ? "Wrong password, or not a KeePass file." : (x && x.message) || "Could not open that file."); }
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
  try { const buf = await db.save(), a = document.createElement("a"), u = URL.createObjectURL(new Blob([buf], {type: "application/octet-stream"}));
    a.href = u; a.download = ((db.meta.name || "vault").replace(/[^\w.-]+/g, "_") || "vault") + ".kdbx"; document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(u), 5000); dirty = false; render();
  } catch (x) { $("dmsg").textContent = "Could not save: " + x.message; }
};
function lock(force) {
  if (dirty && !force && !confirm("You have unsaved changes. Lock anyway and lose them?")) return;
  db = null; cur = null; dirty = false; fileBuf = null; $("fname").textContent = ""; $("detail").hidden = true; $("list").replaceChildren(); $("q").value = ""; ["f_t", "f_u", "f_p", "f_l", "f_n"].forEach(i => $(i).value = "");
  $("unlocked").hidden = true; $("locked").hidden = false; clearTimeout(idle); chk();
}
$("lock").onclick = () => lock(false);
function bump() { clearTimeout(idle); idle = setTimeout(() => lock(true), 5 * 60 * 1000); }
["click", "keydown", "input"].forEach(ev => document.addEventListener(ev, () => { if (db) bump(); }));
addEventListener("beforeunload", e => { if (dirty) { e.preventDefault(); e.returnValue = ""; } });
