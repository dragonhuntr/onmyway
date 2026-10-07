package com.onmyway.app.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

// Color tokens from the On My Way Figma file.
object OmwColors {
    val Ink = Color(0xFF14201A)
    val InkSecondary = Color(0xFF5A6660)
    val Hairline = Color(0xFFDCE3DE)
    val Canvas = Color(0xFFF4F6F3)

    val Brand = Color(0xFF1F6B4A)
    val BrandDeep = Color(0xFF144A33)
    val BrandMid = Color(0xFF2E7F5C)
    val BrandSoft = Color(0xFFE1F0E7)
    val BrandOutline = Color(0xFF5DA283)
    val OnBrand = Color(0xFFD6EADF)
    val OnBrandMuted = Color(0xFFBFE0CC)

    val Amber = Color(0xFFF5B317)
    val AmberSoft = Color(0xFFFFF1CC)
    val AmberInk = Color(0xFF5C4300)
    val AmberInkDeep = Color(0xFF3D2C00)

    val MapGround = Color(0xFFE6EEE8)
    val MapStreet = Color(0xFFF7FAF7)
    val MapBuilding = Color(0xFFD4E1D7)
}

/** Type ramp matching the iOS text styles the designs were built with. */
object OmwType {
    val LargeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp)
    val Title = TextStyle(fontSize = 28.sp, lineHeight = 34.sp)
    val Title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp)
    val Title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp)
    val Headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val Callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
    val Subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val Footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val Caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val Caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp)
}

fun TextStyle.weight(weight: FontWeight) = copy(fontWeight = weight)
val TextStyle.semibold get() = weight(FontWeight.SemiBold)
val TextStyle.medium get() = weight(FontWeight.Medium)
val TextStyle.bold get() = weight(FontWeight.Bold)

@Composable
fun OnMyWayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = OmwColors.Brand,
            onPrimary = Color.White,
            primaryContainer = OmwColors.BrandSoft,
            onPrimaryContainer = OmwColors.BrandDeep,
            secondaryContainer = OmwColors.BrandSoft,
            onSecondaryContainer = OmwColors.BrandDeep,
            background = OmwColors.Canvas,
            onBackground = OmwColors.Ink,
            surface = Color.White,
            onSurface = OmwColors.Ink,
            onSurfaceVariant = OmwColors.InkSecondary,
            surfaceContainer = Color.White,
            outline = OmwColors.Hairline,
            outlineVariant = OmwColors.Hairline,
        ),
        content = content,
    )
}

private val usdFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

fun formatUsd(value: Double): String = usdFormat.format(value)

val Double.usd: String get() = formatUsd(this)

// MARK: - Buttons

enum class CtaKind { Primary, Secondary, Outline }

/** 52dp full-width CTA. Variants match the Figma "Button" component: Primary, Secondary, Outline. */
@Composable
fun CtaButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: CtaKind = CtaKind.Primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    val (foreground, background) = when (kind) {
        CtaKind.Primary -> Color.White to OmwColors.Brand
        CtaKind.Secondary -> OmwColors.BrandDeep to OmwColors.BrandSoft
        CtaKind.Outline -> OmwColors.Ink to Color.White
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) (if (pressed) 0.85f else 1f) else 0.5f)
            .background(background, shape)
            .then(if (kind == CtaKind.Outline) Modifier.border(1.5.dp, OmwColors.Hairline, shape) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(text, style = OmwType.Callout.semibold, color = foreground, modifier = Modifier.padding(horizontal = 12.dp))
    }
}

/** Compact filled button used inside cards ("Accept", "Cash out"). */
@Composable
fun CompactButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text,
        style = OmwType.Subheadline.semibold,
        color = Color.White,
        modifier = Modifier
            .alpha(if (enabled) (if (pressed) 0.85f else 1f) else 0.5f)
            .background(OmwColors.Brand, RoundedCornerShape(10.dp))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** Plain brand-colored text button ("See all", "Change"). */
@Composable
fun LinkButton(text: String, color: Color = OmwColors.Brand, style: TextStyle = OmwType.Subheadline.medium, onClick: () -> Unit) {
    Text(
        text,
        style = style,
        color = color,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

// MARK: - Surfaces

/** White rounded card with a hairline border. */
fun Modifier.card(padding: Dp = 16.dp, radius: Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return fillMaxWidth()
        .background(Color.White, shape)
        .border(1.dp, OmwColors.Hairline, shape)
        .padding(padding)
}

/** Bottom-pinned action area with a white background and top hairline. */
@Composable
fun Footer(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(color = OmwColors.Hairline, thickness = 1.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            content = content,
        )
    }
}

/** Small tracked label used as a card header ("DELIVER TO"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = OmwType.Caption.semibold.copy(letterSpacing = 0.72.sp),
        color = OmwColors.InkSecondary,
        modifier = modifier,
    )
}

/** Selectable capsule chip (tips, filters, feedback tags). */
@Composable
fun ChipToggle(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    fillsWidth: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = if (fillsWidth) 40.dp else 34.dp)
            .background(if (selected) OmwColors.Brand else Color.White, CircleShape)
            .border(1.dp, if (selected) OmwColors.Brand else OmwColors.Hairline, CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
    ) {
        Text(title, style = OmwType.Footnote.medium, color = if (selected) Color.White else OmwColors.Ink, maxLines = 1)
    }
}

/** Rounded info pill with optional leading icon ("+2 min detour"). */
@Composable
fun Pill(
    text: String,
    @DrawableRes icon: Int? = null,
    foreground: Color = OmwColors.BrandDeep,
    background: Color = OmwColors.BrandSoft,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (icon != null) Icon(icon, 14.dp)
        Text(text, style = OmwType.Caption.semibold, color = foreground)
    }
}

/** Fixed-size drawable resource, the equivalent of `Image(...).resizable().frame(...)`. */
@Composable
fun Icon(@DrawableRes id: Int, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(painterResource(id), contentDescription, modifier.size(size))
}

/** Drawable centered in a tinted badge (circle or rounded square). */
@Composable
fun IconBadge(
    @DrawableRes id: Int,
    iconSize: Dp,
    badgeSize: Dp,
    background: Color,
    radius: Dp? = null,
    border: BorderStroke? = null,
    modifier: Modifier = Modifier,
) {
    val shape = radius?.let { RoundedCornerShape(it) } ?: CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(badgeSize)
            .background(background, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier),
    ) {
        Icon(id, iconSize)
    }
}
