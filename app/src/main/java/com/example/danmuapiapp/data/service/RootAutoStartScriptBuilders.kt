package com.example.danmuapiapp.data.service

object RootAutoStartScriptBuilders {

    fun buildServiceSh(
        moduleId: String,
        moduleDir: String,
        flagDir: String,
        flagFile: String,
        modeFile: String,
        mainClass: String,
        frpDir: String = "",
        nativeLibDir: String = "",
        packageName: String = "",
        outboundConfigPath: String = ""
    ): String {
        return RootAutoStartServiceScriptPartA.build(
            moduleId = moduleId,
            moduleDir = moduleDir,
            flagDir = flagDir,
            flagFile = flagFile,
            modeFile = modeFile,
            mainClass = mainClass,
            frpDir = frpDir,
            nativeLibDir = nativeLibDir,
            packageName = packageName,
            outboundConfigPath = outboundConfigPath
        )
    }

    fun buildPostFsDataSh(moduleDir: String, flagDir: String): String {
        return RootAutoStartPostFsDataScriptBuilder.build(
            moduleDir = moduleDir,
            flagDir = flagDir
        )
    }
}
