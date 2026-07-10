package com.androidkris.ide

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt's root component is generated from here.
 *
 * Future phases will warm up the toolchain manager (Fase 2+) and Gradle
 * connection cache (Fase 3+) from here on a background dispatcher.
 */
@HiltAndroidApp
class AndroidKrisApp : Application()
