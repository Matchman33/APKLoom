# Manual update checking

The packaging page remains the launch screen. Its about action opens an
independent about/update screen with current version, manual update checking,
release history, source repository, and license. System back and the toolbar
back action return to the existing packaging session and scroll position.

## Request boundaries

- Application startup and entering the about screen do not issue update requests.
- Only checkForUpdates(), invoked by the check button, starts a request.
- Checking disables the button and ignores duplicate calls while in flight.
- Results, progress, and errors are inline, with no update dialog or notification.
- An available release offers explicit browser actions for APK download and details.
- The application does not download or install an update automatically.
- The public GitHub latest-release endpoint is queried over HTTPS without tokens.
- Only the expected stable manager APK and exact project release URLs are accepted.
- Connect/read timeouts and a 1 MiB response limit bound individual requests.
- Network, API limits, unavailable service, absent releases, and malformed payloads
  leave a retryable state without scheduling another request.
- INTERNET permission is added to the manager only; wrapper network policies,
  packing options, and embedded APK data are unchanged.

## Verification on 2026-10-05

- Six existing manager tests and 16 new update tests passed.
- Tests cover idle creation/state observation without network access, manual checks,
  duplicate clicks, retry, numeric version comparison, stable-release filtering,
  missing APKs, foreign URLs, errors, redirects, oversized responses, and headers.
- Production UpdateChecker code connected to the real public GitHub API using
  the workstation proxy and correctly parsed v1.0.9 and its manager APK URL.
- Manager Release assembly and Release lint completed without errors.
- The test APK uses the existing formal certificate, not a debug key.
- Test artifact: out/manual-update/APK-Loom-1.0.9-manual-update-test.apk.
- Initial implementation verification used a separate test artifact and did not
  replace any published 1.0.9 assets.

## User confirmation and release

On 2026-10-05 the user reported that the test build was usable and requested
a commit and release. This is user-reported validation, not an instrumented
device test by the agent; device details and individual tested flows were not
provided. The release version is 1.0.10 (versionCode 617), using the same
formal signing certificate as earlier releases.

## Remaining device checks

No ADB device was online; reconnecting the previously used address failed.
Starting a newly prepared emulator was rejected by the host execution policy.
Consequently the agent did not verify navigation, rendering across orientations
and font sizes, browser handoff, or actual device connectivity. The user feedback
above confirms usability but does not establish exhaustive coverage of those
flows. Unit tests and workstation network checks are not a substitute for them.
