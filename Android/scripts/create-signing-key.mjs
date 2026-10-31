#!/usr/bin/env node
import { chmodSync, copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, realpathSync, writeFileSync } from 'node:fs';
import { createHash, randomBytes } from 'node:crypto';
import { dirname, isAbsolute, join, parse, relative, resolve } from 'node:path';
import { homedir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

// 私钥只在指定的仓库外目录生成；路径可以展示，密码和私钥绝不写入日志。
const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const [privatePath, backupPath] = process.argv.slice(2);
if (!privatePath || !backupPath || !isAbsolute(privatePath) || !isAbsolute(backupPath)) {
  throw new Error('用法：create-signing-key.mjs <绝对私有目录> <绝对非同步备份目录>');
}
for (const directory of [privatePath, backupPath]) {
  const target = resolve(directory);
  const isAncestorOfProject = relative(target, projectRoot).split('/')[0] !== '..';
  if (target === parse(target).root || target === homedir() || isAncestorOfProject) {
    throw new Error('拒绝根目录、主目录或项目祖先目录；请使用专用空目录。');
  }
  if (relative(projectRoot, target).split('/')[0] !== '..') throw new Error('签名材料不能放入仓库。');
  if (existsSync(target) && readdirSync(target).length !== 0) throw new Error('签名目录必须为空，拒绝修改已有材料。');
  mkdirSync(directory, { recursive: true, mode: 0o700 });
  const actual = realpathSync(directory);
  if (relative(realpathSync(projectRoot), actual).split('/')[0] !== '..' ||
      relative(actual, realpathSync(projectRoot)).split('/')[0] !== '..' ||
      actual === realpathSync(homedir())) {
    throw new Error('签名目录的实际路径仍位于仓库内。');
  }
  chmodSync(directory, 0o700);
}
if (realpathSync(privatePath) === realpathSync(backupPath)) throw new Error('私有目录和备份目录不能相同。');
const names = ['android-release.p12', 'password.txt', 'signing.properties', 'certificate.der', 'RECOVERY.txt'];
if (names.some(name => existsSync(join(privatePath, name)) || existsSync(join(backupPath, name)))) {
  throw new Error('已有签名材料，拒绝覆盖；更新必须继续使用原密钥。');
}
const keytool = join(process.env.JAVA_HOME ?? '', 'bin/keytool');
if (!existsSync(keytool)) throw new Error('请先设置独立 JDK 17 的 JAVA_HOME。');
const passwordFile = join(privatePath, 'password.txt');
writeFileSync(passwordFile, randomBytes(48).toString('base64'), { mode: 0o600, flag: 'wx' });
const keystore = join(privatePath, 'android-release.p12');
execFileSync(keytool, ['-genkeypair', '-storetype', 'PKCS12', '-keystore', keystore,
  '-storepass:file', passwordFile, '-keypass:file', passwordFile, '-alias', 'mathinterviewvocab',
  '-keyalg', 'RSA', '-keysize', '3072', '-sigalg', 'SHA256withRSA', '-validity', '10000',
  '-dname', 'CN=MathInterviewVocab, OU=Android Release, O=MathInterviewVocab'], { stdio: ['ignore', 'pipe', 'pipe'] });
chmodSync(keystore, 0o600);
const certificate = join(privatePath, 'certificate.der');
execFileSync(keytool, ['-exportcert', '-keystore', keystore, '-storepass:file', passwordFile,
  '-alias', 'mathinterviewvocab', '-file', certificate], { stdio: ['ignore', 'pipe', 'pipe'] });
function properties(directory) {
  const escape = value => value.replaceAll('\\', '\\\\').replaceAll(':', '\\:').replaceAll('=', '\\=');
  return `storeFile=${escape(join(directory, 'android-release.p12'))}\nkeyAlias=mathinterviewvocab\n` +
    `storePasswordFile=${escape(join(directory, 'password.txt'))}\nkeyPasswordFile=${escape(join(directory, 'password.txt'))}\n`;
}
writeFileSync(join(privatePath, 'signing.properties'), properties(privatePath), { mode: 0o600, flag: 'wx' });
writeFileSync(join(privatePath, 'RECOVERY.txt'),
  '此目录包含长期 Android 签名私钥与恢复密码。不得提交 Git、上传网盘、发送聊天或放入 APK。\n' +
  '更新必须使用同一包名和此密钥。另行保存到受控离线介质；同一硬盘上的备份不能防止硬盘损坏。\n' +
  '恢复：设置 MATH_ANDROID_SIGNING_PROPERTIES 指向恢复目录的 signing.properties。\n', { mode: 0o600, flag: 'wx' });
for (const name of names) {
  const target = join(backupPath, name);
  if (name === 'signing.properties') writeFileSync(target, properties(backupPath), { mode: 0o600, flag: 'wx' });
  else { copyFileSync(join(privatePath, name), target); chmodSync(target, 0o600); }
}
// 实际打开备份密钥并导出证书，比只检查复制动作更可靠。
const restoredCertificate = execFileSync(keytool, ['-exportcert', '-keystore', join(backupPath, 'android-release.p12'),
  '-storepass:file', join(backupPath, 'password.txt'), '-alias', 'mathinterviewvocab'], { stdio: ['ignore', 'pipe', 'pipe'] });
const fingerprint = bytes => createHash('sha256').update(bytes).digest('hex');
if (fingerprint(restoredCertificate) !== fingerprint(readFileSync(certificate))) throw new Error('备份恢复验证失败。');
console.log(`长期签名密钥与本地备份已生成。公开证书 SHA-256：${fingerprint(restoredCertificate)}`);
console.log('请将恢复材料另存受控离线介质；不要发送密码或私钥。');
