import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, unlink } from 'node:fs/promises';
import path from 'node:path';

export interface InstallerLaunchOptions {
    directory: string;
    parentPID?: number;
    platform?: NodeJS.Platform;
    quit: () => void;
}

export function installerArguments(parentPID: number, readyFile: string): string[] {
    if (!Number.isSafeInteger(parentPID) || parentPID <= 0) throw new Error('更新进程编号无效。');
    // 参数逐个传给 CreateProcess，不经 cmd、PowerShell 或 shell 插值。
    return ['/S', '--updated', '--force-run', '/currentuser', `--vocab-update-pid=${parentPID}`, `--vocab-update-ready=${readyFile}`];
}

/** NSIS 先打开父进程句柄并回执，再允许正常退出，避免 PID 复用和文件占用。 */
export async function launchInstaller(file: string, options: InstallerLaunchOptions): Promise<void> {
    if ((options.platform ?? process.platform) !== 'win32') throw new Error('安装更新仅支持 Windows 正式应用。');
    const parentPID = options.parentPID ?? process.pid;
    const handoffDirectory = path.join(options.directory, 'updates');
    await mkdir(handoffDirectory, { recursive: true, mode: 0o700 });
    const readyFile = path.join(handoffDirectory, `handoff-${randomUUID()}.ready`);
    const child = spawn(file, installerArguments(parentPID, readyFile), {
        detached: true, stdio: 'ignore', shell: false, windowsHide: false,
    });
    let failure: Error | null = null;
    let exited = false;
    child.once('error', error => { failure = error; });
    child.once('exit', () => { exited = true; });
    try {
        const deadline = Date.now() + 45_000;
        while (Date.now() < deadline) {
            if (failure) throw failure;
            if (exited) throw new Error('安装器未完成交接，应用尚未退出。请重试或打开下载文件。');
            try {
                const content = await readFile(readyFile, 'utf8');
                if (content !== String(parentPID)) throw new Error('安装器交接回执无效。');
                child.unref();
                options.quit();
                return;
            } catch (error) {
                if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
            }
            await new Promise(resolve => setTimeout(resolve, 75));
        }
        throw new Error('安装器启动超时，应用尚未退出。请检查系统安全提示后重试。');
    } finally {
        child.unref();
        await unlink(readyFile).catch(() => {});
    }
}
