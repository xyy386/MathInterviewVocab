plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
}
kotlin { jvmToolchain(17) }
providers.environmentVariable("MATH_ANDROID_BUILD_DIR").orNull?.let {
    layout.buildDirectory.set(file("$it/logic-tests"))
}
// 编译应用本身的纯逻辑源码，不另造一个仅供测试的实现。
sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.include("**/core/**", "**/audio/AudioInventory.kt", "**/ui/LayoutPolicy.kt", "**/updates/UpdatePolicy.kt",
            "**/updates/UpdateTransport.kt", "**/updates/UpdateEngine.kt")
    }
    test {
        kotlin.srcDir("../app/src/test/java")
        kotlin.include("**/core/**", "**/audio/AudioInventoryTest.kt", "**/ui/LayoutPolicyTest.kt", "**/updates/UpdatePolicyTest.kt",
            "**/updates/UpdateEngineTest.kt")
    }
}
tasks.test {
    systemProperty("math.repositoryRoot", rootDir.parentFile.parentFile.absolutePath)
    testLogging { events("failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
dependencyLocking { lockAllConfigurations() }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
