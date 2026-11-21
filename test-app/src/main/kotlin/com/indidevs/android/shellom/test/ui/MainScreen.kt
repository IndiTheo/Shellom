package com.indidevs.android.shellom.test.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.indidevs.android.adbeesh.config.AdbSessionConfig
import com.indidevs.android.adbeesh.notifications.NotificationCallback
import com.indidevs.android.adbeesh.notifications.NotificationCodeProvider
import com.indidevs.android.adbeesh.notifications.NotificationFlowConfig
import com.indidevs.android.adbeesh.pairing.AdbPairing
import com.indidevs.android.adbeesh.pairing.AdbPairingWorkflow
import com.indidevs.android.shellom.InstrumentationShellProvider
import com.indidevs.android.shellom.ShellExecutor
import com.indidevs.android.shellom.ShellStatus
import com.indidevs.android.shellom.integrations.adbeesh.AdbeeshExecutor
import com.indidevs.android.shellom.integrations.shizuku.ShizukuExecutor
import com.indidevs.android.shellom.test.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

data class ShellProviderOption(
    val id: String,
    val name: String,
    val prepare: suspend (Context) -> Result<Unit>,
    val createExecutor: suspend (Context, CoroutineScope) -> ShellExecutor,
    val cleanup: suspend (Context) -> Unit = {}
)

@Composable
fun MainScreen(context: Context) {
    val scope = rememberCoroutineScope()
    val pairingId = "shellom-test-session"
    val pairing = remember { AdbPairing.get(context, pairingId, AdbSessionConfig(pairingId)) }
    val pairingStatus by pairing.status.collectAsState()

    val providers = remember {
        listOf(
            ShellProviderOption(
                id = "adbeesh",
                name = "ADBeesh (Wireless ADB)",
                prepare = { ctx ->
                    runCatching {
                        val flowConfig = NotificationFlowConfig.Builder()
                            .common { title = "Shellom Pairing" }
                            .build()
                        val workflow = AdbPairingWorkflow.Builder()
                            .withCodeProvider(NotificationCodeProvider(ctx, flowConfig))
                            .addCallback(NotificationCallback(ctx, flowConfig))
                            .build()
                        pairing.ensurePaired(workflow)
                    }
                },
                createExecutor = { ctx, scope ->
                    val session = pairing.openSession()
                    AdbeeshExecutor(
                        session = session,
                        elevationSatisfied = { false }
                    )
                },
                cleanup = { ctx ->
                    pairing.unpair()
                }
            ),
            ShellProviderOption(
                id = "shizuku",
                name = "Shizuku (IPC Daemon)",
                prepare = { ctx ->
                    runCatching {
                        if (!Shizuku.pingBinder()) {
                            throw IllegalStateException("Shizuku service is not available.")
                        }
                        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                            Shizuku.requestPermission(1001)
                            throw SecurityException("Shizuku permission requested. Grant permission and retry.")
                        }
                    }
                },
                createExecutor = { _, _ ->
                    ShizukuExecutor(autoRequestPermission = true)
                }
            )
        )
    }

    var selectedProvider by remember { mutableStateOf(providers.first()) }
    var shellStatus by remember { mutableStateOf(ShellStatus.IDLE) }
    var outputMessage by remember { mutableStateOf("Ready to prepare and elevate.") }
    var providerInstance by remember { mutableStateOf<InstrumentationShellProvider?>(null) }

    val pLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            pLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Shellom! Test App", style = MaterialTheme.typography.headlineMedium)

        Text("Select Shell Provider:", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            providers.forEach { provider ->
                Button(
                    onClick = { selectedProvider = provider },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedProvider.id == provider.id)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(provider.name.substringBefore(" "))
                }
            }
        }

        Text("Active Provider: ${selectedProvider.name}", style = MaterialTheme.typography.bodyLarge)
        if (selectedProvider.id == "adbeesh") {
            Text("Pairing Status: ${pairingStatus.name}", style = MaterialTheme.typography.bodyLarge)
        }
        Text("Instrumentation Shell Status: ${shellStatus.name}", style = MaterialTheme.typography.bodyLarge)

        Button(
            onClick = {
                scope.launch {
                    runCatching {
                        outputMessage = "Preparing provider '${selectedProvider.name}'..."
                        selectedProvider.prepare(context).getOrThrow()
                        outputMessage = "Provider '${selectedProvider.name}' prepared successfully!"
                    }.onFailure {
                        outputMessage = "Preparation note/error: ${it.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Setup Executor")
        }

        Button(
            onClick = {
                scope.launch {
                    runCatching {
                        outputMessage = "Elevating process via ${selectedProvider.name}..."
                        val executor = selectedProvider.createExecutor(context, scope)
                        val provider = InstrumentationShellProvider(
                            context = context,
                            scope = scope,
                            executor = executor,
                            requiredPermissions = listOf(
                                "android.permission.WRITE_SECURE_SETTINGS",
                                "android.permission.DUMP"
                            )
                        )
                        providerInstance = provider
                        provider.observeStatus(scope) { shellStatus = it }
                        provider.awaitContext()
                        outputMessage = "Elevation successful via ${selectedProvider.name}! Privileged context obtained (UID 2000)."
                    }.onFailure {
                        shellStatus = ShellStatus.ERROR
                        outputMessage = "Elevation failed: ${it.message}"
                        Log.e(Constants.TAG, "Elevation failed", it)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2. Elevate Process via the Selected Executor")
        }

        Button(
            onClick = {
                scope.launch {
                    runCatching {
                        val provider = providerInstance
                        if (provider == null || shellStatus != ShellStatus.READY) {
                            val checkBefore = context.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                            val statusStr = if (checkBefore == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED (-1)"
                            outputMessage = "Before Elevation (Unprivileged): DUMP permission = $statusStr"
                        } else {
                            val privilegedContext = provider.awaitContext()
                            val checkAfter = privilegedContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                            val statusStr = if (checkAfter == PackageManager.PERMISSION_GRANTED) "GRANTED (0)" else "DENIED"
                            outputMessage = "After Elevation (Privileged UID 2000): DUMP permission = $statusStr"
                        }
                    }.onFailure {
                        outputMessage = "Privileged check failed: ${it.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("3. Test Privileged Action (Compare Before vs After)")
        }

        Button(
            onClick = {
                scope.launch {
                    runCatching {
                        val executor = selectedProvider.createExecutor(context, scope)
                        executor.execute("am force-stop com.indidevs.android.shellom.test")
                    }.onFailure {
                        Log.w(Constants.TAG, "Failed to kill test process via executor", it)
                    }
                    shellStatus = ShellStatus.IDLE
                    providerInstance = null
                    outputMessage = "Test / elevation process stopped and reset."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("4. Stop Test / Kill Instrumentation Process")
        }

        Button(
            onClick = {
                scope.launch {
                    runCatching {
                        selectedProvider.cleanup(context)
                        outputMessage = "Cleanup performed for ${selectedProvider.name}."
                    }.onFailure {
                        outputMessage = "Cleanup error: ${it.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Executor")
        }

        if (outputMessage.isNotEmpty()) {
            Text("Output: $outputMessage", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.weight(1f))
        Notice()
    }
}

@Composable
private fun Notice() {
    val annotatedString = buildAnnotatedString {
        append("This application is intended for internal end-to-end testing." +
                "For library integration guides and source code, please visit: ")
                pushStringAnnotation(tag = "URL", annotation = "https://gitlab.com/IndiDevs/shellom")
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append("https://gitlab.com/IndiDevs/shellom")
        }
        pop()
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodySmall.copy(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier.padding(16.dp).fillMaxWidth()
    )
}
