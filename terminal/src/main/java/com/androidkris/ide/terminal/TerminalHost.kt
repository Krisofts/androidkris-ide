package com.androidkris.ide.terminal

/**
 * Fase 2 home: terminal emulator + Linux environment.
 *
 * Will host Termux's TerminalView/TerminalEmulator and manage a bootstrapped
 * rootfs (shell + coreutils) plus an arm64 JDK 17 installed into app data, so
 * `java`, `sh`, and later `gradle` run from an in-app terminal.
 *
 * Placeholder for Fase 0 so the module boundary is real.
 */
object TerminalModule {
    const val PHASE = "Fase 2 — Termux terminal + Linux env"
}
