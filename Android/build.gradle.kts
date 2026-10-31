plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
}

// 构建缓存可以放到仓库外，避免网盘临时文件进入 APK。
allprojects {
    providers.environmentVariable("MATH_ANDROID_BUILD_DIR").orNull?.let { external ->
        layout.buildDirectory.set(file("$external/${project.name}"))
    }
}
