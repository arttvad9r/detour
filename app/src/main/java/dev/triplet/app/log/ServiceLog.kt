package dev.triplet.app.log

import android.util.Log

/** Мост в logcat. Never log secrets. */
object ServiceLog {
    private const val TAG = "Detour"

    fun i(msg: String) = Log.i(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String) = Log.e(TAG, msg)
}
