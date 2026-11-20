// Synthetic tests for site/vault.js's KDBX4 end-of-file check. FAKE DATA ONLY.
// Loads the vendored kdbxweb + hash-wasm exactly as the page does, then runs the
// page's own checkFileEnd() (sliced out of site/vault.js) on fixtures and on
// tampered copies. Not a security review. Does not test the browser UI.
import { readFileSync } from "node:fs";
import vm from "node:vm";
import { webcrypto } from "node:crypto";
import { DOMParser, XMLSerializer } from "@xmldom/xmldom";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const site = path.join(here, "..", "site");
const ctx = { Buffer, atob, btoa, crypto: webcrypto, DOMParser, XMLSerializer, TextEncoder, TextDecoder, console, setTimeout, clearTimeout, WebAssembly, Uint8Array, ArrayBuffer, DataView };
ctx.window = ctx; ctx.self = ctx; ctx.globalThis = ctx;
vm.createContext(ctx);
for (const f of ["vendor/kdbxweb.min.js", "vendor/argon2.umd.min.js"]) vm.runInContext(readFileSync(path.join(site, f), "utf8"), ctx, { filename: f });
const kw = ctx.kdbxweb, hw = ctx.hashwasm;
if (!kw || !hw) throw new Error("vendor libraries did not load");

let lastKey = null;
kw.CryptoEngine.setArgon2Impl(async (pw, salt, memory, iterations, length, parallelism, type) => {
  const fn = type === 2 ? hw.argon2id : hw.argon2d;
  const out = await fn({ password: new Uint8Array(pw), salt: new Uint8Array(salt), parallelism, iterations, memorySize: memory, hashLength: length, outputType: "binary" });
  lastKey = out.slice();
  return out.buffer.slice(out.byteOffset, out.byteOffset + out.byteLength);
});

const src = readFileSync(path.join(site, "vault.js"), "utf8");
const a = src.indexOf("async function checkFileEnd"), b = src.indexOf("\n}\n", a);
if (a < 0 || b < 0) throw new Error("checkFileEnd not found in site/vault.js");
vm.runInContext(src.slice(a, b + 2) + "\nglobalThis.checkFileEnd = checkFileEnd;", ctx);

const ab = u => u.buffer.slice(u.byteOffset, u.byteOffset + u.byteLength);
const cred = pw => new kw.Credentials(kw.ProtectedValue.fromString(pw));
async function run(bytes, pw) {
  lastKey = null;
  let db;
  try { db = await kw.Kdbx.load(ab(bytes), cred(pw)); } catch (e) { if (process.env.DEBUG) console.log("  load error:", e && e.message); return "load-refused"; }
  try { return await ctx.checkFileEnd(ab(bytes), lastKey); } catch (e) { return "end-refused"; }
}

const fx = f => new Uint8Array(readFileSync(path.join(here, "fixtures", f)));
const good = [
  ["demo.kdbx", "demo-not-real", "ok"],
  ["keepassxc-2.7.12-kdbx4-argon2d.kdbx", "fake-kx4-pass", "ok"],
  ["page-saved-from-keepassxc-kdbx4.kdbx", "fake-kx4-pass", "ok"],
  ["page-saved-from-demo.kdbx", "demo-not-real", "ok"],
  ["keepassxc-2.7.9-kdbx3.kdbx", "fake-kx-pass-1", "skipped"],
];
let fail = 0;
const check = (name, got, want) => { const ok = got === want; if (!ok) fail++; console.log((ok ? "PASS " : "FAIL ") + name + " -> " + got + (ok ? "" : " (wanted " + want + ")")); };
for (const [f, pw, want] of good) check(f, await run(fx(f), pw), want);
for (const [f, pw] of good.filter(g => g[2] === "ok")) {
  const d = fx(f);
  const flip = d.slice(); flip[flip.length - 20] ^= 1;
  check(f + " [bit flip in closing HMAC]", await run(flip, pw), "end-refused");
  const app = new Uint8Array(d.length + 1); app.set(d); check(f + " [one appended byte]", await run(app, pw), "end-refused");
  const cut = d.slice(0, d.length - 36); { const r2 = await run(cut, pw); check(f + " [closing block cut off; kdbxweb itself refuses with a raw library error]", r2 === "load-refused" || r2 === "end-refused" ? "refused" : r2, "refused"); }
  const mid = d.slice(); mid[Math.floor(d.length / 2)] ^= 1; const r = await run(mid, pw); check(f + " [bit flip mid-file]", r === "load-refused" || r === "end-refused" ? "refused" : r, "refused");
  check(f + " [wrong password]", await run(d, "definitely-wrong"), "load-refused");
}
console.log(fail ? fail + " FAILED" : "all passed");
process.exit(fail ? 1 : 0);
