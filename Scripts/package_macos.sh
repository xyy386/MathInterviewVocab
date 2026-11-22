#!/bin/bash
set -euo pipefail

# 在独立临时构建目录中生成 Universal 成品，不覆盖任何已有交付物。
script_directory="$(cd "$(dirname "$0")" && pwd)"
project_root="$(cd "$script_directory/.." && pwd)"
destination="${1:-$project_root/release-artifacts/macos-v1.5.0}"
version=1.5.0
build_number=7
archive_name="MathInterviewVocab-macOS-universal-v$version.zip"
app_name='数学面试词汇.app'

if [[ "$(uname -s)" != Darwin || $# -gt 1 ]]; then
    printf 'Usage: /bin/bash Scripts/package_macos.sh [OUTPUT_DIRECTORY] (macOS only)\n' >&2
    exit 1
fi
/bin/mkdir -p "$destination"
destination="$(cd "$destination" && pwd)"
for artifact in "$app_name" "$archive_name" SHA256SUMS-macOS.txt; do
    if [[ -e "$destination/$artifact" || -L "$destination/$artifact" ]]; then
        printf 'Refusing to replace an existing artifact: %s\n' "$destination/$artifact" >&2
        exit 1
    fi
done
verification_root="${MATH_VOCAB_VERIFICATION_ROOT:-/Users/$(/usr/bin/id -un)/Documents/codextest/$(TZ=Asia/Shanghai /bin/date +%F)/MathInterviewVocab/desktop-update}"
/bin/mkdir -p "$verification_root"
task_workspace="$(/usr/bin/mktemp -d "$verification_root/macos-package.XXXXXX")"
cleanup() {
    case "$task_workspace" in
        "$verification_root"/macos-package.??????) /bin/rm -rf "$task_workspace" ;;
    esac
}
trap cleanup EXIT

/usr/bin/xcodebuild -project "$project_root/MathInterviewVocab.xcodeproj" \
    -scheme MathInterviewVocab -configuration Release \
    -destination 'generic/platform=macOS' -derivedDataPath "$task_workspace/DerivedData" \
    ARCHS='arm64 x86_64' ONLY_ACTIVE_ARCH=NO CODE_SIGNING_ALLOWED=NO build

application="$task_workspace/DerivedData/Build/Products/Release/MathInterviewVocab.app"
executable="$application/Contents/MacOS/MathInterviewVocab"
plist="$application/Contents/Info.plist"
if [[ "$(/usr/bin/plutil -extract CFBundleShortVersionString raw -o - "$plist")" != "$version" || \
      "$(/usr/bin/plutil -extract CFBundleVersion raw -o - "$plist")" != "$build_number" ]]; then
    printf '%s\n' 'Unexpected application version.' >&2
    exit 1
fi
if [[ "$(/usr/bin/plutil -extract NSAppTransportSecurity.NSAllowsLocalNetworking raw -o - "$plist")" != true ]]; then
    printf '%s\n' 'Local-only update handoff is not configured.' >&2
    exit 1
fi
architectures="$(/usr/bin/lipo -archs "$executable")"
if [[ "$architectures" != 'arm64 x86_64' && "$architectures" != 'x86_64 arm64' ]]; then
    printf 'Unexpected executable architectures: %s\n' "$architectures" >&2
    exit 1
fi

# 内置资源必须完整，不能把不含语音或混入口音片段的包当作正式版。
audio_directory="$application/Contents/Resources/KokoroAudio/audio"
manifest="$application/Contents/Resources/KokoroAudio/audio-manifest.json"
if [[ "$(/usr/bin/plutil -extract coverage.complete raw -o - "$manifest")" != true || \
      "$(/usr/bin/plutil -extract coverage.expectedEntries raw -o - "$manifest")" != 2328 ]]; then
    printf '%s\n' 'Bundled offline audio inventory is incomplete.' >&2
    exit 1
fi
audio_count=0
shopt -s nullglob
for audio in "$audio_directory/"*.mp3; do
    filename="${audio##*/}"
    if [[ ! "$filename" =~ ^[a-f0-9]{64}\.mp3$ || ! -f "$audio" || -L "$audio" ]]; then
        printf 'Invalid bundled audio file: %s\n' "$filename" >&2
        exit 1
    fi
    audio_count=$((audio_count + 1))
done
if [[ "$audio_count" != 2328 ]]; then
    printf 'Bundled audio files: expected 2328, found %s.\n' "$audio_count" >&2
    exit 1
fi
# 未启用 Xcode 签名时，SPM 的自动嵌入会删掉已封存的头文件。
# 从锁定版本的二进制工件恢复完整框架，保留上游签名和 XPC helper。
sparkle_framework="$application/Contents/Frameworks/Sparkle.framework"
sparkle_artifact="$task_workspace/DerivedData/SourcePackages/artifacts/sparkle/Sparkle/Sparkle.xcframework/macos-arm64_x86_64/Sparkle.framework"
if [[ ! -d "$sparkle_framework" || ! -d "$sparkle_artifact" ]]; then
    printf '%s\n' 'Sparkle.framework is missing.' >&2
    exit 1
fi
/usr/bin/codesign --verify --deep --strict "$sparkle_artifact"
/usr/bin/ditto --norsrc "$sparkle_artifact" "$sparkle_framework"
/usr/bin/codesign --verify --deep --strict "$sparkle_framework"

# 更新 ZIP 仅包含 .app，许可和安装说明放进包内，减少 App Translocation 问题。
notices="$application/Contents/Resources/Notices"
/bin/mkdir -p "$notices"
/bin/cp "$project_root/LICENSE" "$notices/PROJECT-LICENSE.txt"
/bin/cp "$project_root/docs/INSTALL-macOS.txt" "$notices/使用说明.txt"
/bin/cp "$project_root/Windows/resources/AUDIO-SOURCES.md" "$notices/AUDIO-SOURCES.md"
/bin/cp "$project_root/Windows/resources/KOKORO-APACHE-2.0.txt" "$notices/KOKORO-APACHE-2.0.txt"
/bin/cp "$project_root/Resources/Sparkle-LICENSE.txt" "$notices/Sparkle-LICENSE.txt"
/usr/bin/codesign --force --sign - --timestamp=none "$application"
/usr/bin/codesign --verify --deep --strict "$application"

stage="$task_workspace/package"
/bin/mkdir -p "$stage"
/usr/bin/ditto --norsrc "$application" "$stage/$app_name"
/usr/bin/ditto -c -k --norsrc "$stage" "$destination/$archive_name"
/usr/bin/unzip -tq "$destination/$archive_name"
/usr/bin/ditto --norsrc "$stage/$app_name" "$destination/$app_name"
(
    cd "$destination"
    /usr/bin/shasum -a 256 "$archive_name" > SHA256SUMS-macOS.txt
)
printf 'Created Universal macOS %s (build %s): %s\n' "$version" "$build_number" "$destination"
