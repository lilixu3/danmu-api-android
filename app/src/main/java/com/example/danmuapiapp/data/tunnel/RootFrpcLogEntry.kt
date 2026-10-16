package com.example.danmuapiapp.data.tunnel

import android.system.Os
import org.json.JSONObject
import java.io.File

/** Standalone app_process sink; no native network helper and no periodic wakeups. */
object RootFrpcLogEntry {
    @JvmStatic fun main(args: Array<String>) {
        try { run(args) }
        finally { android.os.Process.killProcess(android.os.Process.myPid()) }
    }

    private fun run(args: Array<String>) {
        val options = args.toList().chunked(2).associate { require(it.size == 2); it[0] to it[1] }
        val log = File(requireNotNull(options["--bounded-log"]))
        val snapshot = options["--frpc-link-state"]?.let(::File)
        val instance = options["--frpc-instance"].orEmpty()
        require(snapshot == null || (instance.isNotBlank() && instance.length <= 128 && !instance.contains('\n') && !instance.contains('\r')))
        fun publish(state: TunnelLinkState) {
            if (snapshot == null) return
            val pending = File.createTempFile(".frpc-link-", ".pending", snapshot.parentFile)
            try {
                pending.writeText(JSONObject().put("instanceId", instance).put("state", state.name).put("updatedAt", System.currentTimeMillis()).toString())
                val owner = Os.stat(snapshot.parentFile!!.absolutePath)
                Os.chmod(pending.absolutePath, 0x180) // 0600
                if (Os.geteuid() == 0) Os.chown(pending.absolutePath, owner.st_uid, owner.st_gid)
                check(pending.renameTo(snapshot)) { "Cannot publish FRP snapshot" }
            } finally { pending.delete() }
        }
        publish(TunnelLinkState.Unknown)
        val observer = FrpcLinkObserver(::publish)
        if (!log.exists()) log.createNewFile()
        Os.chmod(log.absolutePath, 0x180)
        val owner = Os.stat(log.parentFile!!.absolutePath)
        if (Os.geteuid() == 0) Os.chown(log.absolutePath, owner.st_uid, owner.st_gid)
        pumpFrpcBoundedLog(System.`in`, log, observer::accept)
    }
}
