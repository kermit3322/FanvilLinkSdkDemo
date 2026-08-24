package com.fanvil.link.sdk.utils

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

class FvlLogger private constructor(
    private val originalTag: String,
    private val instanceLevel: Int = DEFAULT
) {
    companion object {
        // ============ 日志级别常量 ============
        const val DEFAULT = 0
        const val TRACE = 1
        const val VERBOSE = 2
        const val DEBUG = 3
        const val INFO = 4
        const val WARN = 5
        const val ERROR = 6
        const val ASSERT = 7
        const val SILENT = 8
        private const val LOG_MARK = "Fvl"

        @Volatile
        private var globalLevel = DEBUG

        private val listeners = CopyOnWriteArrayList<LogLevelChangedListener>()

        /** Debug 打 DEBUG+；Release 默认 WARN+。完全关闭：`setGlobalLevel(SILENT)` */
        fun applyBuildType(debuggable: Boolean) {
            setGlobalLevel(if (debuggable) DEBUG else WARN)
        }

        fun setGlobalLevel(level: Int) {
            if (level == globalLevel) return
            globalLevel = level
            Log.i("$LOG_MARK/FvlLogger", "Global level set to $level")
            listeners.forEach { it.onLogLevelChanged(level) }
        }

        fun getGlobalLevel() = globalLevel

        fun addLevelChangedListener(listener: LogLevelChangedListener) {
            listeners.add(listener)
        }

        fun removeLevelChangedListener(listener: LogLevelChangedListener) {
            listeners.remove(listener)
        }

        @JvmStatic
        fun getLogger(tag: String, level: Int = DEFAULT): FvlLogger {
            return FvlLogger(tag, level)
        }

        fun setLoggerTraceOn() {
            setGlobalLevel(TRACE)
        }
    }

    interface LogLevelChangedListener {
        fun onLogLevelChanged(level: Int)
    }

    // ---------- 核心：生成带 Fvl 标记的最终 Tag ----------
    private fun getFinalTag(): String {
        return "$LOG_MARK/$originalTag"
    }

    private fun effectiveLevel(): Int {
        return if (instanceLevel > DEFAULT) instanceLevel else globalLevel
    }

    private fun isLoggable(level: Int): Boolean {
        return level >= effectiveLevel() && globalLevel != SILENT
    }

    // ---------- 无异常日志打印 ----------
    private fun log(level: Int, msg: String) {
        if (!isLoggable(level)) return

        // 如果消息也需要加 [Fvl] 前缀，可以在这里统一添加：
        // val finalMsg = "[Fvl] $msg"
        // 但更推荐只改 Tag，消息保持干净，这里按推荐方案只改 Tag
        val finalTag = getFinalTag()

        when (level) {
            VERBOSE -> Log.v(finalTag, msg)
            DEBUG -> Log.d(finalTag, msg)
            INFO -> Log.i(finalTag, msg)
            WARN -> Log.w(finalTag, msg)
            ERROR, ASSERT -> Log.e(finalTag, msg)
            else -> Log.v(finalTag, msg)
        }
    }

    // ---------- 带异常日志打印 ----------
    private fun log(level: Int, msg: String, throwable: Throwable) {
        if (!isLoggable(level)) return

        val finalTag = getFinalTag()

        when (level) {
            ERROR, ASSERT -> Log.e(finalTag, msg, throwable)
            WARN -> Log.w(finalTag, msg, throwable)
            else -> Log.d(finalTag, "$msg\n${throwable.stackTraceToString()}")
        }
    }

    // ---------- 对外公开 API ----------
    fun v(msg: String) = log(VERBOSE, msg)
    fun d(msg: String) = log(DEBUG, msg)
    fun i(msg: String) = log(INFO, msg)
    fun w(msg: String) = log(WARN, msg)
    fun w(msg: String, e: Throwable) = log(WARN, msg, e)
    fun e(msg: String) = log(ERROR, msg)
    fun e(msg: String, e: Throwable) = log(ERROR, msg, e)
    fun a(msg: String) = log(ASSERT, msg)
    fun a(msg: String, e: Throwable) = log(ASSERT, msg, e)
    fun t(msg: String) = log(TRACE, msg)
}