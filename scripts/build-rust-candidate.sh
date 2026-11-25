#!/usr/bin/env bash
set -euo pipefail
: "${RUNNER_TEMP:?}" "${ANDROID_HOME:?}"
root="$RUNNER_TEMP/rust-source"
mkdir -p "$root"
core="$root/core"
mobile="$root/mobile"
core_sha=36f6ebcd493e0297155f3f1b5fca76ff80792b52
mobile_sha=7885435a49f6d3728154dad99b6f5038182eb18d
checkout() {
    git init -q "$2"
    git -C "$2" remote add origin "$1"
    git -C "$2" fetch --depth=1 origin "$3"
    git -C "$2" checkout --detach FETCH_HEAD
    test "$(git -C "$2" rev-parse HEAD)" = "$3"
}
checkout https://github.com/aimalygin/xray-rust.git "$core" "$core_sha"
checkout https://github.com/aimalygin/xray-rust-mobile.git "$mobile" "$mobile_sha"
test "$(git -C "$core" rev-parse 'HEAD^{tree}')" = 361642a8700477b2dac760ea227e54729d5019b3
test "$(git -C "$core" hash-object Cargo.lock)" = 7def1b3004072fa814c1270cbc36553e9889acf8
# The candidate and released SDK have exactly the same native interface.
echo "926e7a5318916a9ebc706a2b48bbdd58f8c6601282683ad942f23109a2067aa7  $core/crates/xray-ffi/include/xray_ffi.h" | sha256sum -c -
echo "28552741cc3df9540cef79770cc103c516dbc565dc87387be01a86ef38066f11  $core/crates/xray-ffi/include/module.modulemap" | sha256sum -c -
lock=$(sha256sum "$core/Cargo.lock" | cut -d' ' -f1)
cat > "$mobile/release/core.env" <<EOF
XRAY_RUST_REPOSITORY=https://github.com/aimalygin/xray-rust.git
XRAY_RUST_REF_KIND=commit
XRAY_RUST_TAG=
XRAY_RUST_TAG_OBJECT=
XRAY_RUST_COMMIT=$core_sha
XRAY_RUST_TREE=361642a8700477b2dac760ea227e54729d5019b3
XRAY_RUST_CARGO_LOCK_SHA256=$lock
XRAY_RUST_FFI_HEADER_SHA256=926e7a5318916a9ebc706a2b48bbdd58f8c6601282683ad942f23109a2067aa7
XRAY_RUST_MODULEMAP_SHA256=28552741cc3df9540cef79770cc103c516dbc565dc87387be01a86ef38066f11
XRAY_FFI_ABI_MAJOR=1
EOF
export XRAY_RUST_SOURCE="$core"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/26.3.11579264"
export CARGO_BUILD_JOBS=2
export GRADLE_USER_HOME="$root/gradle-sdk"
rustup toolchain install 1.96.0 --profile minimal
rustup target add --toolchain 1.96.0 aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
bash "$mobile/scripts/build-android.sh" :xraymobile:testDebugUnitTest :xraymobile:assembleRelease
out="$RUNNER_TEMP/rust-sdk"
mkdir -p "$out"
cp "$mobile/android/xraymobile/build/outputs/aar/xraymobile-release.aar" "$out/xray-rust-mobile.aar"
(cd "$out" && sha256sum xray-rust-mobile.aar > SHA256SUMS)
cp "$mobile/release/core.env" "$out/core.env"
cp "$mobile/LICENSE" "$out/LICENSE"
cp "$mobile/THIRD_PARTY_NOTICES.md" "$out/THIRD_PARTY_NOTICES.md"
