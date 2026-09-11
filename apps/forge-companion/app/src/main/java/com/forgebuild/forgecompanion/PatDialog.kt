package com.forgebuild.forgecompanion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons

@Composable
fun PatDialog(
    currentPat: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var patText by remember { mutableStateOf(currentPat ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(EngineIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text("GitHub Access Token")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter a fine-grained GitHub PAT for repository vjumbo264/forgebuild. " +
                           "It is stored securely on this device and used for API operations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = patText,
                    onValueChange = { patText = it },
                    label = { Text("Personal Access Token") },
                    placeholder = { Text("github_pat_...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                if (isPasswordVisible) EngineIcons.Search else EngineIcons.Lock,
                                contentDescription = if (isPasswordVisible) "Hide token" else "Show token"
                            )
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Recommended scopes: Contents (Read & Write), Actions (Read & Write). No delete_repo needed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (patText.isNotBlank()) {
                        onSave(patText.trim())
                    }
                },
                enabled = patText.isNotBlank()
            ) {
                Text("Save Token")
            }
        },
        dismissButton = {
            Row {
                if (!currentPat.isNullOrBlank()) {
                    TextButton(
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Disconnect")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
