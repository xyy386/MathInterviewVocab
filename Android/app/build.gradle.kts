import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val repositoryRoot = rootProject.projectDir.parentFile
val signingFile = providers.environmentVariable("MATH_ANDROID_SIGNING_PROPERTIES").orNull?.let(::file)
val releaseSigning = Properties().apply {
    signingFile?.inputStream()?.use { load(it) }
}
fun signingPassword(name: String): String? = releaseSigning.getProperty("${name}File")?.let {
    file(it).readText().trim()
} ?: releaseSigning.getProperty(name)
val releaseStorePassword = signingPassword("storePassword")
val releaseKeyPassword = signingPassword("keyPassword")

android {
    namespace = "com.xingyiyao.mathinterviewvocab"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.xingyiyao.mathinterviewvocab"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "1.3.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (signingFile != null) create("official") {
            storeFile = file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseStorePassword
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        release {
            // 首发保留可审计代码；不通过混淆改变数据序列化契约。
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("official")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
    androidResources { noCompress += "mp3" }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("math.repositoryRoot", repositoryRoot.absolutePath)
            it.systemProperty("math.androidRoot", rootProject.projectDir.absolutePath)
        }
    }
    lint { abortOnError = true; checkReleaseBuilds = true; disable += "ObsoleteSdkInt" }
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }) {
        require(signingFile?.isFile == true && listOf("storeFile", "keyAlias")
            .all { !releaseSigning.getProperty(it).isNullOrBlank() } &&
            !releaseStorePassword.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()) {
            "正式构建需要仓库外的 MATH_ANDROID_SIGNING_PROPERTIES；禁止用调试密钥冒充正式版。"
        }
    }
}

// 三个平台共用规范词库、音源与公式矢量资源，不复制维护第二份源文件。
val manifestFile = File(repositoryRoot, "Windows/resources/audio-manifest.json")
val audioDirectory = File(repositoryRoot, "Windows/resources/audio")
val termsFile = File(repositoryRoot, "Resources/terms.json")
val mathDirectory = File(repositoryRoot, "Resources/Math")
val mathManifestFile = File(mathDirectory, "manifest.json")
val generatedAssets = layout.buildDirectory.dir("generated/vocabularyAssets")
val verifyVocabularyAssets by tasks.registering {
    inputs.file(manifestFile)
    inputs.file(termsFile)
    inputs.file(mathManifestFile)
    inputs.files(fileTree(audioDirectory) { include("*.mp3"); exclude(".*") })
    inputs.files(fileTree(mathDirectory) { include("*.svg"); exclude(".*") })
    inputs.files(File(repositoryRoot, "LICENSE"),
        rootProject.file("THIRD-PARTY-NOTICES.md"),
        File(repositoryRoot, "Windows/resources/KOKORO-APACHE-2.0.txt"),
        File(repositoryRoot, "Windows/resources/AUDIO-SOURCES.md"),
        File(mathDirectory, "SOURCES.md"),
        File(mathDirectory, "MathJax-LICENSE.txt"),
        File(mathDirectory, "GUST-FONT-LICENSE.txt"),
        File(mathDirectory, "LPPL-1.3c.txt"),
        File(mathDirectory, "NewComputerModern-License.txt"))
    doLast {
        val parser = JsonSlurper()
        val terms = parser.parse(termsFile) as List<*>
        require(terms.size == 326 && terms.count { (it as Map<*, *>)["important"] == true } == 130)
        val manifest = parser.parse(manifestFile) as Map<*, *>
        val coverage = manifest["coverage"] as Map<*, *>
        require(coverage["complete"] == true && (coverage["missingEntries"] as Number).toInt() == 0 &&
            (coverage["failures"] as List<*>).isEmpty()) { "离线音频覆盖检查失败。" }
        val entries = manifest["entries"] as Map<*, *>
        require(entries.size == 2328)
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        entries.forEach { (rawKey, rawEntry) ->
            val key = rawKey as String
            val entry = rawEntry as Map<*, *>
            require(key.matches(Regex("[0-9a-f]{64}")) && entry["file"] == "$key.mp3")
            require(entry["accent"] in setOf("en-US", "en-GB"))
            require(hash("${entry["accent"]}\n${entry["text"]}".toByteArray(Charsets.UTF_8)) == key)
            val audio = File(audioDirectory, "$key.mp3")
            require(audio.isFile && audio.length() == (entry["bytes"] as Number).toLong()) { "音频大小错误：$key" }
            require(hash(audio.readBytes()) == entry["sha256"]) { "音频校验失败：$key" }
        }
        require(audioDirectory.listFiles { file -> file.isFile && file.extension == "mp3" && !file.name.startsWith(".") }?.size == 2328)

        val termsHash = hash(termsFile.readBytes())
        val mathManifest = parser.parse(mathManifestFile) as Map<*, *>
        require((mathManifest["schema"] as Number).toInt() == 1 && mathManifest["engine"] == "MathJax 4.1.3") { "公式清单版本或引擎错误。" }
        require(mathManifest["termsSHA256"] == termsHash) { "公式清单与当前词库摘要不一致。" }
        val mathEntries = mathManifest["entries"] as List<*>
        require(mathEntries.size == 63) { "公式数量错误：${mathEntries.size}" }
        mathEntries.forEach { rawEntry ->
            val entry = rawEntry as Map<*, *>
            val id = entry["id"] as String
            require(id.matches(Regex("[0-9a-f]{64}")) && entry["svg"] == "$id.svg") { "公式条目异常：$id" }
            val svgFile = File(mathDirectory, "$id.svg")
            require(svgFile.isFile && hash(svgFile.readBytes()) == entry["svgSHA256"]) { "公式 SVG 校验失败：$id" }
        }
        require(mathDirectory.listFiles { file -> file.isFile && file.extension == "svg" && !file.name.startsWith(".") }?.size == 63) { "公式 SVG 目录包含多余或缺失文件。" }
        logger.lifecycle("已验证 326 条词汇、130 条重点、2328 段 Kokoro 音频及 63 个离线矢量公式 SVG（大小/SHA-256）。")
    }
}
val syncVocabularyAssets by tasks.registering(Sync::class) {
    dependsOn(verifyVocabularyAssets)
    into(generatedAssets)
    from(termsFile)
    from(manifestFile)
    from(audioDirectory) { into("audio"); include("*.mp3"); exclude(".*") }
    from(mathDirectory) { into("math"); include("manifest.json", "*.svg") }
    from(File(repositoryRoot, "LICENSE")) { into("licenses"); rename { "PROJECT-MIT.txt" } }
    from(rootProject.file("THIRD-PARTY-NOTICES.md")) { into("licenses") }
    from(File(repositoryRoot, "Windows/resources/AUDIO-SOURCES.md")) { into("licenses") }
    from(File(repositoryRoot, "Windows/resources/KOKORO-APACHE-2.0.txt")) { into("licenses") }
    from(File(mathDirectory, "SOURCES.md")) { into("licenses"); rename { "MATH-SOURCES.md" } }
    from(File(mathDirectory, "MathJax-LICENSE.txt")) { into("licenses") }
    from(File(mathDirectory, "GUST-FONT-LICENSE.txt")) { into("licenses") }
    from(File(mathDirectory, "LPPL-1.3c.txt")) { into("licenses") }
    from(File(mathDirectory, "NewComputerModern-License.txt")) { into("licenses") }
}
android.sourceSets["main"].assets.srcDir(generatedAssets)
tasks.named("preBuild").configure { dependsOn(syncVocabularyAssets) }

dependencyLocking { lockAllConfigurations() }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
