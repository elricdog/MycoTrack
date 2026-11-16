package com.mikotrack

import android.app.Application

/**
 * The application used by the unit tests.
 *
 * The real one initialises MapLibre, whose native library cannot be loaded in a JVM
 * test environment. The tests that need the dependency container build it themselves.
 */
class TestApplication : Application()
