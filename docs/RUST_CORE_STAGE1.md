# Rust core experiment — stage 1 (not release-ready)

Branch: feature/rust-core. Existing libXray remains the default. The experimental adapter targets xray-rust-mobile 0.10.1, C ABI 1.17, core tag v0.10.1, commit 3cef7c42c2fd296afd27ab0b6435c632842e1a26. Upstream: https://github.com/aimalygin/xray-rust-mobile/releases/tag/v0.10.1 and https://github.com/aimalygin/xray-rust/tree/v0.10.1. This is not MantaRay. Upstream core uses MPL 2.0; retain the SDK license, notices and exact covered source availability before distributing an APK.

## Implemented
- CoreEngine interface, LibXrayEngine adapter, Android-owned TUN lifecycle, backend DNS and traffic counters.
- Optional RustCoreEngine with typed SDK API, borrowed fd, socket protection via VpnService, geodata and static-only DNS bootstrap.
- Explicit per-session opt-in stored in settings; toggle visible only in builds containing Rust SDK.
- Runtime profile copy retaining outbounds and routing; reject unsupported protocol/address shapes rather than silently altering them.
- Initial unit tests for runtime adaptation. No live VPN test has been performed in this stage.

## Build
Default existing build remains unchanged. For the opt-in build obtain the official standalone Android AAR for v0.10.1, verify its digest independently against the upstream release manifest/checksums and retain license notices. Then run from repository root:

    python3 scripts/fetch-native.py
    ./gradlew --dependency-verification strict :app:testExternalDebugUnitTest :app:assembleExternalDebug -PsignetRustAar=/absolute/path/to/official.aar -PsignetRustAarSha256=VERIFIED_64_HEX_DIGEST

The SHA must be the independently authenticated expected checksum, not merely a hash computed from an untrusted download. No checksum has been asserted in this repository yet. The build verifies the supplied digest before using the local AAR; regular Gradle verification stays strict. Do not distribute an experimental APK until the SDK provenance, dependency and device gates pass. This APK uses the existing application ID; install on a test device only.

## Known gaps / acceptance gates
- Hostname bootstrap and encrypted DNS upstreams are intentionally not supported in initial Rust mode. Profiles with carrier domains (including current FIN4) cannot be tested unchanged in this mode. Do not rewrite production subscriptions. Use a synthetic local numeric-address fixture.
- xray-rust accepts a documented subset of Xray JSON; parser rejection remains a failed connection, not automatic fallback. Preserve original provider configuration and use libXray if needed.
- Validate Kotlin compilation against exact AAR; JNI packaging/R8/16 KiB alignment; native runtime on supported ABIs; Wi-Fi/cellular transitions; socket protection; no DNS leakage; real HTTPS and traffic counters; shutdown and fd ownership. Upstream reports incomplete Android network-transition acceptance.
- Probe and JSON editor paths remain libXray-specific. Rust statistics count all TUN flows, including direct. No claim of equivalent performance.
- Remaining TЗ: robust multi-target health checks, passive error monitoring, subscription improvements, signed remote config, Network Lab, managed drain, capability-aware probes, and device acceptance. Closed MantaRay algorithms are research only; do not claim implementation.
