package com.onmyway.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onmyway.app.R
import com.onmyway.app.ui.theme.Icon
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.semibold

/**
 * Dashed walking route, authored on the 393-wide Figma artboard.
 * Route SVGs on iOS carry a 2dp stroke bleed on every side, so path coordinates start at (2, 2).
 */
enum class MapRoute(val pathData: String) {
    Finding("M2 210.8H52V74H218V2H280"),
    Tracking("M2 181.8H52V64H218V2H280"),
    Request("M2 112.2H52V40H218V2H280"),
}

/** Horizontal scale from design space to the actual map width. */
class MapScope(val scaleX: Float)

/**
 * Illustrated campus map shared by the finding / tracking / runner screens.
 *
 * Layout is authored on the 393dp-wide Figma artboard; overlay items are placed in those
 * design coordinates and scaled horizontally to the actual width.
 */
@Composable
fun CampusMap(
    height: Dp,
    modifier: Modifier = Modifier,
    route: MapRoute? = null,
    overlay: @Composable MapScope.() -> Unit = {},
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .background(OmwColors.MapGround),
    ) {
        val scope = MapScope(maxWidth.value / DESIGN_WIDTH)
        val routePath = remember(route) { route?.let { PathParser().parsePathString(it.pathData).toPath() } }
        Canvas(Modifier.fillMaxSize()) {
            drawBlocks(height.value, scope.scaleX)
            routePath?.let { base ->
                val path = Path().apply {
                    addPath(base)
                    transform(Matrix().apply { scale(scope.scaleX * density, density) })
                }
                drawPath(
                    path,
                    OmwColors.Brand,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                    ),
                )
            }
        }
        scope.overlay()
    }
}

private const val DESIGN_WIDTH = 393f

private fun DrawScope.drawBlocks(height: Float, scaleX: Float) {
    val d = density
    fun rect(color: Color, x: Float, y: Float, w: Float, h: Float, radius: Float = 0f) {
        if (h <= 0) return
        drawRoundRect(
            color,
            topLeft = Offset(x * scaleX * d, y * d),
            size = Size(w * scaleX * d, h * d),
            cornerRadius = CornerRadius(radius * d),
        )
    }

    val street1 = height * 0.3f
    val street2 = height * 0.66f
    rect(OmwColors.MapStreet, 0f, street1, DESIGN_WIDTH, 12f)
    rect(OmwColors.MapStreet, 0f, street2, DESIGN_WIDTH, 16f)
    rect(OmwColors.MapStreet, 96f, 0f, 12f, height)
    rect(OmwColors.MapStreet, 262f, 0f, 12f, height)
    rect(OmwColors.MapStreet, 340f, 0f, 8f, height)

    val allColumns = listOf(20f to 60f, 124f to 122f, 290f to 40f)
    val rows = listOf(
        Triple(20f, street1 - 40f, allColumns),
        Triple(street1 + 28f, street2 - street1 - 44f, allColumns),
        Triple(street2 + 32f, height - street2 - 32f, allColumns.drop(1)),
    )
    for ((y, h, columns) in rows) {
        for ((x, w) in columns) rect(OmwColors.MapBuilding, x, y, w, h, radius = 6f)
    }
}

/** Place content centered on a design-space point. */
@Composable
fun MapScope.MapItem(x: Float, y: Float, content: @Composable () -> Unit) {
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) {
                placeable.place(
                    (x * scaleX).dp.roundToPx() - placeable.width / 2,
                    y.dp.roundToPx() - placeable.height / 2,
                )
            }
        },
    ) { content() }
}

enum class PinKind { Pickup, Dropoff }

/** Circular pin with a label underneath (pickup bag / destination pin). */
@Composable
fun MapPin(kind: PinKind, label: String) {
    Box(
        Modifier
            .size(30.dp)
            .clearAndSetSemantics {
                contentDescription = if (kind == PinKind.Pickup) "Pickup, $label" else "Drop off, $label"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(if (kind == PinKind.Pickup) OmwColors.Amber else OmwColors.Brand, CircleShape)
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (kind == PinKind.Pickup) R.drawable.bag_white else R.drawable.pin_white, 14.dp)
        }
        // Label hangs below the 30dp pin without affecting the pin's centering.
        Box(
            Modifier.layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())
                layout(0, 0) { placeable.place(-placeable.width / 2, 34.dp.roundToPx() - 15.dp.roundToPx()) }
            },
        ) { MapLabel(label) }
    }
}

@Composable
fun MapLabel(text: String, filled: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        style = OmwType.Caption2.semibold,
        color = if (filled) Color.White else OmwColors.Ink,
        maxLines = 1,
        modifier = Modifier
            .background(if (filled) OmwColors.Brand else Color.White, shape)
            .then(if (filled) Modifier else Modifier.border(1.dp, OmwColors.Hairline, shape))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun RunnerDot(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(R.drawable.runner, 44.dp, modifier, contentDescription)
}
