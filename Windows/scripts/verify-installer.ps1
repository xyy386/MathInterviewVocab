#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$InitialInstaller,
    [Parameter(Mandatory)][string]$UpgradeInstaller,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$InitialVersion = '1.4.0',
    [string]$UpgradeVersion = '1.4.1'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 仅在一次性 Windows runner 中测试，不接触开发者已有安装和记录。
if (!$IsWindows -or $env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_OS -ne 'Windows') {
    throw 'This destructive fixture is restricted to a disposable GitHub Actions Windows runner.'
}
$runnerTemporary = [IO.Path]::GetFullPath($env:RUNNER_TEMP).TrimEnd('\')
$output = [IO.Path]::GetFullPath($OutputDirectory).TrimEnd('\')
if (!$output.StartsWith($runnerTemporary + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputDirectory must be a dedicated directory below RUNNER_TEMP.'
}
$initial = (Resolve-Path -LiteralPath $InitialInstaller).Path
$upgrade = (Resolve-Path -LiteralPath $UpgradeInstaller).Path
foreach ($installer in @($initial, $upgrade)) {
    if (![IO.Path]::GetFullPath($installer).StartsWith($runnerTemporary + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Only fixture installers below RUNNER_TEMP may be executed.'
    }
}
$installDirectory = Join-Path $env:LOCALAPPDATA 'Programs\MathInterviewVocab'
$dataDirectory = Join-Path $env:LOCALAPPDATA 'MathInterviewVocab'
$executable = Join-Path $installDirectory '数学面试词汇.exe'
if ((Test-Path -LiteralPath $installDirectory) -or (Test-Path -LiteralPath $dataDirectory)) {
    throw 'An existing installation or learning directory was found; nothing was modified.'
}
$evidence = Join-Path $output 'evidence'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$results = [Collections.Generic.List[object]]::new()
$ownedInstallers = [Collections.Generic.List[object]]::new()
$transcript = Join-Path $evidence 'native-installer.log'
Start-Transcript -LiteralPath $transcript | Out-Null

# 只点击本次安装器进程的标准 Next/Agree/Install/Finish 按钮，不操作系统安全对话框。
Add-Type @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class VocabInstallerButtons {
    [DllImport("user32.dll")] public static extern IntPtr GetDlgItem(IntPtr parent, int id);
    [DllImport("user32.dll")] public static extern bool IsWindowEnabled(IntPtr window);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr window, StringBuilder text, int count);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);
}
'@

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}

function Wait-Condition([scriptblock]$Condition, [int]$Seconds, [string]$Message) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    do {
        if (& $Condition) { return }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    throw $Message
}

function Find-FixtureProcesses {
    @(
        Get-Process -ErrorAction SilentlyContinue | Where-Object {
            try { $_.Path -and [string]::Equals($_.Path, $executable, [StringComparison]::OrdinalIgnoreCase) }
            catch { $false }
        }
    )
}

function Wait-FixtureWindow {
    $script:fixtureWindow = $null
    Wait-Condition {
        $script:fixtureWindow = Find-FixtureProcesses | Where-Object { $_.MainWindowHandle -ne [IntPtr]::Zero } | Select-Object -First 1
        $null -ne $script:fixtureWindow
    } 45 'The installed EXE did not create a native application window.'
    Start-Sleep -Seconds 2
    $script:fixtureWindow.Refresh()
    Assert-Condition (!$script:fixtureWindow.HasExited) 'The installed application exited immediately after launch.'
    return $script:fixtureWindow
}

function Close-FixtureWindow([Diagnostics.Process]$Process, [switch]$AllowRelaunch) {
    $Process.Refresh()
    if ($Process.HasExited) { return }
    Assert-Condition ([string]::Equals($Process.Path, $executable, [StringComparison]::OrdinalIgnoreCase)) 'Refusing to close a process outside the fixture installation.'
    Assert-Condition ($Process.CloseMainWindow()) 'The fixture application refused a normal window-close request.'
    Assert-Condition ($Process.WaitForExit(30000)) 'The fixture application did not quit gracefully.'
    if (!$AllowRelaunch) {
        Wait-Condition { @(Find-FixtureProcesses).Count -eq 0 } 20 'Fixture child processes remained after graceful quit.'
    }
}

function Start-Installer([string]$File, [string[]]$InstallerArguments, [string]$Name) {
    Write-Host "Starting installer case: $Name"
    $process = Start-Process -FilePath $File -ArgumentList $InstallerArguments -PassThru
    $null = $process.Handle
    $ownedInstallers.Add(@{ Process = $process; Path = $File })
    return $process
}

function Complete-Installer([Diagnostics.Process]$Process, [string]$Name, [bool]$ShouldSucceed, [int]$TimeoutSeconds = 90) {
    Assert-Condition ($Process.WaitForExit($TimeoutSeconds * 1000)) "Installer timed out: $Name"
    $exitCode = $Process.ExitCode
    Assert-Condition ($exitCode -is [int]) "Installer exit code was not captured: $Name"
    if ($ShouldSucceed) {
        Assert-Condition ($exitCode -eq 0) "Installer failed ($exitCode): $Name"
    } else {
        Assert-Condition ($exitCode -ne 0) "Installer incorrectly reported success: $Name"
    }
    $results.Add([ordered]@{ name = $Name; passed = $true; exitCode = $exitCode })
}

function Complete-AssistedInstaller([Diagnostics.Process]$Process) {
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([DateTime]::UtcNow -lt $deadline) {
        $Process.Refresh()
        if ($Process.HasExited) { return }
        Assert-Condition ([string]::Equals($Process.Path, $initial, [StringComparison]::OrdinalIgnoreCase)) 'The installer process path changed unexpectedly.'
        if ($Process.MainWindowHandle -ne [IntPtr]::Zero) {
            $button = [VocabInstallerButtons]::GetDlgItem($Process.MainWindowHandle, 1)
            if ($button -ne [IntPtr]::Zero -and [VocabInstallerButtons]::IsWindowEnabled($button)) {
                $caption = [Text.StringBuilder]::new(160)
                [void][VocabInstallerButtons]::GetWindowText($button, $caption, $caption.Capacity)
                $text = $caption.ToString().Replace('&', '')
                Assert-Condition ($text -match 'Next|Agree|Install|Finish|下一步|同意|接受|安装|完成') "Unexpected installer action: $text"
                Write-Host "Assisted installer action: $text"
                Assert-Condition ([VocabInstallerButtons]::PostMessage($button, 0x00F5, [IntPtr]::Zero, [IntPtr]::Zero)) 'The installer button did not accept a native click event.'
            }
        }
        Start-Sleep -Milliseconds 400
    }
    throw 'The assisted installer did not complete within the bounded UI fixture.'
}

function Assert-InstalledVersion([string]$Expected) {
    Assert-Condition (Test-Path -LiteralPath $executable -PathType Leaf) 'The installed EXE is missing.'
    $version = [Diagnostics.FileVersionInfo]::GetVersionInfo($executable)
    $product = '{0}.{1}.{2}' -f $version.ProductMajorPart, $version.ProductMinorPart, $version.ProductBuildPart
    Assert-Condition ($product -eq $Expected) "Unexpected installed version: $product instead of $Expected."
    Assert-Condition (Test-Path -LiteralPath (Join-Path $installDirectory 'resources\app.asar')) 'The installed application archive is missing.'
}

function Get-RecordHashes {
    $progress = (Get-FileHash -LiteralPath (Join-Path $dataDirectory 'progress.json') -Algorithm SHA256).Hash
    $settings = (Get-FileHash -LiteralPath (Join-Path $dataDirectory 'settings.json') -Algorithm SHA256).Hash
    return "$progress/$settings"
}

function Assert-Records([string]$ExpectedHashes) {
    Assert-Condition ((Get-RecordHashes) -eq $ExpectedHashes) 'Learning records or pronunciation settings changed during installation.'
}

function New-Handoff([int]$ParentID) {
    $directory = Join-Path $dataDirectory 'updates'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $ready = Join-Path $directory ('handoff-' + [Guid]::NewGuid().ToString() + '.ready')
    return @{
        Ready = $ready
        Arguments = @('/S', '--updated', '--force-run', '/currentuser', "--vocab-update-pid=$ParentID", "--vocab-update-ready=`"$ready`"")
    }
}

function Wait-Handoff([hashtable]$Handoff, [int]$ParentID, [Diagnostics.Process]$InstallerProcess) {
    Wait-Condition {
        $InstallerProcess.Refresh()
        if ($InstallerProcess.HasExited) { throw 'The installer exited before opening the parent process handle.' }
        Test-Path -LiteralPath $Handoff.Ready -PathType Leaf
    } 15 'The installer did not acknowledge the parent-process handoff.'
    Assert-Condition (([IO.File]::ReadAllText($Handoff.Ready)) -eq [string]$ParentID) 'The installer acknowledged the wrong parent process.'
}

$failure = $null
try {
    # 首次安装和真实 EXE 启动。
    $process = Start-Installer $initial @('/currentuser') 'assisted-initial-install'
    Complete-AssistedInstaller $process
    Complete-Installer $process 'assisted-initial-install' $true
    Assert-InstalledVersion $InitialVersion
    $window = Wait-FixtureWindow
    Close-FixtureWindow $window

    # 构造可重复核验的收藏、错题、答题历史和英式语音设置。
    New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
    $progress = [ordered]@{
        attempts = @([ordered]@{
            id = '11111111-1111-4111-8111-111111111111'; termID = 104; direction = 'enToZh'
            selectedOptionID = 1; correctOptionID = 104; answeredAt = '2026-11-18T00:00:00Z'
        })
        favoriteIDs = @(104, 52); correctStreak = @{ '104' = 0; '52' = 3 }; wrongIDs = @(104); masteredIDs = @(52)
        quizPreferences = [ordered]@{ direction = 'enToZh'; scope = 'favorites'; questionLength = 'all'; selectedSubjectID = 'statistics'; includeMastered = $false }
    }
    $progress | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $dataDirectory 'progress.json') -Encoding utf8NoBOM
    @{ accent = 'en-GB'; speed = 0.75 } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $dataDirectory 'settings.json') -Encoding utf8NoBOM
    $recordHashes = Get-RecordHashes
    $originalExecutableHash = (Get-FileHash -LiteralPath $executable -Algorithm SHA256).Hash
    Start-Process -FilePath $executable | Out-Null
    $window = Wait-FixtureWindow
    Assert-Records $recordHashes

    # 失败路径不得结束旧应用、覆盖文件或误报安装成功。
    $process = Start-Installer $upgrade @('/S', '--updated', '/currentuser') 'missing-parent-handoff'
    Complete-Installer $process 'missing-parent-handoff' $false
    $wrongReady = Join-Path $output 'handoff-outside-private-directory.ready'
    $process = Start-Installer $upgrade @('/S', '--updated', '/currentuser', "--vocab-update-pid=$($window.Id)", "--vocab-update-ready=`"$wrongReady`"") 'invalid-handoff-path'
    Complete-Installer $process 'invalid-handoff-path' $false
    Assert-Condition (!(Test-Path -LiteralPath $wrongReady)) 'The installer wrote a handoff outside its private directory.'
    $process = Start-Installer $upgrade @('/S', '/currentuser') 'running-application-refused'
    Complete-Installer $process 'running-application-refused' $false
    $process = Start-Installer $upgrade @('/S', '/allusers') 'machine-install-refused'
    Complete-Installer $process 'machine-install-refused' $false
    $unexpectedDirectory = Join-Path $output 'must-not-install-here'
    $process = Start-Installer $upgrade @('/S', '/currentuser', "/D=$unexpectedDirectory") 'custom-install-directory-refused'
    Complete-Installer $process 'custom-install-directory-refused' $false
    # NSIS 在 .onInit 之前会 SetOutPath，可能留下空目录；不得安装文件或删除已有内容。
    if (Test-Path -LiteralPath $unexpectedDirectory) {
        Assert-Condition (@(Get-ChildItem -LiteralPath $unexpectedDirectory -Force).Count -eq 0) 'A rejected installer wrote files to the caller-selected directory.'
    } else {
        New-Item -ItemType Directory -Path $unexpectedDirectory | Out-Null
    }
    $protectedFile = Join-Path $unexpectedDirectory 'existing-user-file.txt'
    'preserve this existing file' | Set-Content -LiteralPath $protectedFile -Encoding utf8NoBOM
    $protectedHash = (Get-FileHash -LiteralPath $protectedFile -Algorithm SHA256).Hash
    $process = Start-Installer $upgrade @('/S', '/currentuser', "/D=$unexpectedDirectory") 'existing-directory-preserved'
    Complete-Installer $process 'existing-directory-preserved' $false
    Assert-Condition ((Get-FileHash -LiteralPath $protectedFile -Algorithm SHA256).Hash -eq $protectedHash) 'A rejected installer changed an existing file.'
    Assert-Condition (@(Get-ChildItem -LiteralPath $unexpectedDirectory -Force).Count -eq 1) 'A rejected installer added files to an existing directory.'
    $handoff = New-Handoff $window.Id
    $process = Start-Installer $upgrade $handoff.Arguments 'parent-exit-timeout'
    Wait-Handoff $handoff $window.Id $process
    Complete-Installer $process 'parent-exit-timeout' $false 50
    $window.Refresh()
    Assert-Condition (!$window.HasExited) 'A rejected installer terminated the running application.'
    Assert-Condition ((Get-FileHash -LiteralPath $executable -Algorithm SHA256).Hash -eq $originalExecutableHash) 'A rejected installer modified the current EXE.'
    Assert-InstalledVersion $InitialVersion
    Assert-Records $recordHashes

    # 安装器先确认持有父进程句柄，再正常退出旧应用。
    $handoff = New-Handoff $window.Id
    $process = Start-Installer $upgrade $handoff.Arguments 'confirmed-upgrade'
    Wait-Handoff $handoff $window.Id $process
    Close-FixtureWindow $window -AllowRelaunch
    Complete-Installer $process 'confirmed-upgrade' $true
    Assert-InstalledVersion $UpgradeVersion
    $upgradedWindow = Wait-FixtureWindow
    Assert-Records $recordHashes
    $results.Add([ordered]@{ name = 'relaunch-and-data-preservation'; passed = $true; installedVersion = $UpgradeVersion })
    Close-FixtureWindow $upgradedWindow

    # 卸载只删除应用，不删除学习记录。
    $uninstallRoot = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall'
    $entry = Get-ChildItem -LiteralPath $uninstallRoot | ForEach-Object { Get-ItemProperty -LiteralPath $_.PSPath } | Where-Object {
        $_.PSObject.Properties.Name -contains 'InstallLocation' -and
        [string]::Equals(([string]$_.InstallLocation).TrimEnd('\'), $installDirectory.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase)
    } | Select-Object -First 1
    Assert-Condition ($null -ne $entry) 'The per-user uninstall registration is missing.'
    Assert-Condition ($entry.UninstallString -match '^"([^"]+\.exe)"') 'The uninstall command has an unexpected format.'
    $uninstaller = [IO.Path]::GetFullPath($Matches[1])
    Assert-Condition ($uninstaller.StartsWith($installDirectory + '\', [StringComparison]::OrdinalIgnoreCase)) 'The uninstaller is outside the fixture installation.'
    $process = Start-Installer $uninstaller @('/S', '/currentuser') 'uninstall-preserves-data'
    Complete-Installer $process 'uninstall-preserves-data' $true
    Wait-Condition { !(Test-Path -LiteralPath $executable) } 30 'The uninstaller left the installed EXE behind.'
    Assert-Records $recordHashes
} catch {
    $failure = $_.ToString()
    Write-Host "Native installer smoke failed: $failure"
} finally {
    # 仅清理本次一次性 runner 中精确路径匹配的夹具进程。
    foreach ($item in $ownedInstallers) {
        $process = $item.Process
        try {
            $process.Refresh()
            if (!$process.HasExited -and [string]::Equals($process.Path, $item.Path, [StringComparison]::OrdinalIgnoreCase)) {
                Stop-Process -Id $process.Id -Force
            }
        } catch { Write-Warning 'Could not stop an owned installer fixture process.' }
    }
    foreach ($process in @(Find-FixtureProcesses)) {
        try {
            if ([string]::Equals($process.Path, $executable, [StringComparison]::OrdinalIgnoreCase)) { Stop-Process -Id $process.Id -Force }
        } catch { Write-Warning 'Could not stop an owned application fixture process.' }
    }
    [ordered]@{
        passed = $null -eq $failure
        execution = 'Native Windows GitHub-hosted runner; not physical-device or full UI acceptance'
        os = [Environment]::OSVersion.VersionString
        initialVersion = $InitialVersion; upgradedVersion = $UpgradeVersion
        cases = $results; failure = $failure; productionSigningKeysUsed = $false; parallelsUsed = $false
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $evidence 'native-installer-result.json') -Encoding utf8NoBOM
    Stop-Transcript | Out-Null
}
if ($failure) { throw $failure }
