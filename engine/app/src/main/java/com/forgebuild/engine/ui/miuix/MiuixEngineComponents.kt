package com.forgebuild.engine.ui.miuix

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * ForgeBuild Engine Miuix component set (opt-in, used under
 * [EngineTheme.MIUIX]). Thin, API-stable wrappers over the Miuix library
 * (`top.yukonga.miuix.kmp:miuix-ui`) core components so generated apps target
 * Engine names rather than third-party package paths, and so component
 * coverage matches the Engine's Material set: buttons, cards, switches,
 * dialogs, nav (+ slider).
 *
 * Coverage parity with Material:
 *  - buttons  -> MiuixEngineButton / MiuixEngineTextButton
 *  - cards    -> MiuixEngineCard
 *  - switches -> MiuixEngineSwitch
 *  - dialogs  -> MiuixEngineDialog
 *  - nav      -> MiuixEngineBottomNav (+ MiuixEngineBottomNavItem)
 *  - slider   -> MiuixEngineSlider
 *
 * Corner radius (Miuix's squircle "shape system") is exposed per component
 * with the library defaults kept.
 */

/** Miuix filled (squircle) button. */
@Composable
fun MiuixEngineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = top.yukonga.miuix.kmp.basic.ButtonDefaults.CornerRadius,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = cornerRadius,
        content = content
    )
}

/** Miuix text button. */
@Composable
fun MiuixEngineTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}

/** Miuix card. */
@Composable
fun MiuixEngineCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = top.yukonga.miuix.kmp.basic.CardDefaults.CornerRadius,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(modifier = modifier, cornerRadius = cornerRadius, content = content)
}

/** Miuix switch. */
@Composable
fun MiuixEngineSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled
    )
}

/** Miuix slider. */
@Composable
fun MiuixEngineSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange
    )
}

/** Miuix bottom navigation bar; fill with [MiuixEngineBottomNavItem]s. */
@Composable
fun MiuixEngineBottomNav(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    NavigationBar(modifier = modifier, content = content)
}

/** Miuix bottom navigation item. */
@Composable
fun RowScope.MiuixEngineBottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = label,
        modifier = modifier,
        enabled = enabled
    )
}

/** Miuix dialog (title + optional summary + arbitrary content/actions). */
@Composable
fun MiuixEngineDialog(
    show: Boolean,
    onDismissRequest: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    content: @Composable () -> Unit
) {
    WindowDialog(
        show = show,
        modifier = modifier,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
        content = content
    )
}
