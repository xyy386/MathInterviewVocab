#!/bin/sh
set -eu

# 只影响这次子进程，不修改系统 Java、shell 配置或 Android Studio 设置。
tool_cache="${MATH_ANDROID_TOOLS_DIR:-$HOME/Library/Caches/MathInterviewVocab-Android}"
task_java="${MATH_ANDROID_JAVA_HOME:-$tool_cache/jdk-17.0.21+9/Contents/Home}"
task_sdk="${MATH_ANDROID_SDK_ROOT:-$tool_cache/sdk}"
if [ ! -x "$task_java/bin/java" ]; then
    echo '请设置 MATH_ANDROID_JAVA_HOME 指向独立的 JDK 17。' >&2
    exit 1
fi
export JAVA_HOME="$task_java"
export ANDROID_HOME="$task_sdk"
export ANDROID_SDK_ROOT="$task_sdk"
export GRADLE_USER_HOME="${MATH_ANDROID_GRADLE_HOME:-$tool_cache/gradle-home}"
export MATH_ANDROID_BUILD_DIR="${MATH_ANDROID_BUILD_DIR:-$tool_cache/build}"
export PATH="$JAVA_HOME/bin:$tool_cache/bin:$ANDROID_HOME/platform-tools:$PATH"
exec "$@"
