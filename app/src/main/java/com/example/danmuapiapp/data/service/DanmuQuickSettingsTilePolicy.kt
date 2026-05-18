package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus

object DanmuQuickSettingsTilePolicy {

    enum class VisualState {
        Active,
        Inactive,
        Unavailable
    }

    enum class ClickAction {
        Start,
        Stop,
        Ignore
    }

    data class Presentation(
        val visualState: VisualState,
        val label: String,
        val subtitle: String
    )

    fun presentation(
        status: ServiceStatus,
        runMode: RunMode,
        port: Int
    ): Presentation {
        return when (status) {
            ServiceStatus.Running -> Presentation(
                visualState = VisualState.Active,
                label = TILE_LABEL,
                subtitle = "运行中"
            )

            ServiceStatus.Starting -> Presentation(
                visualState = VisualState.Unavailable,
                label = TILE_LABEL,
                subtitle = "启动中"
            )

            ServiceStatus.Stopping -> Presentation(
                visualState = VisualState.Unavailable,
                label = TILE_LABEL,
                subtitle = "停止中"
            )

            ServiceStatus.Error -> Presentation(
                visualState = VisualState.Inactive,
                label = TILE_LABEL,
                subtitle = "失败，点按重试"
            )

            ServiceStatus.Stopped -> Presentation(
                visualState = VisualState.Inactive,
                label = TILE_LABEL,
                subtitle = "未启动"
            )
        }
    }

    fun clickAction(status: ServiceStatus): ClickAction {
        return when (status) {
            ServiceStatus.Running -> ClickAction.Stop
            ServiceStatus.Stopped,
            ServiceStatus.Error -> ClickAction.Start
            ServiceStatus.Starting,
            ServiceStatus.Stopping -> ClickAction.Ignore
        }
    }

    private const val TILE_LABEL = "弹幕API"
}
