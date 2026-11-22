# 数学面试词汇 v1.3.5 · Android 多线路更新加速、断点续传与后台进度优化

本次更新将 Android 原生版升级至 **1.3.5（versionCode 7）**，专门解决部分网络环境下访问 GitHub 延迟高、易断流、后台下载安装包极慢的问题；Windows 1.4.0 与 macOS 1.4.0（build 6）保持不变，桌面用户继续使用 [v1.4.0 发布页](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.4.0) 的桌面安装包。

## Android 1.3.5（versionCode 7）更新内容

1. **多线路对冲竞速检查与镜像回退（Hedged Racing）**：检查版本更新时优先探测 GitHub 官方接口；若直连受阻或在 1.2 秒内未响应，自动并发竞速 `gh-proxy.com`、`ghfast.top`、`ghproxy.net` 镜像及 `testingcf.jsdelivr.net` / `fastly.jsdelivr.net` 静态版本索引，取最快通过校验的响应并自动取消其余慢速请求。
2. **HTTP Range 跨线路断点续传**：下载 100 MB 安装包时若某条线路中途断流，自动切换下一条可用加速镜像并携带 `Range: bytes=<offset>-`（`206 Partial Content` + `Content-Range` 校验）从断点继续下载，不再从零重传，最终仍强制校验完整文件的字节大小、SHA-256 与官方签名证书。
3. **低速自动切换线路 + 缩短死连超时**：将连接/读取超时从 15s/30s 缩短至 6s/12s 以快速跳过黑洞节点；若某条线路连续 8 秒平均速率低于 `40 KB/s` 且仍有备用加速线路，自动断开当前慢连接并跨线路续传。
4. **设置页线路切换与实时网速展示**：在 **设置 → 软件更新** 新增“更新线路”切换胶囊（**智能多线加速** / **加速镜像优先** / **GitHub 直连**），实时显示当前生效线路名称、下载百分比与实时下载速率（`KB/s` / `MB/s`）。
5. **主界面非侵入式后台更新进度条**：点击下载更新后切换回词表、检测或学习记录页继续学习时，顶部状态栏会显示轻量级后台下载进度条（百分比、实时网速、当前线路与“查看 / 立即安装”快捷操作）。
6. **严格白名单安全边界**：扩展允许的加速镜像与 CDN 域名均严格限定只代理或分发本仓库 `xyy386/MathInterviewVocab` 路径下的发布元数据与附件，且安装前仍必须通过官方发布证书签名、SHA-256 摘要、包名与递增 `versionCode` 四重核验。

## 下载与升级

- **Android Universal APK**：[MathInterviewVocab-Android-universal-v1.3.5.apk](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.3.5/MathInterviewVocab-Android-universal-v1.3.5.apk)（`100,616,960` 字节，SHA-256：`ad5355e53ffbd8a900f30cd9b3f80b22d149af8602af1eb72e8e1507521b3cf8`）
- **Android 独立校验文件**：[SHA256SUMS-Android.txt](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.3.5/SHA256SUMS-Android.txt)
- **公开签名证书 SHA-256**：`24e92a08b1fe647bb6d1726a6208ad75436c1ac2a391651d58c68c067dce1069`

已安装旧版（`1.2.0`–`1.3.4`）的 Android 设备可在应用内打开 **设置 → 软件更新 → 检查更新** 升级至 `1.3.5`（`versionCode 7`），或从上方链接下载 APK 直接覆盖安装，本机学习记录自动保留。
