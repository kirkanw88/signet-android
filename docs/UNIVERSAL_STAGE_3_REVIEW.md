# Universal — этап 3: читаемость, импорт и уведомления

Scope: `com.signet.client.external`. This feature branch starts from the completed, green CI commit `1d919ff9` on `feature/universal-stages-1-2-ui`. No merge, tag or release.

## Implemented
- For Universal, `Scaffold.snackbarHost` overlays notifications at a dedicated bottom offset instead of inserting Snackbar into the home Column; existing Signet snackbar layout is retained.
- For Universal, advanced XDrive OAuth consent and AGE key are hidden behind an expandable "Дополнительные параметры"; both continue to default off/blank, with no automatic permission changes.
- Traffic totals and rates and server probe state are at least 12sp in Universal, including connected state.
- Server row now has an explicit, accessible favorite toggle, while long-press behavior remains; marks selectable rows with radio-button semantics.
- Android UI test verifies advanced fields are absent until explicitly opened and checks favorite toggle isolation for a synthetic provider subscription.

## Preserved
- No country grouping or client-side fastest-server AUTO; provider group hierarchy, real server names, native Xray JSON and routing are untouched.
- Frozen Signet 0.2.6 package, production VPN infrastructure and signing are not changed.
- File changes to shared Kotlin classes are explicitly conditional on `!BuildConfig.SIGNET_ACCOUNT` where UX differs.

## Acceptance / limitations
- CI must pass full debug/release build, 4+ UI and 11 native instrumentation tests, screenshot layout matrix and dependency verification.
- Real-device verification required for 320dp, system font 1.3–2.0, TalkBack, screen keyboard and Snackbar not obscuring primary connect control.
- Advanced dialog test checks disclosure but not OAuth network authorization (which must be separately verified).
- The update banner remains in the Column and can still affect planet height; this is a follow-up, not claimed solved.
- No APK publication, PR merge or production readiness claim.
