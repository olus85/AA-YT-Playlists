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
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5 MB max

    @Volatile
    private var logFile: File? = null
    @Volatile
    private var prefs: SharedPreferences? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.GERMANY)

    @Volatile
    private var _isEnabled: Boolean = true

    // PII scrubbing patterns
    private val tokenPattern = Regex("(?i)(token|auth|bearer|api[_-]?key|secret|password|credential)\\s*[=:]\\s*['\"]?[\\w\\-]+['\"]?", RegexOption.IGNORE_CASE)
    private val urlWithCredentialsPattern = Regex("(?i)(https?://[^/\\s]*:[^/\\s]*@[^/\\s]+)", RegexOption.IGNORE_CASE)
    private val uuidPattern = Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)

    var isEnabled: Boolean
        get() = _isEnabled
        set(value) {
            _isEnabled = value
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
        
        _isEnabled = prefs?.getBoolean(KEY_ENABLED, true) ?: true
        forceLog(TAG, "AALogger initialized. Debug mode: $_isEnabled")
    }

    /**
     * Scrubs potentially sensitive data (tokens, URLs with credentials, UUIDs) from messages.
     */
    private fun scrubPII(message: String): String {
        return message
            .replace(tokenPattern, "$1=[REDACTED]")
            .replace(urlWithCredentialsPattern, "[REDACTED_URL]")
            .replace(uuidPattern) { "[USER_ID]" }
    }

    fun log(tag: String, message: String) {
        Log.d(tag, message)
        if (!_isEnabled) return
        writeToFile("D", tag, scrubPII(message))
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        // Errors are always logged
        val stackTrace = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
        writeToFile("E", tag, scrubPII("$message$stackTrace"))
    }

    fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
        if (!_isEnabled) return
        writeToFile("I", tag, scrubPII(message))
    }

    /**
     * Logs critical lifecycle events regardless of the enabled toggle.
     */
    fun forceLog(tag: String, message: String) {
        Log.i(tag, message)
        writeToFile("F", tag, scrubPII(message))
    }

    @Synchronized
    private fun writeToFile(level: String, tag: String, message: String) {
        try {
            val file = logFile ?: return
            // Clear and restart when file reaches 5 MB limit (with rotation)
            if (file.exists() && file.length() > MAX_LOG_SIZE) {
                try {
                    val backupFile = File(file.parent, "$LOG_FILE_NAME.bak")
                    if (backupFile.exists()) {
                        backupFile.delete()
                    }
                    file.renameTo(backupFile)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to rotate log file", e)
                }
                file.writeText("")
                val timestamp = dateFormat.format(Date())
                file.appendText("[$timestamp] F/$TAG: === LOG ROTATED (5 MB limit reached) ===\n")
            }
            val timestamp = dateFormat.format(Date())
            file.appendText("[$timestamp] $level/$tag: $message\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    fun getLogs(): String {
        return try {
            val file = logFile ?: return "Keine Logs vorhanden."
            if (!file.exists()) return "Keine Logs vorhanden."
            // Prevent OOM: only load up to last 100 KB of logs
            val limit = 100 * 1024
            if (file.length() > limit) {
                val raf = java.io.RandomAccessFile(file, "r")
                raf.seek(file.length() - limit)
                val bytes = ByteArray(limit)
                val read = raf.read(bytes)
                raf.close()
                val rawText = String(bytes, 0, read)
                val firstNewLine = rawText.indexOf('\n')
                if (firstNewLine != -1) {
                    "... [Restliche Logs gekürzt - zeige letzte 100KB] ...\n" + rawText.substring(firstNewLine + 1)
                } else {
                    rawText
                }
            } else {
                file.readText()
            }
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

