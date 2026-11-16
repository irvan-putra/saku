# Synthetic KeePassDX storage tests

This is a disposable, test-only overlay for the pinned public KeePassDX source. It exercises seven synthetic private-file storage scenarios. It is not app code, a real vault, a UI test, or evidence of production safety. The workflow fetches a fixed upstream revision and checks the exact test-file hashes before running only this class on a temporary Android emulator.
