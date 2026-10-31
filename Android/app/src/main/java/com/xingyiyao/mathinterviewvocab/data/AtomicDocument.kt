package com.xingyiyao.mathinterviewvocab.data

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.util.UUID

/** 文件操作边界让迁移、损坏保护和失败回滚能在 JVM 上测试。 */
internal interface AtomicDocument {
    fun exists(): Boolean
    fun read(): String
    fun write(source: String)
    fun backup(): File
}

internal class AndroidAtomicDocument(private val file: File) : AtomicDocument {
    private val atomic = AtomicFile(file)

    override fun exists(): Boolean = file.exists() || File(file.path + ".bak").exists()

    override fun read(): String = atomic.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }

    override fun write(source: String) {
        ensureDirectory()
        val stream = atomic.startWrite()
        try {
            stream.write(source.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
        // AtomicFile 某些平台版本只记录 rename 失败，读回确认后才提交内存状态。
        if (read() != source) throw IOException("本地文件原子替换未完成。")
    }

    override fun backup(): File {
        ensureDirectory()
        val backup = File(file.parentFile, "${file.name}.backup-${UUID.randomUUID()}")
        // CREATE_NEW 语义，恢复操作绝不覆盖已有备份。
        if (!backup.createNewFile()) throw IOException("无法建立原文件备份。")
        try {
            atomic.openRead().use { input ->
                backup.outputStream().use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
        } catch (failure: Exception) {
            backup.delete()
            throw failure
        }
        return backup
    }

    private fun ensureDirectory() {
        val directory = file.parentFile ?: throw IOException("本地存储目录无效。")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建本地存储目录。")
    }
}
