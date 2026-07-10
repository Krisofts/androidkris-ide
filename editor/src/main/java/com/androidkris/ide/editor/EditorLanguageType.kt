package com.androidkris.ide.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

/**
 * Maps a file to the sora-editor [Language] used for highlighting.
 *
 * Highlighting is driven by TextMate grammars registered in [TextMateSetup]
 * (Java/Kotlin/XML). Anything else falls back to plain text. Requires
 * [TextMateSetup.ensureInitialized] to have run before [create].
 */
enum class EditorLanguageType(private val scope: String?) {
    JAVA("source.java"),
    KOTLIN("source.kotlin"),
    XML("text.xml"),
    PLAIN(null);

    fun create(): Language =
        scope?.let { TextMateLanguage.create(it, true) } ?: EmptyLanguage()

    companion object {
        fun fromFileName(name: String): EditorLanguageType =
            when (name.substringAfterLast('.', "").lowercase()) {
                "java" -> JAVA
                "kt", "kts" -> KOTLIN   // includes *.gradle.kts
                "xml" -> XML
                else -> PLAIN
            }
    }
}
