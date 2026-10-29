"""提前生成离线音频；此脚本与模型仅用于构建，不进入用户运行链。"""

import argparse
import hashlib
import json
import os
import time
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import soundfile as sf
import torch
from huggingface_hub import hf_hub_download
from kokoro import KModel, KPipeline


REPOSITORY = 'hexgrad/Kokoro-82M'
REVISION = 'f3ff3571791e39611d31c381e3a41a3af07b4987'
SOURCE = 'Kokoro-82M v1.0 (Apache-2.0); af_heart en-US; bf_emma en-GB'
VOICES = {'en-US': ('a', 'af_heart'), 'en-GB': ('b', 'bf_emma')}
EXPECTED_HASHES = {
    'kokoro-v1_0.pth': '496dba118d1a58f5f3db2efc88dbdc216e0483fc89fe6e47ee1f2c53f18ad1e4',
    'voices/af_heart.pt': '0ab5709b8ffab19bfd849cd11d98f75b60af7733253ad0d67b12382a102cb4ff',
    'voices/bf_emma.pt': 'd0a423deabf4a52b4f49318c51742c54e21bb89bbbe9a12141e7758ddb5da701',
}


def file_hash(file):
    digest = hashlib.sha256()
    with file.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def save_manifest(output, entries, desired, failures):
    missing = [item for item in desired if item['key'] not in entries]
    payload = {
        'version': 1, 'source': SOURCE,
        'generatedAt': datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
        'entries': dict(sorted(entries.items())),
        'coverage': {
            'expectedEntries': len(desired), 'availableEntries': len(entries),
            'missingEntries': len(missing), 'complete': not missing and not failures,
            'missing': missing, 'failures': list(failures.values()),
        },
    }
    temporary = output / 'audio-manifest.json.tmp'
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    os.replace(temporary, output / 'audio-manifest.json')


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path, default=root / 'resources/audio-manifest.json')
    parser.add_argument('--output', type=Path, default=root / '.out/audio-generated')
    parser.add_argument('--device', choices=['cpu', 'mps'], default='cpu')
    parser.add_argument('--threads', type=int, default=4)
    args = parser.parse_args()
    if not 1 <= args.threads <= 16:
        parser.error('--threads 必须在 1 到 16 之间。')

    # 复用 JavaScript 规范化后的目标库存，避免两种语言的 Unicode 细节漂移。
    inventory = json.loads(args.inventory.read_text(encoding='utf-8'))
    desired = list(inventory['coverage'].get('missing', []))
    desired += [dict(key=key, text=value['text'], accent=value['accent'], file=value['file'])
                for key, value in inventory['entries'].items()]
    desired = list({item['key']: item for item in desired}.values())
    if not desired or len(desired) != inventory['coverage']['expectedEntries']:
        raise ValueError('库存不完整，请先运行 node scripts/prepare-audio.mjs --inventory。')
    for item in desired:
        key = hashlib.sha256((item['accent'] + '\n' + item['text']).encode()).hexdigest()
        if key != item['key'] or item['file'] != key + '.mp3' or item['accent'] not in VOICES:
            raise ValueError('无效音频库存条目。')

    output = args.output.resolve()
    audio_directory = output / 'audio'
    audio_directory.mkdir(parents=True, exist_ok=True)
    manifest_file = output / 'audio-manifest.json'
    previous = json.loads(manifest_file.read_text(encoding='utf-8')) if manifest_file.exists() else {}
    if previous and previous.get('source') != SOURCE:
        raise ValueError('输出目录包含不同音源，拒绝覆盖。')
    entries = {}
    for key, entry in previous.get('entries', {}).items():
        file = audio_directory / f'{key}.mp3'
        if entry.get('file') == file.name and file.is_file() and file.stat().st_size == entry['bytes'] and file_hash(file) == entry['sha256']:
            entries[key] = entry
    failures = {}

    # 固定模型 revision，按发布记录的哈希验证两种声线。
    files = {name: Path(hf_hub_download(REPOSITORY, name, revision=REVISION))
             for name in ['config.json', *EXPECTED_HASHES]}
    for name, expected in EXPECTED_HASHES.items():
        if file_hash(files[name]) != expected:
            raise ValueError(f'模型或声线校验失败：{name}')
    torch.set_grad_enabled(False)
    torch.set_num_threads(args.threads)
    if args.device == 'mps' and not torch.backends.mps.is_available():
        raise RuntimeError('MPS 不可用，请使用 --device cpu。')
    model = KModel(repo_id=REPOSITORY, config=str(files['config.json']), model=str(files['kokoro-v1_0.pth'])).to(args.device).eval()
    pipelines = {accent: KPipeline(lang_code=language, repo_id=REPOSITORY, model=model, device=args.device)
                 for accent, (language, _) in VOICES.items()}
    voice_weights = {accent: torch.load(files[f'voices/{voice}.pt'], map_location='cpu', weights_only=True)
                     for accent, (_, voice) in VOICES.items()}

    started = time.perf_counter()
    processed = 0
    for item in desired:
        key = item['key']
        if key in entries:
            continue
        processed += 1
        try:
            generated = list(pipelines[item['accent']](item['text'], voice=voice_weights[item['accent']], speed=1.0, split_pattern=None))
            parts = [result.audio.detach().cpu() for result in generated if result.audio is not None and result.audio.numel()]
            if not parts:
                raise RuntimeError('语音模型没有返回音频。')
            samples = torch.cat(parts).numpy()
            if len(samples) < 2400 or not np.isfinite(samples).all():
                raise RuntimeError('音频长度或采样值异常。')
            samples = np.pad(samples, (480, 2880), mode='constant')
            destination = audio_directory / item['file']
            sf.write(destination, samples, 24000, format='MP3', subtype='MPEG_LAYER_III', compression_level=0.5, bitrate_mode='CONSTANT')
            entries[key] = {'text': item['text'], 'accent': item['accent'], 'file': item['file'],
                            'sha256': file_hash(destination), 'bytes': destination.stat().st_size}
        except Exception as error:
            failures[key] = {'key': key, 'text': item['text'], 'accent': item['accent'], 'error': str(error)}
        if processed % 30 == 0:
            save_manifest(output, entries, desired, failures)
            print(f'{len(entries)}/{len(desired)} ready; {len(failures)} failures; {processed / (time.perf_counter() - started):.2f} clips/s', flush=True)
    save_manifest(output, entries, desired, failures)
    print(f'Finished: {len(entries)}/{len(desired)}, {len(failures)} failures. Output: {output}', flush=True)
    if len(entries) != len(desired) or failures:
        raise SystemExit(2)


if __name__ == '__main__':
    main()
