# 桌面 1.5.0 · 多线路更新、断点续传与后台进度

macOS **1.5.0 build 7**、Windows **1.5.0（file build 1.5.0.7）**。本次不修改 Android，保留 Android 独立 Latest。

## 更新内容

- 三种更新线路：默认“智能多线加速”、镜像优先、GitHub 直连。官方请求超时或失败时择优使用备用节点，最多同时探测三条线路；大安装包始终单线路下载。两家 jsDelivr 节点只读取小型索引，不分发安装包。
- 下载中断或持续低速时切换线路，使用经过严格校验的 HTTP Range 续传；不支持续传的响应会安全重新下载，不拼接错误内容。
- 增加暂停、继续和单独确认的清除缓存。暂停、断网和退出均保留可恢复断点；下次打开只读取本地缓存，由你手动继续，不自动联网或常驻后台。
- 词汇、检测、学习记录顶部增加轻量进度条，显示百分比、已下载／总大小、速度和当前线路；关闭设置或切页不停止下载。
- 两平台新增独立签名更新索引，固定平台、版本、构建号、附件大小及 SHA-256；镜像不能改变安装包身份。下载完成重新核对完整摘要，安装前继续执行既有签名校验和学习记录保存门禁。

Mac 保留 Sparkle 原生更新窗口及确认、替换和重启流程；新下载层通过仅绑定本机回环地址的单会话只读交接层提供已绑定的更新源与归档，不暴露目录或通用代理。Windows 保留应用内更新界面及 NSIS 安装器，不强制结束其他进程。

## 下载与升级

- [Windows 安装版（推荐）](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.0/MathInterviewVocab-Windows-x64-Setup-v1.5.0.exe)
- [Windows 便携 ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.0/MathInterviewVocab-Windows-x64-v1.5.0.zip)
- [macOS Universal ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.0/MathInterviewVocab-macOS-universal-v1.5.0.zip)
- [SHA256SUMS.txt](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.0/SHA256SUMS.txt)

1.4.0 可以使用原有更新流程升级至 1.5.0，新的加速和持久断点功能从安装 1.5.0 后生效。1.3.0 及更早桌面版本仍需手动安装一次。Windows 便携版通过应用内更新转入固定目录安装版，不自动清理旧解压目录。

326 条词汇、130 条重点、2328 段 Kokoro 离线音频、快捷键、学习数据格式及位置不变。更新缓存与学习记录分离，清除下载缓存不会重置记录。

## 隐私与安全

智能或镜像模式会向第三方节点发送公开更新资源请求，可以选择 GitHub 直连以完全禁用镜像和 CDN；不发送学习记录或朗读文本。没有启动自动检查、静默安装或退出后下载服务。

Mac 仍为 ad-hoc 签名、未 Apple 公证；Windows 未商业代码签名。更新签名不是操作系统的发布者认证，不关闭 Gatekeeper、SmartScreen 或其他系统防护，不手动移除隔离标记。

验证范围与限制见 [发布检查](RELEASE-CHECKS.md)，缓存位置及维护流程见 [桌面更新说明](DESKTOP-UPDATES.md)。Windows 原生安装验收使用 GitHub 官方托管 runner，不是实体设备测试，不使用 PD。
