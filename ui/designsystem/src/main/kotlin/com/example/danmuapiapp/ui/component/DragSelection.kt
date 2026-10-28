package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class DragSelectionSession<Key : Any>(
    private val keys: List<Key>,
    private val initialSelection: Set<Key>,
    anchor: Key
) {
    private val anchorIndex = keys.indexOf(anchor).also { require(it >= 0) }
    private val selecting = anchor !in initialSelection

    fun selectionThrough(target: Key): Set<Key> {
        val targetIndex = keys.indexOf(target)
        if (targetIndex < 0) return initialSelection
        val range = keys.subList(minOf(anchorIndex, targetIndex), maxOf(anchorIndex, targetIndex) + 1).toSet()
        return if (selecting) initialSelection + range else initialSelection - range
    }
}

internal data class SelectionItemBounds<Key : Any>(val key: Key, val offset: Int, val size: Int)

internal fun <Key : Any> selectionKeyAt(
    items: List<SelectionItemBounds<Key>>,
    position: Float,
    nearest: Boolean,
    viewportStartOffset: Int = 0
): Key? {
    val contentPosition = position + viewportStartOffset
    items.firstOrNull { contentPosition >= it.offset && contentPosition < it.offset + it.size }?.let { return it.key }
    if (!nearest) return null
    return items.minByOrNull { maxOf(it.offset - contentPosition, contentPosition - (it.offset + it.size), 0f) }?.key
}

internal fun selectionScrollStep(position: Float, height: Int, edge: Float, maxStep: Float): Float {
    if (height <= 0) return 0f
    val threshold = minOf(edge, height / 2f)
    return when {
        position < threshold -> -maxStep * ((threshold - position) / threshold).coerceIn(0f, 1f)
        position > height - threshold -> maxStep * ((position - height + threshold) / threshold).coerceIn(0f, 1f)
        else -> 0f
    }
}

@Composable
fun <Key : Any> Modifier.longPressDragSelection(
    listState: LazyListState,
    keys: List<Key>,
    selectedKeys: Set<Key>,
    enabled: Boolean = true,
    onSelectionChange: (Set<Key>) -> Unit
): Modifier {
    val currentSelection = rememberUpdatedState(selectedKeys)
    val currentOnChange = rememberUpdatedState(onSelectionChange)
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    val maxStep = with(LocalDensity.current) { 16.dp.toPx() }
    return pointerInput(listState, keys, enabled, edge, maxStep) {
        if (!enabled || keys.isEmpty()) return@pointerInput
        val keyByLayoutKey: Map<Any, Key> = keys.associateBy { it }
        coroutineScope {
            var session: DragSelectionSession<Key>? = null
            var scrollJob: Job? = null
            var pointerY = 0f
            var lastTarget: Key? = null

            fun keyAtPointer(nearest: Boolean): Key? {
                val layout = listState.layoutInfo
                val bounds = layout.visibleItemsInfo.mapNotNull { item ->
                    keyByLayoutKey[item.key]?.let { SelectionItemBounds(it, item.offset, item.size) }
                }
                val position = pointerY.coerceIn(0f, (layout.viewportSize.height - 1).coerceAtLeast(0).toFloat())
                return selectionKeyAt(bounds, position, nearest, layout.viewportStartOffset)
            }

            fun updateSelection() {
                val activeSession = session ?: return
                val target = keyAtPointer(nearest = true) ?: return
                if (target != lastTarget) {
                    lastTarget = target
                    currentOnChange.value(activeSession.selectionThrough(target))
                }
            }

            fun stopSelection() {
                scrollJob?.cancel()
                scrollJob = null
                session = null
                lastTarget = null
            }

            try {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        pointerY = position.y
                        val anchor = keyAtPointer(nearest = false)
                        if (anchor != null) {
                            session = DragSelectionSession(keys, currentSelection.value, anchor)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            updateSelection()
                            scrollJob = launch {
                                while (isActive) {
                                    delay(16)
                                    val step = selectionScrollStep(pointerY, listState.layoutInfo.viewportSize.height, edge, maxStep)
                                    if (step != 0f) listState.scrollBy(step)
                                    updateSelection()
                                }
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        if (session != null) {
                            change.consume()
                            pointerY = change.position.y
                            updateSelection()
                        }
                    },
                    onDragEnd = ::stopSelection,
                    onDragCancel = ::stopSelection
                )
            } finally {
                stopSelection()
            }
        }
    }
}
