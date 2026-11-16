# Pinned upstream host baseline plan

Prepared 2026-11-16. Proposal only, not a build/test result or vault adoption.

Add one manual-only workflow to the existing public Saku default branch and
fetch unchanged public KeePassDX commit
`77ecfa8654a5bd0d79d512f8fa71c0af8a29b554` into a disposable runner. No fork,
source vendoring, app identity/branding change or license change. Manual dispatch
requires the workflow to exist on the default branch.

One standard ubuntu-24.04 job, 45-minute timeout, one concurrency group, no
push/PR/schedule trigger, no automatic retry. JDK 17 from runner, SDK 36, NDK
25.2.9519653 and CMake 3.22.1. Android tool licenses accepted in disposable job.
Public dependency/build code executes; this is not an offline or audited build.
Full source SHA is traceability, not bit-reproducibility of all dependencies.
The pinned upstream Gradle wrapper lacks a distribution checksum; this baseline
does not alter it or imply verified distribution integrity. HTTPS downloads
still leave that supply-chain assurance limit explicit.

Run upstream database/app host unit tests plus app Kotlin compilation, at most
two Gradle workers. Report command status and available JUnit counts. Existing
public tests are synthetic fixtures, not new interop/save-failure/security test
coverage. Missing reports, no non-skipped tests or prerequisite failure must not be called
passed. Source status must remain clean after the host command.
No emulator/native instrumentation/autofill/unlock/transport/phone claim.

Empty token permissions; anonymous public source fetch. No third-party Actions,
secret, account, real vault, private data, signing key, APK assembly/distribution,
release, source writeback, custom runner or paid fallback. No artifact upload or
persistent dependency-cache upload. Only ordinary run logs and count/status
summary. No private history belongs in this note, workflow, logs or summary.

GitHub says standard hosted runners in public repositories are free independently
of private included usage; larger runners are billed even public. Artifact/cache
storage have separate allowances and possible charges, so neither is uploaded.
This does not guarantee absence of unrelated account charges. Verify public
visibility and Actions permission/policy before publication/dispatch; job refuses
to execute if repo is private. Capacity/policy limits can still block it. No
billing-setting change is authorized. Publication and one dispatch require
owner-approved scope; no run has occurred as part of this preparation.

Sources checked 2026-11-16:
- https://docs.github.com/en/actions/concepts/billing-and-usage
- https://docs.github.com/en/actions/reference/runners/github-hosted-runners
- https://docs.github.com/en/billing/concepts/product-billing/github-actions
- https://docs.github.com/en/actions/reference/limits
- https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow
- https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax?use_case=
- https://raw.githubusercontent.com/Kunzisoft/KeePassDX/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/app/build.gradle.kts
- https://raw.githubusercontent.com/Kunzisoft/KeePassDX/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/database/build.gradle.kts
- https://raw.githubusercontent.com/Kunzisoft/KeePassDX/77ecfa8654a5bd0d79d512f8fa71c0af8a29b554/crypto/src/main/jni/CMakeLists.txt
