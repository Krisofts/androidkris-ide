package com.androidkris.ide.buildengine

import kotlinx.coroutines.flow.Flow

/**
 * Contract for the on-device build pipeline (Fase 3–4).
 *
 * A concrete implementation will:
 *  1. ensure the toolchain is installed (Gradle/AAPT2/d8/JDK, arm64) — [ToolchainManager],
 *  2. connect to a Gradle daemon via the Tooling API and sync the project model,
 *  3. run tasks (e.g. `assembleDebug`), streaming logs back as [BuildEvent]s,
 *  4. surface the produced APK for install.
 *
 * This is a placeholder interface for Fase 0 so the module boundary is real;
 * no implementation exists yet.
 */
interface BuildEngine {

    /** Sync the project at [projectDir] and return its coarse model. */
    suspend fun sync(projectDir: String): ProjectModel

    /** Run [tasks] (e.g. `["assembleDebug"]`), streaming progress + log lines. */
    fun runTasks(projectDir: String, tasks: List<String>): Flow<BuildEvent>
}

data class ProjectModel(
    val name: String,
    val modules: List<String>,
)

sealed interface BuildEvent {
    data class Log(val line: String) : BuildEvent
    data class Progress(val message: String, val fraction: Float) : BuildEvent
    data class Finished(val success: Boolean, val outputApk: String?) : BuildEvent
}
