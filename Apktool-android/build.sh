#!/usr/bin/env bash
set -euo pipefail

# Reproducible local Android build bootstrap for Master App.
# Usage:
#   ./build.sh                  # debug APK
#   ./build.sh assembleRelease  # release APK

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="${MASTER_APP_TOOLS_DIR:-"$ROOT_DIR/.tools"}"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$ROOT_DIR/.android-sdk"}}"
GRADLE_VERSION="${GRADLE_VERSION:-8.11.1}"
CMDLINE_TOOLS_VERSION="${CMDLINE_TOOLS_VERSION:-11076708}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-36}"
ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-36.0.0}"
GRADLE_DIR="$TOOLS_DIR/gradle-$GRADLE_VERSION"
CMDLINE_DIR="$SDK_DIR/cmdline-tools/latest"

log() {
    printf '\n[build] %s\n' "$*"
}

die() {
    printf '\n[build] ERROR: %s\n' "$*" >&2
    exit 1
}

download() {
    local url="$1"
    local destination="$2"
    curl --fail --location --retry 3 --retry-delay 2 --output "$destination" "$url"
}

ensure_java() {
    command -v java >/dev/null 2>&1 || die "Java 17 or newer is required. Install Java and rerun this script."
    local version
    version="$(java -version 2>&1 | awk -F '"' '/version/ {print $2; exit}')"
    local major="${version%%.*}"
    if [ "$major" = "1" ]; then
        major="${version#1.}"
        major="${major%%.*}"
    fi
    [ -n "$major" ] && [ "$major" -ge 17 ] \
        || die "Java 17 or newer is required; found ${version:-unknown}."
}

ensure_gradle() {
    if [ -x "$GRADLE_DIR/bin/gradle" ]; then
        return
    fi

    log "Downloading Gradle $GRADLE_VERSION"
    mkdir -p "$TOOLS_DIR"
    local archive="$TOOLS_DIR/gradle-$GRADLE_VERSION-bin.zip"
    download "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" "$archive"
    rm -rf "$TOOLS_DIR/gradle-$GRADLE_VERSION" "$TOOLS_DIR/gradle-$GRADLE_VERSION.tmp"
    mkdir "$TOOLS_DIR/gradle-$GRADLE_VERSION.tmp"
    unzip -q "$archive" -d "$TOOLS_DIR/gradle-$GRADLE_VERSION.tmp"
    mv "$TOOLS_DIR/gradle-$GRADLE_VERSION.tmp/gradle-$GRADLE_VERSION" "$GRADLE_DIR"
    rm -rf "$TOOLS_DIR/gradle-$GRADLE_VERSION.tmp" "$archive"
}

ensure_android_sdk() {
    if [ ! -x "$CMDLINE_DIR/bin/sdkmanager" ]; then
        log "Downloading Android command-line tools"
        mkdir -p "$SDK_DIR/cmdline-tools"
        local archive="$TOOLS_DIR/commandlinetools-linux-$CMDLINE_TOOLS_VERSION.zip"
        mkdir -p "$TOOLS_DIR"
        download "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" "$archive"
        rm -rf "$SDK_DIR/cmdline-tools/latest" "$SDK_DIR/cmdline-tools/extracted"
        mkdir "$SDK_DIR/cmdline-tools/extracted"
        unzip -q "$archive" -d "$SDK_DIR/cmdline-tools/extracted"
        mv "$SDK_DIR/cmdline-tools/extracted/cmdline-tools" "$CMDLINE_DIR"
        rm -rf "$SDK_DIR/cmdline-tools/extracted" "$archive"
    fi

    export ANDROID_HOME="$SDK_DIR"
    export ANDROID_SDK_ROOT="$SDK_DIR"
    export PATH="$CMDLINE_DIR/bin:$SDK_DIR/platform-tools:$PATH"

    log "Installing Android SDK platform and build tools"
    yes | sdkmanager --sdk_root="$SDK_DIR" --licenses >/dev/null || true
    sdkmanager --sdk_root="$SDK_DIR" \
        "platform-tools" \
        "platforms;$ANDROID_PLATFORM" \
        "build-tools;$ANDROID_BUILD_TOOLS"
}

ensure_java
ensure_gradle
ensure_android_sdk

mkdir -p "$ROOT_DIR"
printf 'sdk.dir=%s\n' "$SDK_DIR" > "$ROOT_DIR/local.properties"

TASK="${1:-assembleDebug}"
shift || true

log "Running :apktool:$TASK"
"$GRADLE_DIR/bin/gradle" --no-daemon --stacktrace ":apktool:$TASK" "$@"

if [[ "$TASK" == *assembleDebug* ]]; then
    RELEASE_DIR="$ROOT_DIR/releases"
    DEBUG_APK="$ROOT_DIR/apktool/build/outputs/apk/debug/apktool-debug.apk"
    if [ -f "$DEBUG_APK" ]; then
        mkdir -p "$RELEASE_DIR"
        cp "$DEBUG_APK" "$RELEASE_DIR/master-app-debug.apk"
        log "Debug APK copied to releases/master-app-debug.apk"
    fi
fi

log "Build complete"