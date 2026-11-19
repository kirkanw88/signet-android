# Universal — этапы 1–2 (review implementation)

**Scope:** `com.signet.client.external`, PR against `feature/android-0.2.0`. This is NOT a release and NOT a completed physical-device acceptance.

## Implemented
- Connection CTA: CONNECTING/RECONNECTING shows **Отменить подключение**; ERROR offers **Повторить подключение**. Connection error provides server switch and diagnostics actions.
- Settings: nested navigation uses one visible back button and the same system Back policy.
- Multi-provider Home shows name of the source for the selected server.
- Provider cards: keep independent pinned/collapsed state, place per-provider Refresh/Ping below the title (only when expanded), compact collapsed headings, display actual relative update time.
- List: prominent search control; readable server metadata; do not duplicate `JSON` after transport; explicit selected-state accessibility semantics; selected radio indicator.
- Announcements: per-provider content can be expanded instead of silently truncated.
- Refresh failures: retain existing subscription and show a safe per-provider error without raw URLs/tokens; 401/403/429 have meaningful descriptions.
- AUTO quick selection is limited to the selected subscription, never a client-side fastest-server or balancer algorithm.
- Explain eight-subscription limit.
- Remove photo overlay and bundled offline landmark assets from the **external** source set. Keep the GLES globe.

## Existing behavior intentionally preserved
- No grouping by country. Provider grouping remains, with independent subscription IDs and pin/collapse.
- Real server names, raw JSON, routes, balancers and AUTO configuration are not modified.
- No bottom navigation, crowns, in-app account/billing for Universal.
- No changes to Remnawave servers, Signet backend, signing, release tags, `main`, or frozen Signet 0.2.6 APK.

## Mandatory acceptance before merging
- Verify strict Gradle dependency metadata. Branch CI currently fails during plugin POM verification **before Kotlin compilation**. Existing expected SHA-256 is in verification-metadata.xml; do NOT blindly regenerate or trust downloaded POM.
- Compile and run `:app:testExternalDebugUnitTest :app:lintExternalDebug :app:assembleExternalDebug :app:assembleExternalRelease`.
- Verify external APK no longer bundles `landmarks/*.webp` or `landmarks.json`, measure APK size.
- UI test Android 320/360/412 dp and landscape, 1.3–2.0 font scale, TalkBack and system Back.
- Test several provider subscriptions including duplicate server names, pinned/collapsed groups, AUTO, per-provider refresh failures and refresh of active provider.
- Physically verify start/cancel/reconnect, real TUN HTTPS and Ping both on/off VPN, Wi-Fi/mobile transition, QR import.
- Verify Signet flavor regression compile before allowing shared files into a future Signet build.

## Remaining work (not claimed done)
- Full physical Android VPN/QR/transport regression and actual UI acceptance; camera/TUN hardware was not available here.
- Rich error actions for all VPN phases and device-specific reasons.
- Fine-grained refresh cancellation; detailed live HWID/provider errors; security leak-test matrix.
- A complete visual design polish, screenshots and performance measurements.

No PR merge until CI is green and required acceptance is completed.
