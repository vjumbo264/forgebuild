@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.forgebuild.engine.ui.components

import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Engine shared Material 3 Expressive component surface.
 *
 * These are the OFFICIAL M3 Expressive components from the pinned material3
 * 1.5.0-alpha train — NEVER hand-rolled approximations (standing permanent
 * rule). Wavy progress indicators and the morphing LoadingIndicator already
 * live in [EngineProgress] (single source, not duplicated here).
 *
 * What you get FOR FREE under ForgeBuildTheme (MaterialExpressiveTheme +
 * MotionScheme.expressive()): expressive Button/FAB shapes and spring-based
 * press/expand behavior, expressive Menus, Sliders, TopAppBars, ListItems —
 * no opt-in needed. The wrappers below exist for the expressive components
 * that DO have distinct APIs apps should prefer over plain counterparts.
 *
 * Full discipline reference: engine/ARCHITECTURE_MATERIAL_DISCIPLINE.md.
 */

/**
 * Expressive [ButtonGroup] — a group of related buttons with official
 * spring-based press/expand spacing animation. Prefer this over a hand-spaced
 * Row of buttons for toolbars/actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EngineButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.material3.ButtonGroupScope.() -> Unit
) {
    ButtonGroup(modifier = modifier, content = content)
}

/**
 * Expressive [SplitButton] — a leading action button + trailing menu button
 * with the official expressive chevron expansion behavior. Prefer this over a
 * hand-built "button + dropdown" pair.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EngineSplitButton(
    leadingContent: @Composable androidx.compose.material3.SplitButtonLeadingContentScope.() -> Unit,
    trailingContent: @Composable androidx.compose.material3.SplitButtonTrailingContentScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    SplitButton(leadingContent = leadingContent, trailingContent = trailingContent, modifier = modifier)
}

/**
 * Expressive [FloatingActionButtonMenu] — FAB that expands into a menu of
 * related actions with the official expressive motion. Prefer this over a
 * hand-rolled speed-dial.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EngineFabMenu(
    expanded: Boolean,
    button: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.material3.FloatingActionButtonMenuScope.() -> Unit
) {
    FloatingActionButtonMenu(expanded = expanded, button = button, modifier = modifier, content = content)
}
