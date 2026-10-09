package com.rslnabk.aivideotest

import android.os.Bundle
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.runner.AndroidJUnitRunner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/** Activity rules must never authenticate against the live server while running fixtures. */
class AiVideoTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        val listeners = listOfNotNull(arguments.getString("listener"), DemoFixtureListener::class.java.name)
        arguments.putString("listener", listeners.joinToString(","))
        super.onCreate(arguments)
    }
}
class DemoFixtureListener : RunListener() {
    override fun testStarted(description: Description) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT <= 28) ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("pm grant ${instrumentation.targetContext.packageName} android.permission.WRITE_EXTERNAL_STORAGE")
        ).use { it.readBytes() }
        instrumentation.targetContext
            .getSharedPreferences("backend_source_v1", 0).edit().putString("source", "DEMO").commit()
    }
}
