package com.example.danmuapiapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.ui.compat.CompatModeActivity

class CompatEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = if (DeviceCompatMode.shouldUseCompatMode(this)) {
            CompatModeActivity::class.java
        } else {
            MainActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
        overridePendingTransition(0, 0)
    }
}
