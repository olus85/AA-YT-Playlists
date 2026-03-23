package app.olus.ytmusic.autolauncher.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Globaler Logger für Android Auto Diagnostik.
 * Schreibt Logs in eine lokale Datei im Cache-Verzeichnis.
 * Kann über einen UI-Schalter aktiviert/deaktiviert werden.
 * Kritische Lifecycle-Events werden IMMER geloggt (forceLog).
 */
object AALogger {

    private const val TAG = "AALogger"
    private const val PREFS_NAME = "aa_debug_prefs"
    private const val KEY_ENABLED = "debug_enabled"
    private const val KEY_FIRST_RUN = "first_run"
    private const val LOG_FILE_NAME = "aa_debug.log"
    private const val MAX_LOG_SIZE = 512 * 1024 // 512 KB max

    private var logFile: File? = null
    private var prefs: SharedPreferences? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.GERMANY)

    var isEnabled: Boolean
        get() = prefs?.getBoolean(KEY_ENABLED, true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        }

    /**
     * Muss einmal beim App-Start aufgerufen werden.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        logFile = File(context.cacheDir, LOG_FILE_NAME)
        
        // Default to enabled on first run
        val isFirstRun = prefs?.getBoolean(KEY_FIRST_RUN, true) ?: true
        if (isFirstRun) {
            prefs?.edit()
                ?.putBoolean(KEY_ENABLED, true)
                ?.putBoolean(KEY_FIRST_RUN, false)
                ?.apply()
        }
        
        forceLog(TAG, "AALogger initialized. Debug mode: $isEnabled")
    }

    fun log(tag: String, message: String) {
        Log.d(tag, message)
        if (!isEnabled) return
        writeToFile("D", tag, message)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        // Errors are always logged
        val stackTrace = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
        writeToFile("E", tag, "$message$stackTrace")
    }

    fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
        if (!isEnabled) return
        writeToFile("I", tag, message)
    }

    /**
     * Logs critical lifecycle events regardless of the enabled toggle.
     */
    fun forceLog(tag: String, message: String) {
        Log.i(tag, message)
        writeToFile("F", tag, message)
    }

    @Synchronized
    private fun writeToFile(level: String, tag: String, message: String) {
        try {
            val file = logFile ?: return
            // Rotate if too large
            if (file.exists() && file.length() > MAX_LOG_SIZE) {
                val lines = file.readLines()
                val halfLines = lines.drop(lines.size / 2)
                file.writeText(halfLines.joinToString("\n") + "\n")
            }
            val timestamp = dateFormat.format(Date())
            file.appendText("[$timestamp] $level/$tag: $message\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    fun getLogs(): String {
        return try {
            logFile?.takeIf { it.exists() }?.readText() ?: "Keine Logs vorhanden."
        } catch (e: Exception) {
            "Fehler beim Lesen: ${e.message}"
        }
    }

    fun clearLogs() {
        try {
            logFile?.takeIf { it.exists() }?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }

    fun getLogFile(): File? {
        return logFile?.takeIf { it.exists() }
    }
}

