package com.andrespar2.parflix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.andrespar2.parflix.ui.navigation.ParFlixNavHost
import com.andrespar2.parflix.ui.theme.ParFlixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParFlixTheme {
                ParFlixNavHost()
            }
        }
    }
}
