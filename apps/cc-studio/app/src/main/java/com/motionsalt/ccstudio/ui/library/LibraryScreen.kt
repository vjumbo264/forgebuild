package com.motionsalt.ccstudio.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.motionsalt.ccstudio.core.EffectDefinition
import com.motionsalt.ccstudio.data.EffectRepository

@Composable
fun LibraryScreen(
    effectRepository: EffectRepository
) {
    val effects = remember { effectRepository.getAvailableEffects() }
    var selectedCategory by remember { mutableStateOf("All") }
    var expandedEffectId by remember { mutableStateOf<String?>(null) }

    val categories = listOf("All", "Colour correction", "Tone", "Detail", "Look")

    val filtered = effects.filter { eff ->
        selectedCategory == "All" || eff.category.equals(selectedCategory, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Effect Library (${effects.size})", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "17 In-Scope Keyflaw 1.4.9 effects with strict GLSL ES 1.00 precision headers and typed uniforms.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory),
            edgePadding = 0.dp
        ) {
            categories.forEach { cat ->
                Tab(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    text = { Text(cat, fontSize = 12.sp) }
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.id }) { eff ->
                val isExpanded = expandedEffectId == eff.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedEffectId = if (isExpanded) null else eff.id },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(eff.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    "${eff.id} • ${eff.parameters.size} parameters",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SuggestionChip(
                                onClick = { expandedEffectId = if (isExpanded) null else eff.id },
                                label = { Text(eff.category, fontSize = 11.sp) }
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (eff.description.isNotBlank()) {
                                    Text(eff.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                }

                                if (eff.expandBounds > 0f) {
                                    AssistChip(
                                        onClick = { /* expandBounds chip */ },
                                        label = { Text("expandBounds: ${eff.expandBounds}") }
                                    )
                                }

                                Text("Parameters:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                eff.parameters.forEach { p ->
                                    Text(
                                        "• ${p.label} (${p.key}): ${p.type} default=${p.defaultValue}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(Modifier.height(4.dp))
                                Text("GLSL ES 1.00 Shader:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.85f))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        eff.shaderCode.take(240) + if (eff.shaderCode.length > 240) "\n..." else "",
                                        color = Color.Green,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
