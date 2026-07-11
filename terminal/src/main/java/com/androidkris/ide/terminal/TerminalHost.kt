package com.androidkris.ide.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.launch

private enum class TerminalMode { Loading, Setup, Bash, System }

/**
 * Entry point for the terminal: on first use it offers to install the Linux
 * bootstrap (bash + coreutils). Once installed, runs a real login bash inside
 * the prefix; otherwise the user can fall back to the Android system shell.
 */
@Composable
fun TerminalHost(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(TerminalMode.Loading) }
    // Set when the prefix is missing/broken but a previously-downloaded, still-valid bootstrap
    // zip is cached (see Environment.cachedZip) — lets BootstrapSetup silently re-extract in
    // seconds instead of showing the "unduh 30 MB" screen, for the case where something outside
    // our control (see extract()'s OEM-cleanup comment) wiped an already-working prefix.
    var autoRepair by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Environment.init(context)
        if (Environment.isInstalled) {
            val binCount = Environment.bin.listFiles()?.size ?: -1
            Environment.logDiag("TerminalHost launch: isInstalled=true binCount=$binCount")
            // Cheap + idempotent: keeps an already-installed prefix in sync with whatever this
            // build of the app expects, without re-downloading the bootstrap.
            Environment.repairPrefix()
            mode = TerminalMode.Bash
        } else {
            val cached = BootstrapInstaller.hasValidCache(context)
            Environment.logDiag("TerminalHost launch: isInstalled=false bashExists=${Environment.bash.exists()} validCache=$cached")
            autoRepair = cached
            mode = TerminalMode.Setup
        }
    }

    when (mode) {
        TerminalMode.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }

        TerminalMode.Setup -> BootstrapSetup(
            modifier = modifier,
            autoStart = autoRepair,
            onInstalled = { mode = TerminalMode.Bash },
            onUseSystemShell = { mode = TerminalMode.System },
        )

        TerminalMode.Bash -> {
            var activeSession by remember { mutableStateOf<TerminalSession?>(null) }
            Column(modifier.fillMaxSize()) {
                GradleBar()
                UbuntuBar(sendCommand = { cmd ->
                    activeSession?.let { s -> val b = cmd.toByteArray(); s.write(b, 0, b.size) }
                })
                TerminalScreen(
                    modifier = Modifier.weight(1f),
                    shellPath = Environment.bash.absolutePath,
                    workingDir = Environment.home.absolutePath,
                    env = Environment.shellEnv(),
                    // Non-login: a login shell (leading '-') sources bash's compiled-in
                    // /data/data/com.termux/.../etc/profile, which our sandbox can't even stat
                    // (EACCES) — noisy and pointless since Environment.shellEnv() already sets
                    // everything a login shell's profile would.
                    args = arrayOf("bash"),
                    onSessionEnd = onClose,
                    onSessionReady = { activeSession = it },
                )
            }
        }

        TerminalMode.System -> TerminalScreen(
            modifier = modifier,
            onSessionEnd = onClose,
        )
    }
}

@Composable
private fun BootstrapSetup(
    modifier: Modifier,
    autoStart: Boolean,
    onInstalled: () -> Unit,
    onUseSystemShell: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<BootstrapState>(BootstrapState.Idle) }
    var autoTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is BootstrapState.Done) onInstalled()
    }

    // Fires once per composition if a valid cached zip was found. Left ungated on failure (falls
    // through to the normal Idle/Failed branch below with the manual button + system-shell
    // fallback still available) so a broken auto-repair never strands the user with no options.
    LaunchedEffect(autoStart) {
        if (autoStart && !autoTriggered) {
            autoTriggered = true
            state = BootstrapState.Verifying
            BootstrapInstaller.install(context) { st -> state = st }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.Terminal,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Environment Linux",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Untuk shell Linux penuh (bash + coreutils + apt), unduh environment sekali (±30 MB). " +
                    "Tanpa ini, tersedia shell sistem Android yang terbatas.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                modifier = Modifier.padding(vertical = 16.dp),
            )

            when (val s = state) {
                is BootstrapState.Idle, is BootstrapState.Failed -> {
                    Button(onClick = {
                        // Belt-and-suspenders alongside BootstrapInstaller's own Mutex: flip
                        // state away from Idle/Failed synchronously so a rapid double-tap
                        // landing before recomposition swaps out this Button can't fire a
                        // second install() call in the same frame.
                        if (state is BootstrapState.Idle || state is BootstrapState.Failed) {
                            state = BootstrapState.Downloading(0f)
                            scope.launch {
                                BootstrapInstaller.install(context) { st -> state = st }
                            }
                        }
                    }) { Text("Unduh & pasang (30 MB)") }
                    TextButton(onClick = onUseSystemShell) { Text("Pakai shell sistem dulu") }
                    if (s is BootstrapState.Failed) {
                        Text(
                            "Gagal: ${s.message}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }

                is BootstrapState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { s.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Mengunduh ${(s.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                is BootstrapState.Verifying -> ProgressLabel(
                    if (autoStart) "Memperbaiki environment (dari cache)…" else "Memverifikasi…"
                )
                is BootstrapState.Extracting -> ProgressLabel(
                    if (autoStart) "Memperbaiki environment (dari cache)…" else "Mengekstrak…"
                )
                is BootstrapState.Done -> ProgressLabel("Selesai")
            }
        }
    }
}

@Composable
private fun ProgressLabel(text: String) {
    CircularProgressIndicator()
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
}

/**
 * Thin status/action bar shown above the terminal until Gradle is installed, then hides itself.
 * Separate from [BootstrapSetup] on purpose: Gradle isn't part of the apt/dpkg prefix at all (see
 * [GradleInstaller]) and installing it doesn't block using the shell for anything else, so it's a
 * non-modal bar rather than a full-screen gate like the bootstrap install is.
 */
@Composable
private fun GradleBar() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(Environment.isGradleInstalled) }
    var state by remember { mutableStateOf<GradleState>(GradleState.Idle) }
    var autoTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is GradleState.Done) installed = true
    }

    // Same self-heal as BootstrapSetup's autoStart: Gradle's install also writes ~2000 files and
    // chmods a launcher script, so it's exposed to the same OEM-cleanup risk as the bootstrap.
    LaunchedEffect(Unit) {
        if (!installed && !autoTriggered && GradleInstaller.hasValidCache(context)) {
            autoTriggered = true
            state = GradleState.Verifying
            GradleInstaller.install(context) { st -> state = st }
        }
    }

    if (installed) return

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            when (val s = state) {
                is GradleState.Idle, is GradleState.Failed -> {
                    Text(
                        if (s is GradleState.Failed) "Gradle gagal: ${s.message}" else "Gradle belum terpasang",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (state is GradleState.Idle || state is GradleState.Failed) {
                            state = GradleState.Downloading(0f)
                            scope.launch {
                                GradleInstaller.install(context) { st -> state = st }
                            }
                        }
                    }) { Text("Pasang Gradle") }
                }

                is GradleState.Downloading -> Text(
                    "Mengunduh Gradle ${(s.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )

                is GradleState.Verifying -> Text("Memverifikasi Gradle…", style = MaterialTheme.typography.bodySmall)
                is GradleState.Extracting -> Text("Mengekstrak Gradle…", style = MaterialTheme.typography.bodySmall)
                is GradleState.Done -> Text("Gradle terpasang", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Bar for the second, independent environment: a genuine Ubuntu rootfs entered via `proot` (see
 * [UbuntuInstaller] and [Environment.prootEnterCommand]). Unlike the bionic prefix, apt/dpkg
 * inside there are completely unmodified — no hardcoded-path bugs are possible by construction.
 * `proot` itself isn't auto-installed here (unlike the Ubuntu rootfs download) — it's a single
 * `apt install -y proot` the user runs in the same bash session like any other package, reusing
 * the already-hardened apt/dpkg pipeline instead of adding a separate native-binary trust
 * decision. A manual "Cek lagi" button re-checks for it since the bar can't observe a file
 * appearing from a command typed directly into the terminal without polling.
 */
@Composable
private fun UbuntuBar(sendCommand: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(Environment.isUbuntuInstalled) }
    var state by remember { mutableStateOf<UbuntuState>(UbuntuState.Idle) }
    var autoTriggered by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    val prootReady = remember(refreshTick, state) { Environment.isProotInstalled }

    LaunchedEffect(state) {
        if (state is UbuntuState.Done) installed = true
    }

    LaunchedEffect(Unit) {
        if (!installed && !autoTriggered && UbuntuInstaller.hasValidCache(context)) {
            autoTriggered = true
            state = UbuntuState.Verifying
            UbuntuInstaller.install(context) { st -> state = st }
        }
    }

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            when (val s = state) {
                is UbuntuState.Idle, is UbuntuState.Failed -> {
                    if (installed) {
                        if (prootReady) {
                            Text(
                                "Ubuntu siap",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { sendCommand(Environment.prootEnterCommand() + "\n") }) {
                                Text("Masuk Ubuntu")
                            }
                        } else {
                            Text(
                                "Ubuntu siap — jalankan 'apt install -y proot' dulu",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { refreshTick++ }) { Text("Cek lagi") }
                        }
                    } else {
                        Text(
                            if (s is UbuntuState.Failed) "Ubuntu gagal: ${s.message}" else "Ubuntu (proot) belum terpasang",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            if (state is UbuntuState.Idle || state is UbuntuState.Failed) {
                                state = UbuntuState.Downloading(0f)
                                scope.launch {
                                    UbuntuInstaller.install(context) { st -> state = st }
                                }
                            }
                        }) { Text("Pasang Ubuntu") }
                    }
                }

                is UbuntuState.Downloading -> Text(
                    "Mengunduh Ubuntu ${(s.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )

                is UbuntuState.Verifying -> Text("Memverifikasi Ubuntu…", style = MaterialTheme.typography.bodySmall)
                is UbuntuState.Extracting -> Text("Mengekstrak Ubuntu…", style = MaterialTheme.typography.bodySmall)
                is UbuntuState.Done -> Text("Ubuntu terpasang", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
