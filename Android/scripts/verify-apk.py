#!/usr/bin/env python3
"""核对正式 APK 的身份、签名与全部离线资源；不安装、不访问学习记录。"""
import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def run(*args):
    return subprocess.run(args, check=True, text=True, capture_output=True).stdout


def verify(apk, sdk):
    root = Path(__file__).resolve().parents[2]
    tools = sdk / "build-tools" / "36.0.0"
    signature = run(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    certificates = [value.lower() for value in re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signature)]
    expected = (root / "Android/release-certificate.sha256").read_text().strip()
    if certificates != [expected]:
        raise ValueError("APK 不是项目长期正式证书签名。")
    badging = run(str(tools / "aapt2"), "dump", "badging", str(apk))
    identity = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not identity or identity.groups() != ("com.xingyiyao.mathinterviewvocab", "1", "1.2.0"):
        raise ValueError("包名、versionCode 或 versionName 不符合 Android 1.2.0。")
    if not re.search(r"^(?:minSdkVersion|sdkVersion):'29'$", badging, re.M) or not re.search(r"^targetSdkVersion:'36'$", badging, re.M):
        raise ValueError("Android API 范围不符合 29 / 36。")
    if "application-debuggable" in badging:
        raise ValueError("调试 APK 不能作为正式成品。")
    permissions = set(re.findall(r"uses-permission: name='([^']+)'", badging))
    # AndroidX Core 合并的应用自有签名权限保护不可导出的动态接收器，不是新增用户授权。
    expected_permissions = {"android.permission.INTERNET", "android.permission.REQUEST_INSTALL_PACKAGES",
                            "com.xingyiyao.mathinterviewvocab.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
    if permissions != expected_permissions:
        raise ValueError(f"发现非预期权限：{permissions}")
    manifest = run(str(tools / "aapt2"), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml")
    if not re.search(r":allowBackup\(0x01010280\)=(?:false|\(type 0x12\)0x0)$", manifest, re.M):
        raise ValueError("APK 未明确关闭自动备份。")
    if not re.search(r":usesCleartextTraffic\(0x010104ec\)=(?:false|\(type 0x12\)0x0)$", manifest, re.M):
        raise ValueError("APK 未明确禁止明文网络。")
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise ValueError("APK ZIP 数据损坏。")
        names = archive.namelist()
        if len(set(names)) != len(names):
            raise ValueError("APK 含重复路径。")
        if any("baiduyun" in name.lower() or name.startswith("__MACOSX/") for name in names):
            raise ValueError("APK 含同步临时文件。")
        canonical_terms = (root / "Resources/terms.json").read_bytes()
        if archive.read("assets/terms.json") != canonical_terms:
            raise ValueError("APK 词库与规范资源不一致。")
        terms = json.loads(canonical_terms)
        if len(terms) != 326 or sum(item["important"] for item in terms) != 130:
            raise ValueError("词库数量错误。")
        canonical_manifest = (root / "Windows/resources/audio-manifest.json").read_bytes()
        if archive.read("assets/audio-manifest.json") != canonical_manifest:
            raise ValueError("APK 音频索引与规范资源不一致。")
        entries = json.loads(canonical_manifest)["entries"]
        audio_names = {name for name in names if name.startswith("assets/audio/") and not name.endswith("/")}
        expected_names = {f'assets/audio/{entry["file"]}' for entry in entries.values()}
        if len(entries) != 2328 or audio_names != expected_names:
            raise ValueError("APK 音频清单有缺失或多余项。")
        for entry in entries.values():
            data = archive.read(f'assets/audio/{entry["file"]}')
            if len(data) != entry["bytes"] or sha256(data) != entry["sha256"]:
                raise ValueError(f'APK 音频校验失败：{entry["file"]}')
        for bundled, source in [
            ("assets/licenses/PROJECT-MIT.txt", root / "LICENSE"),
            ("assets/licenses/AUDIO-SOURCES.md", root / "Windows/resources/AUDIO-SOURCES.md"),
            ("assets/licenses/KOKORO-APACHE-2.0.txt", root / "Windows/resources/KOKORO-APACHE-2.0.txt"),
            ("assets/licenses/THIRD-PARTY-NOTICES.md", root / "Android/THIRD-PARTY-NOTICES.md"),
        ]:
            if archive.read(bundled) != source.read_bytes():
                raise ValueError("APK 许可证缺失或不一致。")
        # Compose UI graphics-path 随附 4 个通用 Android ABI 的路径原生库；核对完整 Universal ABI 覆盖且无其他原生库。
        native_libs = {name for name in names if name.startswith("lib/") and not name.endswith("/")}
        expected_native_libs = {
            f"lib/{abi}/libandroidx.graphics.path.so"
            for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
        if native_libs != expected_native_libs:
            raise ValueError(f"原生库或 Universal ABI 覆盖不符合预期：{sorted(native_libs)}")
    return {"packageName": identity.group(1), "versionName": "1.2.0", "versionCode": 1,
            "sha256": sha256(apk.read_bytes()), "bytes": apk.stat().st_size,
            "certificateSHA256": expected, "terms": 326, "importantTerms": 130, "audioClips": 2328}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--sdk", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.apk.resolve(strict=True), args.sdk.resolve(strict=True)), ensure_ascii=False, indent=2))
