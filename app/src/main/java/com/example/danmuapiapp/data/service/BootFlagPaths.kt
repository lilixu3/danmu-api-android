package com.example.danmuapiapp.data.service

/**
 * 开机触发相关共享标记路径。
 */
object BootFlagPaths {
    const val FLAG_DIR = "/data/adb/danmuapi_boot_autostart"
    const val MODE_FILE = "$FLAG_DIR/mode"
    const val ROOT_MODULE_FLAG_FILE = "$FLAG_DIR/enabled"
}
