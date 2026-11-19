; 只扩展正式 NSIS 安装器的交接门禁，不实现自制目录覆盖或强制终止。
!include FileFunc.nsh
!include LogicLib.nsh

!ifndef BUILD_UNINSTALLER
  Var VocabParentPID
  Var VocabReadyFile
  Var VocabParentHandle
!endif
Var VocabProcessRetries

!macro customInstallMode
  StrCpy $isForceCurrentInstall "1"
!macroend

!ifndef BUILD_UNINSTALLER
  ; 向导的用户模式页会重新计算目录，进入安装页前再次固定到专用目录。
  !macro customPageAfterChangeDir
    !define MUI_PAGE_CUSTOMFUNCTION_PRE vocabFixedInstallationDirectory
  !macroend

  Function vocabFixedInstallationDirectory
    StrCpy $INSTDIR "$LOCALAPPDATA\Programs\MathInterviewVocab"
  FunctionEnd
!endif

!macro vocabFailure message code
  MessageBox MB_OK|MB_ICONSTOP "${message}" /SD IDOK
  SetErrorLevel ${code}
  Quit
!macroend

!macro customInit
  SetShellVarContext current
  StrCpy $installMode "CurrentUser"
  StrCpy $hasPerMachineInstallation "0"
  StrCpy $hasPerUserInstallation "1"
  StrCpy $INSTDIR "$LOCALAPPDATA\Programs\MathInterviewVocab"
  ${If} ${isForAllUsers}
    !insertmacro vocabFailure "此安装包仅支持当前用户安装，不申请管理员权限。" 20
  ${EndIf}
  ; /D 是 NSIS 特殊参数，普通 GetParameters 可能不包含它。
  !insertmacro GetDParameter $R1
  ${If} $R1 != ""
    !insertmacro vocabFailure "此安装包使用固定的当前用户安装目录，不支持 /D 参数。" 20
  ${EndIf}

  ${If} ${isUpdated}
    ${StdUtils.GetParameter} $VocabParentPID "vocab-update-pid" ""
    ${StdUtils.GetParameter} $VocabReadyFile "vocab-update-ready" ""
    ${If} $VocabParentPID <= 0
      !insertmacro vocabFailure "更新交接参数无效；程序文件尚未替换。请重新打开旧版并重试。" 21
    ${EndIf}
    ${GetParent} "$VocabReadyFile" $R1
    ${GetFileName} "$VocabReadyFile" $R2
    StrCpy $R3 $R2 8
    StrCpy $R4 $R2 6 -6
    ${If} $R1 != "$LOCALAPPDATA\MathInterviewVocab\updates"
    ${OrIf} $R3 != "handoff-"
    ${OrIf} $R4 != ".ready"
      !insertmacro vocabFailure "更新交接路径无效；程序文件尚未替换。" 22
    ${EndIf}
    ${If} ${FileExists} "$VocabReadyFile"
      !insertmacro vocabFailure "更新交接回执已存在，请重新下载或重试。" 23
    ${EndIf}

    ; 打开真实父进程句柄后才回执；随后等待的不是可复用的 PID。
    System::Call 'kernel32::OpenProcess(i 0x00100000, i 0, i $VocabParentPID) p .r0'
    StrCpy $VocabParentHandle $0
    ${If} $VocabParentHandle == 0
      !insertmacro vocabFailure "无法确认旧版进程，已取消安装；不会强制关闭任何程序。" 24
    ${EndIf}
    ClearErrors
    FileOpen $R5 "$VocabReadyFile.partial" w
    ${If} ${Errors}
      System::Call 'kernel32::CloseHandle(p $VocabParentHandle)'
      !insertmacro vocabFailure "无法写入更新交接回执，已取消安装。" 25
    ${EndIf}
    FileWrite $R5 "$VocabParentPID"
    FileClose $R5
    Rename "$VocabReadyFile.partial" "$VocabReadyFile"
    ${If} ${Errors}
      Delete "$VocabReadyFile.partial"
      System::Call 'kernel32::CloseHandle(p $VocabParentHandle)'
      !insertmacro vocabFailure "无法完成更新交接回执，已取消安装。" 25
    ${EndIf}
    System::Call 'kernel32::WaitForSingleObject(p $VocabParentHandle, i 30000) i .r0'
    StrCpy $R6 $0
    System::Call 'kernel32::CloseHandle(p $VocabParentHandle)'
    ${If} $R6 != 0
      !insertmacro vocabFailure "旧版未在规定时间内正常退出，安装已取消。请返回旧版重试；不会强制终止程序。" 26
    ${EndIf}
  ${EndIf}
!macroend

!macro customCheckAppRunning
  ; 替换 electron-builder 默认的停止／强杀流程，只检测并拒绝覆盖。
  nsProcess::_FindProcess "${APP_EXECUTABLE_FILENAME}"
  Pop $R0
  ${If} ${isUpdated}
    ; 主进程正常退出后，给其 Electron 子进程少量收尾时间，仍不强制终止。
    StrCpy $VocabProcessRetries 0
    ${DoWhile} $R0 == 0
      ${If} $VocabProcessRetries >= 20
        ${Break}
      ${EndIf}
      Sleep 250
      IntOp $VocabProcessRetries $VocabProcessRetries + 1
      nsProcess::_FindProcess "${APP_EXECUTABLE_FILENAME}"
      Pop $R0
    ${Loop}
  ${EndIf}
  ${If} $R0 == 0
    !insertmacro vocabFailure "数学面试词汇仍在运行，请正常退出后重试。已取消安装，不会强制终止或覆盖运行中的程序。" 27
  ${ElseIf} $R0 != 603
    !insertmacro vocabFailure "无法确认程序是否已经退出，已取消安装。" 28
  ${EndIf}
  ${If} $INSTDIR != "$LOCALAPPDATA\Programs\MathInterviewVocab"
    !insertmacro vocabFailure "安装目录与固定目录不一致，已停止操作以保护其他文件。" 29
  ${EndIf}
!macroend

!ifndef BUILD_UNINSTALLER
Function .onInstFailed
  ; 交接后的磁盘／权限失败必须可见，不能把退出旧版当作安装成功。
  MessageBox MB_OK|MB_ICONSTOP "安装未完成。学习记录和发音设置不会被清除；请检查磁盘空间与目录权限，并从官方 GitHub 下载页重试安装。不要关闭系统安全防护。"
FunctionEnd
!endif
