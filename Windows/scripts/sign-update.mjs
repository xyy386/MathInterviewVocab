import { createHash, createPrivateKey, createPublicKey, sign, verify } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
if (args.length !== 6 || args[0] !== '--installer' || args[2] !== '--key' || args[4] !== '--output') {
    throw new Error('Usage: node scripts/sign-update.mjs --installer FILE --key PRIVATE_KEY_FILE --output DIRECTORY');
}
const metadata = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8'));
const installer = path.resolve(args[1]);
const expectedName = `MathInterviewVocab-Windows-x64-Setup-v${metadata.version}.exe`;
if (path.basename(installer) !== expectedName) throw new Error('安装包名称与当前源码版本不一致。');
const file = await readFile(installer);
if (file.length < 1024 || file.toString('ascii', 0, 2) !== 'MZ') throw new Error('安装包不是 Windows PE 文件。');
const privateKey = createPrivateKey(await readFile(path.resolve(args[3])));
if (privateKey.asymmetricKeyType !== 'ed25519') throw new Error('仅支持 Ed25519 发布签名密钥。');
const source = await readFile(path.join(root, 'src/main/update-signature.ts'), 'utf8');
const pinned = /-----BEGIN PUBLIC KEY-----[\s\S]+?-----END PUBLIC KEY-----/.exec(source)?.[0];
if (!pinned || createPublicKey(privateKey).export({ type: 'spki', format: 'pem' }).toString().trim() !== pinned.trim()) {
    throw new Error('签名私钥与应用内置公钥不匹配；未生成发布文件。');
}
const manifest = Buffer.from(JSON.stringify({ schema: 1, platform: 'windows-x64', version: metadata.version,
    fileName: expectedName, bytes: file.length, sha256: createHash('sha256').update(file).digest('hex') }, null, 2) + '\n');
const signature = sign(null, manifest, privateKey);
if (!verify(null, manifest, createPublicKey(pinned), signature)) throw new Error('签名回读校验失败。');
const output = path.resolve(args[5]);
await mkdir(output, { recursive: true });
await writeFile(path.join(output, 'Windows-update.json'), manifest, { flag: 'wx' });
await writeFile(path.join(output, 'Windows-update.json.sig'), signature.toString('base64') + '\n', { flag: 'wx' });
console.log(`Signed Windows manifest for ${expectedName}; private key was not copied or logged.`);
