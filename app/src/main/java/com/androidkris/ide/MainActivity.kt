package com.androidkris.ide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.androidkris.ide.ui.theme.AndroidKrisTheme
import com.androidkris.ide.ui.workspace.WorkspaceScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AndroidKrisTheme {
                WorkspaceScreen()
            }
        }
    }
}
