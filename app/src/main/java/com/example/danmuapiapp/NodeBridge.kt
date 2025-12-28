package com.example.danmuapiapp

object NodeBridge {
    init {
        // Order matters: our JNI lib first, then libnode.so
        System.loadLibrary("native-lib")
        System.loadLibrary("node")
    }

    external fun startNodeWithArguments(args: Array<String>): Int
}
