"""完整解码每段离线 MP3，检查采样率、非空波形与数值有效性。"""

import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np
import soundfile as sf


def main():
    resources = Path(__file__).resolve().parents[1] / 'resources'
    manifest = json.loads((resources / 'audio-manifest.json').read_text(encoding='utf-8'))

    def inspect(entry):
        samples, sample_rate = sf.read(resources / 'audio' / entry['file'], dtype='float32')
        if sample_rate != 24000 or samples.ndim != 1 or len(samples) < 2400:
            raise ValueError(f"无效采样格式：{entry['file']}")
        if not np.isfinite(samples).all() or np.sqrt(np.mean(samples ** 2)) < 0.00001:
            raise ValueError(f"无效或静音波形：{entry['file']}")
        return len(samples) / sample_rate

    with ThreadPoolExecutor(max_workers=4) as executor:
        durations = list(executor.map(inspect, manifest['entries'].values()))
    if not durations or not manifest['coverage']['complete']:
        raise ValueError('音频资源尚未齐全。')
    print(json.dumps({
        'decodedFiles': len(durations), 'sampleRate': 24000, 'channels': 1,
        'minimumSeconds': min(durations), 'maximumSeconds': max(durations),
        'totalSeconds': sum(durations), 'errors': 0,
    }, indent=2))


if __name__ == '__main__':
    main()
