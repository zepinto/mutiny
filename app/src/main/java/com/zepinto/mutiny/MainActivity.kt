package com.zepinto.mutiny

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // FLAG_SECURE: no screenshots, screen recording or recents thumbnail of a role or a secret choice.
        if (BuildConfig.SECURE_WINDOW) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Palette.Gold,
                    secondary = Palette.Aqua,
                    tertiary = Palette.Lilac,
                    background = ComposeColor.Black,
                    surface = ComposeColor.Black,
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = ComposeColor.Black) { MutinyApp() }
            }
        }
    }
}
