package com.yateeshpriv.applockpriv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * A 3×3 pattern lock grid that captures swipe gestures using Compose [Canvas].
 *
 * The user drags a finger across the dot grid; any dot whose hit area is crossed
 * gets added to the sequence with haptic feedback. Lines are drawn between connected
 * dots with an outer glow in real-time.
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    resetKey: Any = Unit,
    onPatternComplete: (List<Int>) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var selectedNodes by remember { mutableStateOf(emptyList<Int>()) }
    var dragOffset by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val activeColor = if (isError) errorColor else primaryColor

    LaunchedEffect(resetKey) {
        selectedNodes = emptyList()
        dragOffset = null
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize) {
                val positions = nodePositions(canvasSize.width.toFloat())
                val hitRadius = canvasSize.width / 3f * 0.32f

                detectDragGestures(
                    onDragStart = { start ->
                        selectedNodes = emptyList()
                        dragOffset = start
                        positions.entries
                            .firstOrNull { (_, pos) -> (pos - start).getDistance() <= hitRadius }
                            ?.let { (node, _) ->
                                selectedNodes = listOf(node)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                    },
                    onDrag = { change, _ ->
                        dragOffset = change.position
                        positions.entries
                            .firstOrNull { (node, pos) ->
                                node !in selectedNodes &&
                                (pos - change.position).getDistance() <= hitRadius
                            }
                            ?.let { (node, _) ->
                                selectedNodes = selectedNodes + node
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                    },
                    onDragEnd = {
                        dragOffset = null
                        if (selectedNodes.size >= 4) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPatternComplete(selectedNodes)
                        }
                        selectedNodes = emptyList()
                    },
                    onDragCancel = {
                        dragOffset = null
                        selectedNodes = emptyList()
                    }
                )
            }
    ) {
        val positions = nodePositions(size.width)
        val nodeRadius = size.width / 3f * 0.22f
        val dotRadius = nodeRadius * 0.32f
        val lineWidth = 6.dp.toPx()
        val glowWidth = 14.dp.toPx()

        // ── Outer glow line behind connection lines ─────────────────────────
        for (i in 1 until selectedNodes.size) {
            val from = positions[selectedNodes[i - 1]] ?: continue
            val to = positions[selectedNodes[i]] ?: continue
            drawLine(
                color = activeColor.copy(alpha = 0.22f),
                start = from,
                end = to,
                strokeWidth = glowWidth,
                cap = StrokeCap.Round
            )
        }

        // ── Sharp connection lines between selected nodes ───────────────────
        for (i in 1 until selectedNodes.size) {
            val from = positions[selectedNodes[i - 1]] ?: continue
            val to = positions[selectedNodes[i]] ?: continue
            drawLine(
                color = activeColor,
                start = from,
                end = to,
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
        }

        // ── Trailing line to current finger position ────────────────────────
        if (selectedNodes.isNotEmpty() && dragOffset != null) {
            positions[selectedNodes.last()]?.let { last ->
                drawLine(
                    color = activeColor.copy(alpha = 0.45f),
                    start = last,
                    end = dragOffset!!,
                    strokeWidth = lineWidth * 0.85f,
                    cap = StrokeCap.Round
                )
            }
        }

        // ── Draw 3×3 nodes ──────────────────────────────────────────────────
        positions.forEach { (nodeNum, center) ->
            val selected = nodeNum in selectedNodes
            val nodeColor = if (selected) activeColor else outlineColor

            if (selected) {
                // Soft glowing halo
                drawCircle(
                    color = activeColor.copy(alpha = 0.18f),
                    radius = nodeRadius * 1.35f,
                    center = center
                )
            }

            // Outer ring fill
            drawCircle(
                color = nodeColor.copy(alpha = if (selected) 0.2f else 0.08f),
                radius = nodeRadius,
                center = center
            )

            // Outer ring stroke
            drawCircle(
                color = if (selected) activeColor else outlineColor.copy(alpha = 0.6f),
                radius = nodeRadius,
                center = center,
                style = Stroke(width = if (selected) 2.5.dp.toPx() else 1.5.dp.toPx())
            )

            // Inner solid dot
            drawCircle(
                color = if (selected) activeColor else outlineColor.copy(alpha = 0.7f),
                radius = if (selected) dotRadius * 1.15f else dotRadius,
                center = center
            )
        }
    }
}

private fun nodePositions(canvasWidth: Float): Map<Int, Offset> {
    val cell = canvasWidth / 3f
    return buildMap {
        for (row in 0..2) for (col in 0..2) {
            put(row * 3 + col + 1, Offset(cell * col + cell / 2f, cell * row + cell / 2f))
        }
    }
}
