package com.example.danmuapiapp.data.service

object RootAutoStartScriptBuilders {

    fun buildServiceSh(
        moduleId: String,
        moduleDir: String,
        flagDir: String,
        flagFile: String,
        modeFile: String,
        mainClass: String
    ): String {
        return RootAutoStartServiceScriptPartA.build(
            moduleId = moduleId,
            moduleDir = moduleDir,
            flagDir = flagDir,
            flagFile = flagFile,
            modeFile = modeFile,
            mainClass = mainClass
        )
    }

    fun buildPostFsDataSh(moduleDir: String, flagDir: String): String {
        return RootAutoStartPostFsDataScriptBuilder.build(
            moduleDir = moduleDir,
            flagDir = flagDir
        )
    }
}
