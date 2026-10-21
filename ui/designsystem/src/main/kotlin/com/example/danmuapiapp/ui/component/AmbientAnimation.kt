package com.example.danmuapiapp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive

/**
 * 常驻装饰动画的生命周期与刷新率治理。
 *
 * 背景：首页的 hero 光球旋转、状态点脉冲这类无限动画会让整页（以及底栏的 backdrop 图层）
 * 持续重绘。这里做两件事，都不改变动画的视觉表现：
 * 1. 只在宿主处于 RESUMED（应用在前台且当前页面可见）时运行，后台/被覆盖时完全停下来；
 * 2. 以 maxFps 上限更新（默认 30fps），把每帧的重绘/合成次数减半。
 *
 * 返回的是 [State]<Float>（0..1 循环），调用方请在绘制阶段读取
 * （例如 `Modifier.graphicsLayer { rotationZ = progress.value * 360f }`），
 * 这样不会触发逐帧重组合。
 */
@Composable
internal fun rememberAmbientLoopState(
    durationMillis: Int,
    maxFps: Int = 30,
    active: Boolean = true
): State<Float> {
    val appResumed = rememberAppResumed()
    val running = active && appResumed
    val state = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(running, durationMillis, maxFps) {
        if (!running) return@LaunchedEffect
        val periodNanos = durationMillis.coerceAtLeast(1).toLong() * 1_000_000L
        val frameIntervalNanos = 1_000_000_000L / maxFps.coerceIn(1, 120)
        var startNanos = 0L
        var lastUpdateNanos = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            if (startNanos == 0L) {
                startNanos = now
                lastUpdateNanos = now
            }
            if (now - lastUpdateNanos < frameIntervalNanos) continue
            lastUpdateNanos = now
            state.floatValue = ((now - startNanos) % periodNanos) / periodNanos.toFloat()
        }
    }
    return state
}

/** 宿主生命周期是否至少处于 RESUMED（应用在前台且当前页面可见）。 */
@Composable
internal fun rememberAppResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}
