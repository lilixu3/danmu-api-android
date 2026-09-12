package com.example.danmuapiapp.data.service

/**
 * 只转发给当前 Service 的回调。解绑不结束进程级运行时；旧 Service 已排队的
 * 通知/stopSelf 回调也不能在新 Service 接管后继续执行。
 * [post] 必须投递到 Service 生命周期所在的主线程，测试可注入内存队列。
 */
internal class NodeRuntimeHostRelay<T : Any>(private val post: (() -> Unit) -> Unit) {
    private val lock = Any()
    private var host: T? = null
    private var attachment = 0L

    fun attach(next: T) = synchronized(lock) {
        host = next
        attachment++
    }

    fun detach(previous: T) = synchronized(lock) {
        if (host === previous) {
            host = null
            attachment++
        }
    }

    fun dispatch(action: (T) -> Unit) {
        val target = synchronized(lock) {
            val current = host ?: return
            current to attachment
        }
        post {
            val stillAttached = synchronized(lock) {
                host === target.first && attachment == target.second
            }
            if (stillAttached) action(target.first)
        }
    }
}

/** 同一进程内同一时间只允许一个 node::Start 调用，退出后允许下一次用户重启。 */
internal class NodeRuntimeInvocationGate {
    private val active = java.util.concurrent.atomic.AtomicBoolean(false)

    fun tryClaim(): Boolean = active.compareAndSet(false, true)

    /** 调用权随真实 JNI 线程归还，不随启动协程完成或通知宿主销毁归还。 */
    fun <R> runClaimed(block: () -> R): R {
        check(active.get()) { "Node invocation must be claimed before running" }
        return try {
            block()
        } finally {
            release()
        }
    }

    fun release() {
        active.set(false)
    }

    val isActive: Boolean get() = active.get()
}
