import { createPublicKey, verify } from 'node:crypto';

// 仅公钥进入应用；发布私钥保存在仓库外，并独立备份。
export const windowsUpdatePublicKey = `-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAARXNq1u+RRrwflu32ZY5Qf6A3mvnoGIcdp8DHNkbPMI=
-----END PUBLIC KEY-----`;

export interface SignedInstaller {
    schema: 1;
    platform: 'windows-x64';
    version: string;
    fileName: string;
    bytes: number;
    sha256: string;
}

/** 先验证原始字节签名，再解析字段；不重序列化签名消息。 */
export function verifyInstallerManifest(
    bytes: Buffer,
    signatureBytes: Buffer,
    expected: { version: string; fileName: string; bytes: number; sha256?: string },
    publicKey = windowsUpdatePublicKey,
): Readonly<SignedInstaller> {
    if (bytes.length > 4096 || signatureBytes.length > 1024) throw new Error('更新签名信息超过允许大小。');
    const signatureText = signatureBytes.toString('utf8').trim();
    if (!/^[A-Za-z0-9+/]{86}==$/.test(signatureText)) throw new Error('更新签名格式无效。');
    const key = createPublicKey(publicKey);
    if (key.asymmetricKeyType !== 'ed25519' || !verify(null, bytes, key, Buffer.from(signatureText, 'base64'))) {
        throw new Error('更新包数字签名校验失败，已阻止安装。');
    }
    const manifest: unknown = JSON.parse(bytes.toString('utf8'));
    if (!manifest || typeof manifest !== 'object' || Array.isArray(manifest)) throw new Error('更新清单格式无效。');
    const value = manifest as Record<string, unknown>;
    const keys = Object.keys(value).sort().join(',');
    if (keys !== 'bytes,fileName,platform,schema,sha256,version'
        || value.schema !== 1 || value.platform !== 'windows-x64'
        || value.version !== expected.version || value.fileName !== expected.fileName
        || value.bytes !== expected.bytes || !Number.isSafeInteger(value.bytes) || Number(value.bytes) <= 0
        || typeof value.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(value.sha256)
        || (expected.sha256 && value.sha256 !== expected.sha256)) {
        throw new Error('签名更新清单与正式发布附件不一致，已阻止安装。');
    }
    return Object.freeze(value as unknown as SignedInstaller);
}
