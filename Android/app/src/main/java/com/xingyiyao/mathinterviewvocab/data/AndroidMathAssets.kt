package com.xingyiyao.mathinterviewvocab.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.xingyiyao.mathinterviewvocab.core.MathAssetEntry
import com.xingyiyao.mathinterviewvocab.core.MathAssetStore
import com.xingyiyao.mathinterviewvocab.core.MathManifest
import com.xingyiyao.mathinterviewvocab.core.MathSvgDocument
import com.xingyiyao.mathinterviewvocab.core.MathSvgParser
import java.util.concurrent.ConcurrentHashMap

/**
 * 线程安全、纯离线的 Android 公式资源管理器：
 * 从 `assets/math/manifest.json` 与 `assets/math/<id>.svg` 懒加载预渲染矢量资源，
 * 每次首次读取均校验 `svgSHA256` 并缓存已解析的路径对象。
 */
class AndroidMathAssets private constructor(
    private val openAsset: (String) -> ByteArray,
) : MathAssetStore {
    private class CachedDoc(val value: MathSvgDocument?)

    private val loadLock = Any()
    @Volatile
    private var manifestLoaded = false
    @Volatile
    private var entriesByKey: Map<Pair<String, Boolean>, MathAssetEntry> = emptyMap()
    private val docCache = ConcurrentHashMap<String, CachedDoc>()

    private fun ensureLoaded() {
        if (manifestLoaded) return
        synchronized(loadLock) {
            if (manifestLoaded) return
            entriesByKey = try {
                val json = openAsset("math/manifest.json").toString(Charsets.UTF_8)
                val manifest = MathManifest.parseAndValidate(json)
                manifest.entries.associateBy { it.tex to it.display }
            } catch (_: Exception) {
                emptyMap()
            }
            manifestLoaded = true
        }
    }

    override fun entry(latex: String, display: Boolean): MathAssetEntry? {
        ensureLoaded()
        return entriesByKey[latex to display]
    }

    override fun document(entry: MathAssetEntry): MathSvgDocument? {
        docCache[entry.id]?.let { return it.value }
        val parsed = try {
            val bytes = openAsset("math/${entry.svg}")
            if (MathManifest.sha256Hex(bytes) == entry.svgSHA256) {
                MathSvgParser.parse(bytes.toString(Charsets.UTF_8))
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
        docCache.putIfAbsent(entry.id, CachedDoc(parsed))
        return parsed
    }

    companion object {
        @Volatile
        private var instance: AndroidMathAssets? = null

        fun get(context: Context): AndroidMathAssets = instance ?: synchronized(this) {
            instance ?: fromAssetManager(context.applicationContext.assets).also { instance = it }
        }

        fun fromAssetManager(assets: android.content.res.AssetManager): AndroidMathAssets =
            AndroidMathAssets { path -> assets.open(path).use { it.readBytes() } }

        internal fun fromBytesLoader(loader: (String) -> ByteArray): AndroidMathAssets =
            AndroidMathAssets(loader)
    }
}

val LocalMathAssets = staticCompositionLocalOf<MathAssetStore?> { null }

@Composable
fun rememberMathAssets(): MathAssetStore {
    val injected = LocalMathAssets.current
    if (injected != null) return injected
    val context = LocalContext.current
    return remember(context.applicationContext) { AndroidMathAssets.get(context) }
}
