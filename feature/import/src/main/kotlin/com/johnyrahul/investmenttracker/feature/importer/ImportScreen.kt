package com.johnyrahul.investmenttracker.feature.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.johnyrahul.investmenttracker.core.data.repository.StatementRecord
import java.time.format.DateTimeFormatter

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun ImportRoute(viewModel: ImportViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importUri(it, origin = "file") }
    }
    val gmailConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onGmailConsentResult(it.data)
    }
    LaunchedEffect(state.gmailConsent) {
        state.gmailConsent?.let { gmailConsent.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }

    ImportScreen(
        state = state,
        modifier = modifier,
        onSavePan = viewModel::savePan,
        onAddPassword = viewModel::addPassword,
        onPickPdf = { pickPdf.launch(arrayOf("application/pdf")) },
        onConnectGmail = viewModel::connectGmail,
        onCheckGmail = viewModel::checkGmailNow,
        onDisconnectGmail = viewModel::disconnectGmail,
        onRefreshPrices = viewModel::refreshPrices,
        onDismissMessage = viewModel::dismissMessage,
    )

    if (state.needsPassword) {
        PasswordDialog(onSubmit = viewModel::submitPassword, onCancel = viewModel::cancelPassword)
    }
}

@Composable
fun ImportScreen(
    state: ImportUiState,
    modifier: Modifier = Modifier,
    onSavePan: (String) -> Boolean,
    onAddPassword: (String) -> Unit,
    onPickPdf: () -> Unit,
    onConnectGmail: () -> Unit,
    onCheckGmail: () -> Unit,
    onDisconnectGmail: () -> Unit,
    onRefreshPrices: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.busy?.let { busy ->
            item {
                Column {
                    Text(busy, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
        state.message?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                        Text(message, Modifier.weight(1f).padding(vertical = 8.dp))
                        TextButton(onClick = onDismissMessage) { Text("OK") }
                    }
                }
            }
        }
        item { PasswordsCard(state, onSavePan, onAddPassword) }
        item {
            Section("Import a statement") {
                Text(
                    "NSDL / CDSL email a Consolidated Account Statement (CAS) every month with your shares and " +
                        "mutual funds. For full mutual fund transaction history, request a detailed CAS from CAMS. " +
                        "You can also share a CAS PDF to this app from Gmail or Files.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onPickPdf, enabled = state.busy == null) { Text("Choose CAS PDF") }
            }
        }
        item {
            Section("Gmail auto-import") {
                if (state.gmailEnabled) {
                    Text(state.gmailStatus ?: "Connected. New CAS emails are imported twice a day.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCheckGmail, enabled = state.busy == null) { Text("Check now") }
                        OutlinedButton(onClick = onDisconnectGmail) { Text("Disconnect") }
                    }
                } else {
                    Text(
                        "Let the app read your CAS emails (read-only) and import them automatically. " +
                            "Emails are read on this phone; nothing is uploaded.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.gmailStatus?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = onConnectGmail, enabled = state.busy == null) { Text("Connect Gmail") }
                }
            }
        }
        item {
            Section("Prices") {
                Text(state.pricesStatus ?: "NAVs (AMFI) and closing prices (NSE / BSE) are refreshed twice a day.")
                OutlinedButton(onClick = onRefreshPrices, enabled = state.busy == null) { Text("Refresh now") }
            }
        }
        if (state.statements.isNotEmpty()) {
            item { Text("Imported statements", style = MaterialTheme.typography.titleMedium) }
            items(state.statements, key = { "${it.kind}-${it.importedAtMillis}" }) { StatementRow(it) }
        }
        item {
            Section("Gmail setup details") {
                Text(
                    "Needed once to create the Android OAuth client in Google Cloud (see README).",
                    style = MaterialTheme.typography.bodySmall,
                )
                SelectionContainer {
                    Text(
                        "Package: ${state.packageName}\nSHA-1: ${state.signingSha1 ?: "unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordsCard(state: ImportUiState, onSavePan: (String) -> Boolean, onAddPassword: (String) -> Unit) {
    var editingPan by remember { mutableStateOf(false) }
    var pan by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    Section("Statement passwords") {
        val saved = state.pan
        if (saved != null && !editingPan) {
            Text("PAN ${saved.take(2)}•••••${saved.takeLast(3)} is used to open CAS PDFs.")
            TextButton(onClick = { editingPan = true }) { Text("Change PAN") }
        } else {
            Text(
                "CAS PDFs are locked with your PAN. It's stored encrypted on this phone only.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = pan,
                onValueChange = { pan = it.uppercase().take(10) },
                label = { Text("PAN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                if (onSavePan(pan)) {
                    pan = ""
                    editingPan = false
                }
            }) { Text("Save PAN") }
        }
        Text(
            "CAMS / KFintech statements use the password you chose when requesting them." +
                if (state.extraPasswordCount > 0) " ${state.extraPasswordCount} saved." else "",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = extra,
                onValueChange = { extra = it },
                label = { Text("Other password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                onAddPassword(extra)
                extra = ""
            }) { Text("Add") }
        }
    }
}

@Composable
private fun StatementRow(statement: StatementRecord) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("${statement.kind} · as of ${statement.asOf.format(DATE)}", style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append("${statement.holdingsCount} holdings")
                    if (statement.transactionsCount > 0) append(", ${statement.transactionsCount} transactions")
                    append(" · from ${statement.origin}")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PasswordDialog(onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Statement password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This PDF didn't open with your PAN or saved passwords. Enter its password; it will be remembered.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text("Open") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
