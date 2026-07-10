# AndroidKris IDE

On-device Android IDE (build APKs on your phone), AndroidIDE-style. Kotlin + Jetpack Compose,
multi-module. See [MASTERPLAN.md](MASTERPLAN.md) for the full architecture and phased roadmap.

> **License note:** the build toolchain, terminal, and editor components we reuse (AndroidIDE,
> Termux — GPLv3; sora-editor — LGPL) mean this project must ship as **GPLv3 open source** once
> those land. Decide this before Fase 2.

## Status — Fase 1 (Code editor) ✅

Working code editor: sora-editor with Java highlighting, line numbers, undo/redo, find/replace,
symbol input row; a file-tree drawer (expand/collapse, new/rename/delete, long-press menu),
multi-file tabs with dirty markers, and save-to-disk. A sample project is seeded on first run.
Kotlin/XML highlighting + arbitrary-folder access are Fase 1.1 (see MASTERPLAN.md).

Fase 0 (multi-module skeleton + arm64 build-capability gate in `:build-engine`) is done.

## Modules

| Module | Role | Phase |
|---|---|---|
| `:app` | Compose UI shell, Hilt root, navigation | ongoing |
| `:editor` | sora-editor code-editing surface | Fase 1 |
| `:terminal` | Termux terminal + Linux env + arm64 JDK | Fase 2 |
| `:build-engine` | ABI detection, Gradle Tooling, build pipeline | Fase 3–4 |
| `:lsp` | Java/XML/Kotlin language servers | Fase 5 |

## Requirements

- **Android Studio** (Ladybug or newer) — open this folder as a project.
- JDK 17 (bundled with Android Studio's JBR).
- To actually build APKs on a device (Fase 4+): a **physical arm64-v8a device, Android 8.0+**.
  x86 emulators run the editor but can never build (toolchain is arm64-only).

## First build

1. Open the folder in Android Studio → let it sync (it will generate the Gradle wrapper jar and
   `local.properties` pointing at your SDK).
2. Run the `app` configuration on any device/emulator (API 26+).
3. If syncing from CLI instead: install Gradle 8.9+ and run `gradle wrapper` once to create the
   wrapper, then `./gradlew :app:assembleDebug`.

## Next up — Fase 2 (Terminal + Linux env)

Integrate Termux's `terminal-emulator` + `terminal-view`, bootstrap a minimal rootfs, and install
an arm64 JDK 17 into app data so `java`/`sh` run from an in-app terminal. See MASTERPLAN.md §3.
(Optionally finish Fase 1.1 first: TextMate Kotlin/XML highlighting + open-any-folder access.)
