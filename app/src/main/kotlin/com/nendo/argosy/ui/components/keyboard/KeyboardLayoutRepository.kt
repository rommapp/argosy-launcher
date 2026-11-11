package com.nendo.argosy.ui.components.keyboard

import android.content.Context
import com.nendo.argosy.ui.components.keyboard.KeyboardLayout
import com.nendo.argosy.util.Logger
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "KeyboardLayoutRepository"

@Singleton
class KeyboardLayoutRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {
    private val cache = ConcurrentHashMap<String, KeyboardLayout>()

    fun availableIds(): List<String> = LAYOUT_IDS

    suspend fun layout(id: String): KeyboardLayout? {
        if (id !in LAYOUT_IDS) return null
        cache[id]?.let { return it }
        return withContext(Dispatchers.IO) {
            load(id)?.let { cache.putIfAbsent(id, it) ?: it }
        }
    }

    private fun load(id: String): KeyboardLayout? {
        val json = readAsset(id) ?: return null
        return try {
            parseLayout(json, ::readAsset, moshi)
        } catch (e: JsonDataException) {
            Logger.warn(TAG, "Unreadable keyboard layout $id: ${e.message}")
            null
        } catch (e: IOException) {
            Logger.warn(TAG, "Unreadable keyboard layout $id: ${e.message}")
            null
        }
    }

    private fun readAsset(id: String): String? = try {
        context.assets.open("$ASSET_DIR/$id.json").bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        Logger.warn(TAG, "Missing keyboard layout file $id: ${e.message}")
        null
    }

    companion object {
        const val ASSET_DIR = "keyboard"

        val LAYOUT_IDS: List<String> = listOf(
            KeyboardLayout.QWERTY_LAYOUT_ID,
            KeyboardLayout.AZERTY_LAYOUT_ID,
            KeyboardLayout.QWERTZ_LAYOUT_ID,
            KeyboardLayout.JCUKEN_LAYOUT_ID,
            KeyboardLayout.HANGUL_LAYOUT_ID,
            KeyboardLayout.NUMERIC_LAYOUT_ID
        )
    }
}
