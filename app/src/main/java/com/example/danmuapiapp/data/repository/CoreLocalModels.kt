package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.RunMode
import java.io.File

internal data class CoreLocation(
    val mode: RunMode,
    val normalDir: File,
    val rootDirPath: String
)

internal data class LoadedCoreState(
    val info: CoreInfo,
    val localMetadata: CoreSourceMetadata?
)

internal enum class PendingCoreMutationType {
    ReplaceCore,
    RepairInstalledDependencies
}

internal data class PendingCoreMutation(
    val operationId: Long,
    val repair: CoreDependencyRepairRequest,
    val stagingDir: File,
    val targetDir: File,
    val mode: RunMode,
    val rootDirPath: String,
    val sourceMetadata: CoreSourceMetadata?,
    val type: PendingCoreMutationType = PendingCoreMutationType.ReplaceCore
)
