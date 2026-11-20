# Universal — Stage 9: cinematic globe (full-day edition)

## Visual reference
User-provided four-phone Signet Universal mockup (2026-11-20), especially its first/Home screen: photographic terrestrial relief, bright cyan rim, deeply saturated navy background, precise country outlines. It is a *visual reference*, not a reusable equirectangular Earth texture; it must never be pasted onto a 3D sphere.

**User correction:** entirely remove the dark/night hemisphere. The Universal globe uses an evenly illuminated full-day Earth, with gentle directional relief but **without a dark terminator or city-light overlay**. Keep a strong blue atmospheric rim, believable land/ocean texture and country highlighting.

## Changed
- `app/src/main/java/com/signet/client/graphics/GlobeView.kt`: `uPremium` enables *Universal-only* enhanced high-contrast daylight colors, blue atmosphere and navy star-field. Night-side and city lights are omitted from Universal's final color. Signet-flavor shader behavior is preserved when `uPremium=0`.
- Retains the native GLES 2.0 interactive 3D globe, offline NASA-origin texture, zoom, names and selected-country polygon borders. No stock photo or static screenshot substituted.
- Country highlight: lower-alpha polygon fill, narrower cyan border offset to avoid a washed-out glow.
- Camera: frame-rate-independent focus and zoom easing, with immediate frame settlement when Reduce Motion is on.
- Existing stage 6 immediate vertical/diagonal rotation is preserved.
- `core/src/main/kotlin/com/signet/core/GlobeMotion.kt` + unit tests assert no overshoot, bounded response at different framerates and Reduce Motion behavior.

## Acceptance still required
1. GitHub Android CI: all unit tests, external debug/release, instrumentation UI and native transport tests, screenshot matrix.
2. **Visual** comparison between emulator screenshots and the supplied mockup. The mockup depicts a perspective rendering, so exact pixel copying would not permit correct 3D rotation.
3. Manual hardware testing: initial vertical, horizontal and diagonal gestures, FPS/heat, orientation, country camera focus/zoom, toggling Reduce Motion, background/restore, mobile/Wi-Fi.
4. Verify no overly bright atmosphere, halo around country, washed-out oceans or visual artifacts at night settings / light theme.
5. Ensure protocol configs, real provider-issued AUTO, HWID and all subscription IDs remain unchanged.

Frozen Signet 0.2.6, main, signing, production nodes and backend remain untouched.
**Draft only, not a release.**
