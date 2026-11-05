package com.motionsalt.ccstudio.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.motionsalt.ccstudio.data.ProjectData
import com.motionsalt.ccstudio.data.ProjectRepository
import com.motionsalt.ccstudio.ui.components.ConfirmDeleteDialog
import com.motionsalt.ccstudio.ui.components.TextInputDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    repository: ProjectRepository,
    onOpenProject: (String) -> Unit,
    activeProjectId: String?,
    onProjectDeleted: (String) -> Unit
) {
    var projects by remember { mutableStateOf(repository.getAllProjects()) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<ProjectData?>(null) }
    var projectToRename by remember { mutableStateOf<ProjectData?>(null) }

    fun refresh() {
        projects = repository.getAllProjects()
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewProjectDialog = true },
                icon = { Icon(EngineIcons.Add, contentDescription = "New Project") },
                text = { Text("New Look") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("MOTIONSALT CC Projects", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Offline color correction fitting & Keyflaw shader pack generator.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            if (projects.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("No Color Projects Yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Create a new look project or generate a demo project with synthetic color charts to test without taking photos.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showNewProjectDialog = true }) {
                                Text("Create Project")
                            }
                            FilledTonalButton(onClick = {
                                val demo = repository.createDemoProject()
                                refresh()
                                onOpenProject(demo.id)
                            }) {
                                Text("Create Demo")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(projects, key = { it.id }) { p ->
                        ProjectCard(
                            project = p,
                            isActive = p.id == activeProjectId,
                            onClick = { onOpenProject(p.id) },
                            onRename = { projectToRename = p },
                            onDuplicate = {
                                repository.duplicateProject(p.id)
                                refresh()
                            },
                            onDelete = { projectToDelete = p }
                        )
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        var name by remember { mutableStateOf("") }
        var lookName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("New Look Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Project Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lookName,
                        onValueChange = { lookName = it },
                        label = { Text("Look Name (e.g. Anime_Vibrant)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = if (name.isBlank()) "Untitled Look" else name.trim()
                        val finalLook = if (lookName.isBlank()) "MOTIONSALT_Grade" else lookName.trim().replace(" ", "_")
                        val created = repository.createProject(finalName, finalLook)
                        showNewProjectDialog = false
                        refresh()
                        onOpenProject(created.id)
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    projectToDelete?.let { p ->
        ConfirmDeleteDialog(
            title = "Delete Project?",
            message = "Are you sure you want to delete '${p.name}'? This will permanently remove all copied frames, fitted LUTs, and tune states from this device.",
            onConfirm = {
                repository.deleteProject(p.id)
                onProjectDeleted(p.id)
                projectToDelete = null
                refresh()
            },
            onDismiss = { projectToDelete = null }
        )
    }

    projectToRename?.let { p ->
        TextInputDialog(
            title = "Rename Project",
            initialValue = p.name,
            label = "Project Title",
            onConfirm = { newName ->
                p.name = newName
                repository.saveProject(p)
                projectToRename = null
                refresh()
            },
            onDismiss = { projectToRename = null }
        )
    }
}

@Composable
fun ProjectCard(
    project: ProjectData,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    val dateStr = dateFormatter.format(Date(project.updatedAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${project.lookName} • $dateStr",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(EngineIcons.MoreVert, contentDescription = "Project options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                            leadingIcon = { Icon(EngineIcons.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            },
                            leadingIcon = { Icon(EngineIcons.ContentCopy, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = { Icon(EngineIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val completeCount = project.pairs.count { it.isComplete }
                AssistChip(
                    onClick = onClick,
                    label = { Text("${project.pairs.size} pairs ($completeCount ready)") }
                )
                if (project.fitResult != null) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("Fit ΔE: ${String.format(Locale.US, "%.1f", project.fitResult!!.meanDeltaE)}") }
                    )
                }
            }
        }
    }
}
