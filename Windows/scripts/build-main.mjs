import { build } from 'esbuild';
import { cp, mkdir, readdir, readFile, writeFile, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import pngToIco from 'png-to-ico';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const stage = path.join(root, '.out/app');

async function copyBuildDirectory(source, destination) {
    await mkdir(destination, { recursive: true });
    const entries = await readdir(source, { withFileTypes: true });
    for (const entry of entries) {
        // 先按名称过滤同步临时文件，避免递归复制时访问已消失的元数据。
        if (entry.name.startsWith('.') || entry.name.endsWith('.baiduyun.uploading.cfg') || entry.name.endsWith('.tmp')) continue;
        const input = path.join(source, entry.name);
        const output = path.join(destination, entry.name);
        if (entry.isDirectory()) await copyBuildDirectory(input, output);
        else await cp(input, output);
    }
}

// 只重建本脚本自己的暂存目录，不触及现有 Mac 成品。
await rm(stage, { recursive: true, force: true });
await mkdir(path.join(stage, 'resources'), { recursive: true });
await build({ entryPoints: { main: 'src/main/main.ts', preload: 'src/main/preload.ts' },
    absWorkingDir: root, outdir: stage, outExtension: { '.js': '.cjs' }, bundle: true,
    platform: 'node', target: 'node22', format: 'cjs', external: ['electron'], sourcemap: false });
await build({ entryPoints: [path.join(root, 'src/shared/core.ts')], outfile: path.join(root, '.out/core-audit.mjs'),
    bundle: true, platform: 'node', target: 'node22', format: 'esm' });
await copyBuildDirectory(path.join(root, '.out/ui'), path.join(stage, 'ui'));
await cp(path.join(root, '../Resources/terms.json'), path.join(stage, 'resources/terms.json'));
await copyBuildDirectory(path.join(root, 'resources'), path.join(stage, 'resources'));
try { await readFile(path.join(stage, 'resources/audio-manifest.json')); }
catch {
    await writeFile(path.join(stage, 'resources/audio-manifest.json'), JSON.stringify({ version: 1, source: '音频资源准备中', entries: {} }));
}
const png = await sharp(path.join(root, 'resources/icon.svg')).resize(256, 256).png().toBuffer();
await writeFile(path.join(stage, 'resources/icon.png'), png);
await writeFile(path.join(root, '.out/icon.ico'), await pngToIco(png));
const metadata = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8'));
await writeFile(path.join(stage, 'package.json'), JSON.stringify({
    name: 'MathInterviewVocab', productName: metadata.productName, version: metadata.version,
    description: metadata.description, main: 'main.cjs', private: true,
}, null, 2));
console.log(`Built isolated application staging directory: ${stage}`);
