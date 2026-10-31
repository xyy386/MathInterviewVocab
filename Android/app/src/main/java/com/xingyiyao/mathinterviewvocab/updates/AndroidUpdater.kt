package com.xingyiyao.mathinterviewvocab.updates

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.xingyiyao.mathinterviewvocab.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 应用级单实例；初始化、前后台切换均不自动检查网络版本。 */
class AndroidUpdater(context: Context) {
    private val app = context.applicationContext
    private val manager = app.packageManager
    private val installer = manager.packageInstaller
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = app.getSharedPreferences("update-install-session", Context.MODE_PRIVATE)
    private val installationActive = AtomicBoolean(false)
    private val restoredSession = AtomicBoolean(false)
    @Volatile private var confirmation: Intent? = null
    private val engine = UpdateEngine(
        currentVersion = runCatching { installedIdentity().versionName }.getOrDefault(BuildConfig.VERSION_NAME),
        directory = File(app.filesDir, "updates"),
        installed = ::installedIdentity,
        inspector = ApkInspector(::archiveIdentity),
    )
    val state: StateFlow<UpdateUiState> = engine.state

    init {
        if (preferences.getInt(SESSION_ID, -1) >= 0) {
            installationActive.set(true)
            restoredSession.set(true)
            engine.updateInstallState { it.copy(status = "installing", message = "已有系统安装会话，等待系统结果；不会自动下载安装包。") }
        }
    }

    fun check() { if (!installationActive.get()) engine.check() }
    fun download() { if (!installationActive.get()) engine.download() }
    fun cancel() = engine.cancel()

    fun openReleaseNotes(activity: Activity) {
        val url = engine.releasePage() ?: "${UpdatePolicy.REPOSITORY}/releases/latest"
        if (url != "${UpdatePolicy.REPOSITORY}/releases/latest" &&
            !url.matches(Regex("${Regex.escape(UpdatePolicy.REPOSITORY)}/releases/tag/v[0-9]+\\.[0-9]+\\.[0-9]+"))) return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: Exception) { fail("无法打开浏览器，请稍后重试。", retryInstall = state.value.canInstall) }
    }

    fun requestInstall(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!state.value.canInstall || installationActive.get()) return
        if (!manager.canRequestPackageInstalls()) {
            engine.updateInstallState { it.copy(needsInstallPermission = true, message = "请亲自在系统设置中允许此应用安装更新，然后返回并再次点击安装。") }
            return
        }
        if (!installationActive.compareAndSet(false, true)) return
        engine.updateInstallState { it.copy(status = "installing", canInstall = false, needsInstallPermission = false, message = "正在准备系统安装会话…") }
        scope.launch {
            var createdSession: Int? = null
            var committed = false
            try {
                val update = engine.verifyForInstall()
                val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(update.file.length())
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                val sessionId = installer.createSession(parameters)
                createdSession = sessionId
                val nonce = UUID.randomUUID().toString()
                if (!preferences.edit().putInt(SESSION_ID, sessionId).putString(SESSION_NONCE, nonce).commit()) {
                    throw UpdateFailure("无法保存安装会话，已取消安装。")
                }
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, update.file.length()).use { output ->
                        update.file.inputStream().use { input ->
                            copyVerifiedArchive(input, output, update.release.apk.bytes, update.sha256)
                        }
                        session.fsync(output)
                    }
                    val resultIntent = Intent(app, InstallStatusReceiver::class.java)
                        .setAction("${app.packageName}.INSTALL_RESULT.$nonce")
                        .putExtra(OWN_SESSION_ID, sessionId)
                        .putExtra(SESSION_NONCE, nonce)
                    // PackageInstaller 必须填入状态；显式、唯一、不可导出的接收器限制 mutable 范围。
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    val callback = PendingIntent.getBroadcast(app, sessionId, resultIntent, flags)
                    session.commit(callback.intentSender)
                    committed = true
                }
                engine.updateInstallState { it.copy(message = "安装会话已提交，等待系统确认。不会静默安装。") }
            } catch (failure: Exception) {
                if (!committed) {
                    createdSession?.let { runCatching { installer.abandonSession(it) } }
                    clearSession()
                }
                fail((failure as? UpdateFailure)?.message ?: "无法准备系统安装。原有应用和学习记录未被修改。", retryInstall = engine.verifiedUpdate() != null)
            }
        }
    }

    fun openInstallPermission(activity: Activity) {
        if (!state.value.needsInstallPermission || installationActive.get()) return
        try {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")))
        } catch (_: Exception) { fail("无法打开安装来源设置，请通过系统设置为本应用授权。", retryInstall = true) }
    }

    fun continueInstall(activity: Activity) {
        val pending = confirmation ?: return
        if (!state.value.needsUserConfirmation || !installationActive.get()) return
        try {
            val target = manager.resolveActivity(pending, PackageManager.MATCH_DEFAULT_ONLY)
                ?: throw UpdateFailure("系统安装确认页面不可用。")
            val flags = target.activityInfo.applicationInfo.flags
            if (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                throw UpdateFailure("安装确认目标不是系统组件，已拒绝打开。")
            }
            val explicit = Intent(pending).setComponent(ComponentName(target.activityInfo.packageName, target.activityInfo.name))
            activity.startActivity(explicit)
            confirmation = null
            engine.updateInstallState { it.copy(needsUserConfirmation = false, message = "请在系统安装页面确认；拒绝或取消不会更改学习记录。") }
        } catch (failure: Exception) {
            abandonOwnSession()
            fail((failure as? UpdateFailure)?.message ?: "无法打开系统安装确认页面。", retryInstall = engine.verifiedUpdate() != null)
        }
    }

    fun onAppForeground() {
        if (state.value.needsInstallPermission && manager.canRequestPackageInstalls()) {
            engine.updateInstallState { it.copy(needsInstallPermission = false, message = "安装来源已允许。请再次点击安装更新，以打开系统确认。") }
        }
        if (installationActive.get() && confirmation == null) {
            if (restoredSession.compareAndSet(true, false)) {
                // 进程重建后不持久化系统 Intent；安全取消自己的旧会话，允许重新操作。
                abandonOwnSession()
                fail("上次安装的系统确认已失效，已安全取消旧会话。请重新检查并下载更新，学习记录不受影响。", retryInstall = false)
                return
            }
            val id = preferences.getInt(SESSION_ID, -1)
            val present = runCatching { id >= 0 && installer.getSessionInfo(id) != null }.getOrDefault(false)
            if (!present) {
                clearSession()
                // 会话消失不能等同于成功；成功只能来自系统回调。
                fail("安装会话已结束，未收到成功确认。请检查当前版本；可重新检查更新。", retryInstall = engine.verifiedUpdate() != null)
            }
        }
    }

    fun onAppBackground() { engine.cancel() }

    internal fun handleInstallResult(intent: Intent) {
        val expectedId = preferences.getInt(SESSION_ID, -1)
        val nonce = preferences.getString(SESSION_NONCE, null) ?: return
        if (expectedId < 0 || intent.action != "${app.packageName}.INSTALL_RESULT.$nonce" ||
            intent.getStringExtra(SESSION_NONCE) != nonce || intent.getIntExtra(OWN_SESSION_ID, -1) != expectedId ||
            intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != expectedId) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val pending: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (pending == null) {
                    abandonOwnSession()
                    fail("系统没有提供安装确认页面。", retryInstall = engine.verifiedUpdate() != null)
                    return
                }
                confirmation = pending
                restoredSession.set(false)
                engine.updateInstallState { it.copy(status = "installing", message = "系统需要你确认安装，请点击“继续系统安装”。", needsUserConfirmation = true, canInstall = false) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                clearSession()
                engine.updateInstallState { it.copy(status = "current", message = "系统已确认安装成功，学习记录由同包名覆盖更新保留。", canInstall = false, canDownload = false, needsUserConfirmation = false) }
            }
            else -> {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                clearSession()
                fail(if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "安装已拒绝或取消。原有应用和学习记录保持不变。" else "系统报告安装失败（状态 $status）。原有应用和学习记录保持不变。", retryInstall = engine.verifiedUpdate() != null)
            }
        }
    }

    private fun clearSession() {
        preferences.edit().remove(SESSION_ID).remove(SESSION_NONCE).commit()
        installationActive.set(false)
        restoredSession.set(false)
        confirmation = null
    }

    private fun abandonOwnSession() {
        val id = preferences.getInt(SESSION_ID, -1)
        if (id >= 0) runCatching { installer.abandonSession(id) }
        clearSession()
    }

    private fun fail(message: String, retryInstall: Boolean) {
        engine.updateInstallState { it.copy(status = "error", message = message, canInstall = retryInstall, needsUserConfirmation = false) }
    }

    private fun installedIdentity(): ApkIdentity {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            manager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        return info.identity()
    }

    private fun archiveIdentity(file: File): ApkIdentity {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            manager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } ?: throw UpdateFailure("无法解析 Android 安装包，已拒绝安装。")
        return info.identity()
    }

    private fun PackageInfo.identity(): ApkIdentity {
        val signers = signingInfo?.apkContentsSigners ?: throw UpdateFailure("无法读取安装包的当前签名证书。")
        val certificates = signers.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()
        if (certificates.isEmpty()) throw UpdateFailure("安装包缺少签名证书。")
        return ApkIdentity(
            packageName.takeIf { it.isNotBlank() } ?: throw UpdateFailure("安装包缺少应用标识。"),
            versionName ?: throw UpdateFailure("安装包缺少版本名。"),
            longVersionCode,
            certificates,
        )
    }

    private companion object {
        const val SESSION_ID = "sessionId"
        const val SESSION_NONCE = "sessionNonce"
        const val OWN_SESSION_ID = "mathSessionId"
    }
}
