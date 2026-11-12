package com.appresso.app

import android.app.Application

class AppressoApp : Application() {

    /** La URL del backend sale de `BuildConfig.API_BASE_URL` (ver android/README.md). */
    val contenedor: ContenedorApp by lazy { ContenedorApp(BuildConfig.API_BASE_URL) }
}
