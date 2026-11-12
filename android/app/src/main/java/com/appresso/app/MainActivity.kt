package com.appresso.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.appresso.app.ui.AppressoRaiz
import com.appresso.app.ui.theme.AppressoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barra de estado sobre fondo espresso (íconos claros) y barra inferior sobre crema (íconos oscuros).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val contenedor = (application as AppressoApp).contenedor
        setContent {
            AppressoTheme {
                AppressoRaiz(contenedor)
            }
        }
    }
}
