# Offline pronunciation sources

The Windows build contains pre-generated MP3 pronunciation clips. It does not
contain Google Translate audio, Google models, or a network text-to-speech
client.

## Kokoro model and voices

- Model: `hexgrad/Kokoro-82M`, release v1.0
- Model repository: <https://huggingface.co/hexgrad/Kokoro-82M>
- Pinned repository revision: `f3ff3571791e39611d31c381e3a41a3af07b4987`
- Model license declared by the publisher: Apache License 2.0
- Model file: `kokoro-v1_0.pth`
- Model SHA-256:
  `496dba118d1a58f5f3db2efc88dbdc216e0483fc89fe6e47ee1f2c53f18ad1e4`
- Official model card: <https://huggingface.co/hexgrad/Kokoro-82M/blob/f3ff3571791e39611d31c381e3a41a3af07b4987/README.md>
- Official voice list: <https://huggingface.co/hexgrad/Kokoro-82M/blob/f3ff3571791e39611d31c381e3a41a3af07b4987/VOICES.md>

The two accents use distinct official voice weights; one accent is never a
copy or relabeling of the other.

| App accent | Kokoro language | Voice | Voice-weight SHA-256 |
| --- | --- | --- | --- |
| American English (`en-US`) | `a` | `af_heart` | `0ab5709b8ffab19bfd849cd11d98f75b60af7733253ad0d67b12382a102cb4ff` |
| British English (`en-GB`) | `b` | `bf_emma` | `d0a423deabf4a52b4f49318c51742c54e21bb89bbbe9a12141e7758ddb5da701` |

The inference code came from `kokoro==0.9.4`, whose official source repository
is <https://github.com/hexgrad/kokoro> (revision
`dfb907a02bba8152ca444717ca5d78747ccb4bec`). The code repository also carries
the Apache License 2.0. Generation additionally used `torch==2.14.1` and
`soundfile==0.14.0` in an isolated build environment. These build-time Python
packages and the Kokoro model weights are not included in the application.

The full Apache License 2.0 text is distributed beside this file as
`KOKORO-APACHE-2.0.txt`. License and model notices describe upstream rights and
conditions; they are not an endorsement of this application by the Kokoro
authors.

## Generated files

- Source text: the canonical `Resources/terms.json` included with this project.
- Audio format: mono MPEG Layer III, 24 kHz, constant bitrate.
- Clip padding: 20 ms of leading silence and 120 ms of trailing silence are
  added before the single MP3 encode so playback does not clip the first or
  final sound.
- Generation speed: Kokoro `speed=1.0`; the app applies 0.75x, 1x, or 1.25x at
  playback time.
- File name: SHA-256 of UTF-8 `accent + "\n" + normalizedText`, followed by
  `.mp3`.
- Content integrity: every entry in `audio-manifest.json` records the exact
  MP3 byte count and content SHA-256.

Text normalization first trims Unicode boundary whitespace and punctuation,
then applies NFKC normalization, converts common apostrophes to ASCII `'`,
converts common dash characters to `-`, folds internal Unicode whitespace to a
single space, and preserves letter case.

The desired inventory contains both accents for every full `speechText`, every
complete Latin-script word, every contiguous complete-word selection that
preserves its internal punctuation, and a space-joined form of the same word
sequence. Selections that cross non-Latin letters or mathematical symbols are
not silently substituted; the app reports a missing offline clip. Full-term
pronunciation remains available through each term's normalized `speechText`.

`audio-manifest.json` is the authoritative audit record. A build must not claim
complete offline coverage unless `coverage.complete` is `true`,
`coverage.missingEntries` is `0`, and `coverage.failures` is empty.
