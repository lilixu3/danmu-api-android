package com.example.danmuapiapp.desktop.runtime

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/**
 * 应用单实例锁：对锁文件持有 JVM 级文件锁，第二个实例 tryLock 失败即退出。
 * Android 端靠单进程 + generation 天然单实例；桌面端必须显式加锁，
 * 否则多实例会互相抢端口、托盘与服务状态全部失真。
 */
object AppInstanceLock {

    private var channel: RandomAccessFile? = null
    private var lock: FileLock? = null

    /** 尝试持有锁；返回 false 表示已有实例在运行。 */
    fun tryAcquire(lockFile: File): Boolean {
        lockFile.parentFile?.mkdirs()
        val raf = RandomAccessFile(lockFile, "rw")
        val fileLock = runCatching { raf.channel.tryLock() }.getOrNull()
        if (fileLock == null) {
            runCatching { raf.close() }
            return false
        }
        channel = raf
        lock = fileLock
        return true
    }

    fun release() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }
}
