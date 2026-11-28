package com.terpenesalad.nugofwar

import android.content.SharedPreferences
import java.io.File
import java.util.Properties

/** SharedPreferences saved to a small properties file (settings, traced walls, best wave). */
class FilePrefs(private val file: File) : SharedPreferences {
    private val props = Properties()

    init {
        try { if (file.exists()) file.inputStream().use { props.load(it) } } catch (_: Exception) {}
    }

    @Synchronized private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "Nug of War") }
        } catch (e: Exception) { System.err.println("Could not save settings: $e") }
    }

    override fun getBoolean(key: String, defValue: Boolean) = props.getProperty(key)?.toBooleanStrictOrNull() ?: defValue
    override fun getInt(key: String, defValue: Int) = props.getProperty(key)?.toIntOrNull() ?: defValue
    override fun getLong(key: String, defValue: Long) = props.getProperty(key)?.toLongOrNull() ?: defValue
    override fun getFloat(key: String, defValue: Float) = props.getProperty(key)?.toFloatOrNull() ?: defValue
    override fun getString(key: String, defValue: String?): String? = props.getProperty(key) ?: defValue
    override fun contains(key: String) = props.containsKey(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val puts = LinkedHashMap<String, String?>()
        private var clear = false
        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value.toString() }
        override fun putInt(key: String, value: Int) = apply { puts[key] = value.toString() }
        override fun putLong(key: String, value: Long) = apply { puts[key] = value.toString() }
        override fun putFloat(key: String, value: Float) = apply { puts[key] = value.toString() }
        override fun putString(key: String, value: String?) = apply { puts[key] = value }
        override fun remove(key: String) = apply { puts[key] = null }
        override fun clear() = apply { clear = true }
        override fun apply() { commit() }
        override fun commit(): Boolean {
            synchronized(this@FilePrefs) {
                if (clear) props.clear()
                for ((k, v) in puts) if (v == null) props.remove(k) else props.setProperty(k, v)
            }
            save()
            return true
        }
    }
}
