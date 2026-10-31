#!/bin/bash
set -euo pipefail

# 在独立临时构建目录中生成 Universal 成品，不覆盖任何已有交付物。
script_directory="$(cd "$(dirname "$0")" && pwd)"
project_root="$(cd "$script_directory/.." && pwd)"
destination="${1:-$project_root/release-artifacts/macos-v1.3.0}"
version=1.3.0
build_number=5
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
task_workspace="$(/usr/bin/mktemp -d /tmp/math-vocab-macos-release.XXXXXX)"
cleanup() {
    case "$task_workspace" in
        /tmp/math-vocab-macos-release.??????) /bin/rm -rf "$task_workspace" ;;
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
/usr/bin/codesign --force --sign - --timestamp=none "$application"
/usr/bin/codesign --verify --deep --strict "$application"

# ZIP 附带项目及音源许可，安装说明与当前版本一同发布。
stage="$task_workspace/package"
/bin/mkdir -p "$stage"
/usr/bin/ditto --norsrc "$application" "$stage/$app_name"
/bin/cp "$project_root/LICENSE" "$stage/PROJECT-LICENSE.txt"
/bin/cp "$project_root/docs/INSTALL-macOS.txt" "$stage/使用说明.txt"
/bin/cp "$project_root/Windows/resources/AUDIO-SOURCES.md" "$stage/AUDIO-SOURCES.md"
/bin/cp "$project_root/Windows/resources/KOKORO-APACHE-2.0.txt" "$stage/KOKORO-APACHE-2.0.txt"
/usr/bin/ditto -c -k --norsrc "$stage" "$destination/$archive_name"
/usr/bin/unzip -tq "$destination/$archive_name"
/usr/bin/ditto --norsrc "$stage/$app_name" "$destination/$app_name"
(
    cd "$destination"
    /usr/bin/shasum -a 256 "$archive_name" > SHA256SUMS-macOS.txt
)
printf 'Created Universal macOS %s (build %s): %s\n' "$version" "$build_number" "$destination"
