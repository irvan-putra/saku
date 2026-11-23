// Synthetic tests for site/vault.js's Argon2 resource guards (the hostile-KDF caps).
// FAKE DATA ONLY. Slices the page's real LIMITS and setArgon2Impl hook out of
// site/vault.js (the same way run.mjs slices checkFileEnd), registers it on the
// vendored kdbxweb, then loads synthetic KDBX 4 files whose KDF headers ask for too
// much - or just little enough - memory, iterations or parallelism.
// Fixtures are built in memory with the same vendored kdbxweb. Ones whose parameters
// exceed a cap are sealed with a no-op key, so the big allocation never actually runs;
// the guard must throw before kdbxweb ever checks that key. confirm() is stubbed to
// false: the user always says "no" when the page asks to continue. Not a security review.
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

// The page asks before using more than the soft memory cap. Here the answer is always no.
let confirmCalls = 0;
ctx.confirm = () => { confirmCalls++; return false; };

// Slice the page's real LIMITS and its real Argon2 hook out of site/vault.js, unchanged.
const src = readFileSync(path.join(site, "vault.js"), "utf8");
const limA = src.indexOf("const LIMITS"), limB = src.indexOf("\n", limA);
const hookA = src.indexOf("kw.CryptoEngine.setArgon2Impl"), hookB = src.indexOf("\n});", hookA);
if (limA < 0 || hookA < 0 || hookB < 0) throw new Error("LIMITS or the Argon2 hook not found in site/vault.js");
// Wrapped in a fresh function scope so it can re-register after fixture builds swap the impl.
const hookCode = "(() => {\nvar kw = window.kdbxweb;\n" + src.slice(limA, limB) + "\n" + src.slice(hookA, hookB + 3) + "\n})();";
const usePageHook = () => vm.runInContext(hookCode, ctx);
usePageHook();

// Unguarded real Argon2, used only to build under-cap fixtures honestly.
const honest = async (pw, salt, memory, iterations, length, parallelism, type) => {
  const fn = type === 2 ? hw.argon2id : hw.argon2d;
  const out = await fn({ password: new Uint8Array(pw), salt: new Uint8Array(salt), parallelism, iterations, memorySize: memory, hashLength: length, outputType: "binary" });
  return out.buffer.slice(out.byteOffset, out.byteOffset + out.byteLength);
};
// No-op key for sealing over-cap fixtures: the guard must refuse before the key matters.
const noop = async (pw, salt, memory, iterations, length) => new Uint8Array(length).buffer;

const ab = u => u.buffer.slice(u.byteOffset, u.byteOffset + u.byteLength);
const cred = () => new kw.Credentials(kw.ProtectedValue.fromString("fake-kx4-pass"));
const base = new Uint8Array(readFileSync(path.join(here, "fixtures", "keepassxc-2.7.12-kdbx4-argon2d.kdbx")));
kw.CryptoEngine.setArgon2Impl(honest);
const db = await kw.Kdbx.load(ab(base), cred());
usePageHook();
// The real KeePassXC fixture's own KDF ask, recorded before any fixture building mutates it.
const baseAsk = { memMiB: Math.round(Number(db.header.kdfParameters.get("M")) / 1048576), iterations: Number(db.header.kdfParameters.get("I")), parallelism: db.header.kdfParameters.get("P") };

const VT = { UInt32: 4, UInt64: 5 }; /* kdbxweb's internal VariantDictionary value types */
// A copy of the base file with different KDF parameters in its header, sealed with impl.
async function makeVault({ memKiB = 8192, iterations = 3, parallelism = 1 }, impl) {
  const p = db.header.kdfParameters;
  p.set("M", VT.UInt64, kw.Int64.from(memKiB * 1024));
  p.set("I", VT.UInt64, kw.Int64.from(iterations));
  p.set("P", VT.UInt32, parallelism);
  kw.CryptoEngine.setArgon2Impl(impl);
  const out = await db.save();
  usePageHook();
  return out;
}
async function unlock(bytes) {
  const before = confirmCalls;
  try { await kw.Kdbx.load(bytes, cred()); return { res: "ok", asked: confirmCalls - before }; }
  catch (e) { return { res: e && e.message || String(e), asked: confirmCalls - before }; }
}

const K = 1024, SOFT = 256 * K, HARD = 1024 * K; /* the page's proposed caps, in KiB */
let fail = 0;
const check = (name, got, want) => { const ok = got === want; if (!ok) fail++; console.log((ok ? "PASS " : "FAIL ") + name + (ok ? "" : " -> " + JSON.stringify(got) + " (wanted " + JSON.stringify(want) + ")")); };
const refusedHard = m => typeof m === "string" && m.includes("more key-stretching work") ? "refused: over-cap" : m;
const refusedSoft = m => typeof m === "string" && m.startsWith("Cancelled:") ? "refused: user said no" : m;

// Hard caps: anything over them is refused before any allocation happens.
for (const [name, params] of [
  ["memory 1 GiB + 64 KiB (over hard cap)", { memKiB: HARD + 64 }],
  ["iterations 101 (over cap)", { iterations: 101 }],
  ["parallelism 17 (over cap)", { parallelism: 17 }],
]) {
  const r = await unlock(await makeVault(params, noop));
  check(name, refusedHard(r.res), "refused: over-cap");
  check(name + " [confirm never offered]", r.asked, 0);
}
// Between the soft and hard memory caps the page asks; a "no" refuses, before any allocation.
{
  const r = await unlock(await makeVault({ memKiB: SOFT + K }, noop));
  check("memory 257 MiB (over soft cap), user says no", refusedSoft(r.res), "refused: user said no");
  check("memory 257 MiB [confirm was offered]", r.asked, 1);
}
// Exactly at a cap the guard does not fire (it is exclusive); the soft-cap ask still applies.
{
  const r = await unlock(await makeVault({ memKiB: HARD }, noop));
  check("memory exactly 1 GiB, user says no", refusedSoft(r.res), "refused: user said no");
}
// Under the caps the file unlocks and confirm is never consulted (stubbed false would refuse otherwise).
{
  const r = await unlock(await makeVault({ memKiB: SOFT - K }, honest));
  check("memory 255 MiB (just under soft cap) unlocks", r.res, "ok");
  check("memory 255 MiB [confirm never offered]", r.asked, 0);
}
{
  const r = await unlock(await makeVault({ memKiB: 8192, iterations: 100 }, honest));
  check("iterations exactly 100 unlocks", r.res, "ok");
}
{
  const r = await unlock(await makeVault({ memKiB: 8192, parallelism: 16 }, honest));
  check("parallelism exactly 16 unlocks", r.res, "ok");
}
// Context for the guessed caps: what the real KeePassXC fixture asks for.
console.log("note: the KeePassXC 2.7.12 fixture (calibrated ~1s KDF) asks for " + baseAsk.memMiB + " MiB, " + baseAsk.iterations +
  " iterations, parallelism " + baseAsk.parallelism + " - iterations are already at half the page's 100 cap.");
console.log(fail ? fail + " FAILED" : "all passed");
process.exit(fail ? 1 : 0);
