package com.hc.rzi.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hc.rzi.ui.theme.Spacing

@Composable
fun PinSetupScreen(viewModel: PinSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.surface,
                        1f to scheme.primaryContainer.copy(alpha = 0.4f),
                    )
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = scheme.primary,
                    contentColor = scheme.onPrimary,
                    modifier = Modifier.size(88.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.FormatQuote,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = "Set an admin PIN",
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Only an admin can add, edit, or delete quotes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.xl))

                OutlinedTextField(
                    value = state.pin,
                    onValueChange = viewModel::onPinChange,
                    label = { Text("PIN") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm + 4.dp))

                OutlinedTextField(
                    value = state.confirm,
                    onValueChange = viewModel::onConfirmChange,
                    label = { Text("Confirm PIN") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = state.error?.let { message -> { Text(message) } },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.xl))

                Button(
                    onClick = viewModel::createPin,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Create PIN")
                }
            }
        }
    }
}
