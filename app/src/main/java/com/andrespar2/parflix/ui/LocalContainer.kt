package com.andrespar2.parflix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.andrespar2.parflix.AppContainer
import com.andrespar2.parflix.ParFlixApp

/**
 * Acceso al contenedor de dependencias desde la interfaz.
 *
 * La aplicación es la única propietaria del contenedor; las pantallas nunca
 * crean dependencias por su cuenta, evitando duplicar conexiones o bases de
 * datos.
 */
@Composable
fun rememberContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as ParFlixApp).container }
}
