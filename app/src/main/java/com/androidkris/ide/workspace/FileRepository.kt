package com.androidkris.ide.workspace

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real `java.io.File` access to the current project tree.
 *
 * Fase 1 operates inside the app's own external files dir (`.../files/projects`),
 * which needs no runtime permission and gives real filesystem paths — the shape
 * the Gradle build engine will need in Fase 3. Opening arbitrary device folders
 * (via `MANAGE_EXTERNAL_STORAGE`, AndroidIDE-style) is a Fase 1.1 add.
 */
@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val projectsDir: File
        get() = File(context.getExternalFilesDir(null), "projects").apply { mkdirs() }

    /** Ensures a sample project exists so there is something to edit on first run. */
    fun ensureSampleProject(): File {
        val root = File(projectsDir, "HelloAndroid")
        if (root.exists()) return root
        root.mkdirs()
        File(root, "app/src/main/java/com/example/hello").mkdirs()
        File(root, "app/src/main/java/com/example/hello/MainActivity.java")
            .writeText(SAMPLE_JAVA)
        File(root, "app/src/main/res/values/strings.xml").apply { parentFile?.mkdirs() }
            .writeText(SAMPLE_STRINGS)
        File(root, "app/build.gradle.kts").writeText(SAMPLE_GRADLE)
        File(root, "README.md").writeText("# HelloAndroid\n\nSample project opened by AndroidKris IDE.\n")
        return root
    }

    fun listChildren(dir: File): List<File> =
        dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()

    fun readText(file: File): String = file.readText()

    fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    fun createFile(parent: File, name: String): File =
        File(parent, name).apply { parentFile?.mkdirs(); createNewFile() }

    fun createDir(parent: File, name: String): File =
        File(parent, name).apply { mkdirs() }

    fun delete(file: File): Boolean = file.deleteRecursively()

    fun rename(file: File, newName: String): File {
        val target = File(file.parentFile, newName)
        file.renameTo(target)
        return target
    }
}

private const val SAMPLE_JAVA = """package com.example.hello;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello from AndroidKris IDE!");
        setContentView(tv);
    }
}
"""

private const val SAMPLE_STRINGS = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">HelloAndroid</string>
</resources>
"""

private const val SAMPLE_GRADLE = """plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.hello"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.hello"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
"""
