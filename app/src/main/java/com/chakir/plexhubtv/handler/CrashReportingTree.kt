package com.chakir.plexhubtv.handler

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

class CrashReportingTree(
    private val crashlytics: FirebaseCrashlytics
) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.WARN) return

        crashlytics.log(if (tag != null) "[$tag] $message" else message)

        if (t != null) {
            crashlytics.recordException(t)
        }
    }
}
