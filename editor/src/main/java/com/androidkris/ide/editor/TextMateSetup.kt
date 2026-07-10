package com.androidkris.ide.editor

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource

/**
 * One-time registration of TextMate grammars + themes bundled in `assets/textmate/`.
 *
 * Grammars (Java/Kotlin/XML) and the two themes were taken from sora-editor's own
 * demo assets, so they load through sora's registries unchanged. Idempotent —
 * [ensureInitialized] can be called from every editor view creation.
 */
object TextMateSetup {

    private const val THEME_DARK = "darcula"
    private const val THEME_LIGHT = "quietlight"

    @Volatile
    private var initialized = false

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) return
        val assets = context.applicationContext.assets
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))

        val themeRegistry = ThemeRegistry.getInstance()
        for (name in arrayOf(THEME_DARK, THEME_LIGHT)) {
            val path = "textmate/$name.json"
            themeRegistry.loadTheme(
                ThemeModel(
                    IThemeSource.fromInputStream(
                        FileProviderRegistry.getInstance().tryGetInputStream(path), path, null
                    ),
                    name,
                ).apply { isDark = name == THEME_DARK }
            )
        }

        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        initialized = true
    }

    /** Switch the active theme (call before reading [colorScheme]). */
    fun applyTheme(dark: Boolean) {
        ThemeRegistry.getInstance().setTheme(if (dark) THEME_DARK else THEME_LIGHT)
    }

    /** A color scheme reflecting the currently active TextMate theme. */
    fun colorScheme(): EditorColorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
}
