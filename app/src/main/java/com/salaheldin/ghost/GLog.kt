package com.salaheldin.ghost

import android.util.Log
import com.salaheldin.ghost.BuildConfig

/** Debug-only logging. Never pass message content, sender names, or conversation titles. */
object GLog {
    fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }
    fun w(tag: String, message: String) = Log.w(tag, message)
    fun e(tag: String, message: String, t: Throwable? = null) = Log.e(tag, message, t)
}