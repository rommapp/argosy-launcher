package com.nendo.argosy.data.repository

import android.view.InputDevice
import android.view.KeyEvent
import com.nendo.argosy.data.local.dao.ControllerMappingDao
import com.nendo.argosy.data.local.dao.ControllerOrderDao
import com.nendo.argosy.data.local.dao.HotkeyDao
import com.nendo.argosy.data.local.entity.ControllerMappingEntity
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.ui.input.ControllerDetector
import com.nendo.argosy.ui.input.DetectedLayout
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InputConfigRepository"
private const val MAPPING_FORMAT_VERSION = 2

data class ControllerInfo(
    val deviceId: Int,
    val controllerId: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val detectedLayout: DetectedLayout?
)

@Singleton
class InputConfigRepository @Inject constructor(
    private val controllerOrderDao: ControllerOrderDao,
    private val controllerMappingDao: ControllerMappingDao,
    private val hotkeyDao: HotkeyDao
) {
    fun observeControllerOrder(): Flow<List<ControllerOrderEntity>> =
        controllerOrderDao.observeAll()

    suspend fun getControllerOrder(): List<ControllerOrderEntity> =
        controllerOrderDao.getAll()

    suspend fun assignControllerToPort(port: Int, device: InputDevice) = withContext(Dispatchers.IO) {
        val controllerId = getControllerId(device)
        Logger.info(TAG, "Assigning ${device.name} to port $port")

        controllerOrderDao.deleteByControllerId(controllerId)

        controllerOrderDao.upsert(
            ControllerOrderEntity(
                port = port,
                controllerId = controllerId,
                controllerName = device.name ?: "Unknown Controller",
                assignedAt = Instant.now()
            )
        )
    }

    suspend fun clearControllerOrder() = withContext(Dispatchers.IO) {
        Logger.info(TAG, "Clearing all controller assignments")
        controllerOrderDao.deleteAll()
    }

    suspend fun getPortForController(controllerId: String): Int? =
        controllerOrderDao.getByControllerId(controllerId)?.port

    suspend fun getPortForDevice(device: InputDevice): Int? =
        getPortForController(getControllerId(device))

    fun observeControllerMappings(): Flow<List<ControllerMappingEntity>> =
        controllerMappingDao.observeAll()

    private suspend fun findMapping(controllerId: String, platformId: String?): ControllerMappingEntity? {
        if (platformId != null) {
            controllerMappingDao.getByControllerIdAndPlatform(controllerId, platformId)?.let { return it }
        }
        return controllerMappingDao.getByControllerIdGlobal(controllerId)
    }

    private suspend fun findExactMapping(controllerId: String, platformId: String?): ControllerMappingEntity? {
        return if (platformId != null) {
            controllerMappingDao.getByControllerIdAndPlatform(controllerId, platformId)
        } else {
            controllerMappingDao.getByControllerIdGlobal(controllerId)
        }
    }

    private suspend fun updateOrInsertMapping(
        controllerId: String,
        platformId: String?,
        controllerName: String,
        vendorId: Int,
        productId: Int,
        mappingJson: String,
        presetName: String?,
        isAutoDetected: Boolean
    ) {
        val existing = findExactMapping(controllerId, platformId)
        if (existing != null) {
            if (platformId != null) {
                controllerMappingDao.updateMappingForPlatform(
                    controllerId = controllerId,
                    platformId = platformId,
                    mappingJson = mappingJson,
                    presetName = presetName,
                    isAutoDetected = isAutoDetected,
                    updatedAt = Instant.now()
                )
            } else {
                controllerMappingDao.updateMappingGlobal(
                    controllerId = controllerId,
                    mappingJson = mappingJson,
                    presetName = presetName,
                    isAutoDetected = isAutoDetected,
                    updatedAt = Instant.now()
                )
            }
        } else {
            controllerMappingDao.upsert(
                ControllerMappingEntity(
                    controllerId = controllerId,
                    controllerName = controllerName,
                    vendorId = vendorId,
                    productId = productId,
                    platformId = platformId,
                    mappingJson = mappingJson,
                    presetName = presetName,
                    isAutoDetected = isAutoDetected,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            )
        }
    }

    suspend fun getMappingForController(controllerId: String, platformId: String? = null): Map<Int, Int>? =
        withContext(Dispatchers.IO) {
            val entity = findMapping(controllerId, platformId) ?: return@withContext null
            parseMappingJson(entity.mappingJson)
        }

    suspend fun getMappingForDevice(device: InputDevice, platformId: String? = null): Map<Int, Int>? =
        getMappingForController(getControllerId(device), platformId)

    suspend fun getOrCreateMappingForDevice(device: InputDevice, platformId: String? = null): Map<Int, Int> =
        withContext(Dispatchers.IO) {
            val controllerId = getControllerId(device)
            val existing = findMapping(controllerId, platformId)

            if (existing != null) {
                return@withContext parseMappingJson(existing.mappingJson)
            }

            val detectionResult = ControllerDetector.detectFromDevice(device)
            val layout = detectionResult.layout ?: DetectedLayout.XBOX
            val defaultMapping = InputPresets.getDefaultMappingForLayout(layout)

            saveMapping(
                device = device,
                mapping = defaultMapping,
                presetName = layout.name,
                isAutoDetected = true
            )

            defaultMapping
        }

    suspend fun saveMapping(
        device: InputDevice,
        mapping: Map<Int, Int>,
        presetName: String?,
        isAutoDetected: Boolean,
        platformId: String? = null
    ) = withContext(Dispatchers.IO) {
        val controllerId = getControllerId(device)
        val mappingJson = encodeMappingJson(mapping)

        updateOrInsertMapping(
            controllerId = controllerId,
            platformId = platformId,
            controllerName = device.name ?: "Unknown",
            vendorId = device.vendorId,
            productId = device.productId,
            mappingJson = mappingJson,
            presetName = presetName,
            isAutoDetected = isAutoDetected
        )
        Logger.info(TAG, "Saved mapping for ${device.name} (platform: $platformId, preset: $presetName)")
    }

    suspend fun applyPreset(device: InputDevice, presetName: String): Boolean =
        withContext(Dispatchers.IO) {
            val preset = InputPresets.getPresetByName(presetName) ?: return@withContext false
            saveMapping(
                device = device,
                mapping = preset.mapping,
                presetName = presetName,
                isAutoDetected = false
            )
            true
        }

    suspend fun clearMappingForController(controllerId: String) = withContext(Dispatchers.IO) {
        controllerMappingDao.deleteByControllerId(controllerId)
    }

    suspend fun clearAutoDetectedMappings() = withContext(Dispatchers.IO) {
        controllerMappingDao.deleteAllAutoDetected()
    }

    suspend fun getExtendedMappingForController(controllerId: String, platformId: String? = null): Map<InputSource, Int>? =
        withContext(Dispatchers.IO) {
            val entity = findMapping(controllerId, platformId) ?: return@withContext null
            parseExtendedMappingJson(entity.mappingJson)
        }

    suspend fun getExtendedMappingForDevice(device: InputDevice, platformId: String? = null): Map<InputSource, Int>? =
        getExtendedMappingForController(getControllerId(device), platformId)

    suspend fun getOrCreateExtendedMappingForDevice(device: InputDevice, platformId: String? = null): Map<InputSource, Int> =
        withContext(Dispatchers.IO) {
            val controllerId = getControllerId(device)
            val existing = findMapping(controllerId, platformId)

            if (existing != null) {
                return@withContext parseExtendedMappingJson(existing.mappingJson)
            }

            val detectionResult = ControllerDetector.detectFromDevice(device)
            val layout = detectionResult.layout ?: DetectedLayout.XBOX
            val defaultMapping = InputPresets.getDefaultMappingForLayout(layout)
            val extendedMapping: Map<InputSource, Int> = defaultMapping.map { (keyCode, retroButton) ->
                InputSource.Button(keyCode) as InputSource to retroButton
            }.toMap()

            saveExtendedMapping(
                device = device,
                mapping = extendedMapping,
                presetName = layout.name,
                isAutoDetected = true
            )

            extendedMapping
        }

    suspend fun saveExtendedMapping(
        device: InputDevice,
        mapping: Map<InputSource, Int>,
        presetName: String?,
        isAutoDetected: Boolean,
        platformId: String? = null
    ) = withContext(Dispatchers.IO) {
        val controllerId = getControllerId(device)
        val mappingJson = encodeExtendedMappingJson(mapping)

        updateOrInsertMapping(
            controllerId = controllerId,
            platformId = platformId,
            controllerName = device.name ?: "Unknown",
            vendorId = device.vendorId,
            productId = device.productId,
            mappingJson = mappingJson,
            presetName = presetName,
            isAutoDetected = isAutoDetected
        )
        Logger.info(TAG, "Saved extended mapping for ${device.name} (platform: $platformId, preset: $presetName)")
    }

    fun groupMappingByRetroButton(mapping: Map<InputSource, Int>): Map<Int, List<InputSource>> {
        return mapping.entries
            .groupBy({ it.value }, { it.key })
    }

    fun observeHotkeys(): Flow<List<HotkeyEntity>> = hotkeyDao.observeAll()

    fun observeEnabledHotkeys(): Flow<List<HotkeyEntity>> = hotkeyDao.observeEnabled()

    suspend fun getHotkeys(): List<HotkeyEntity> = hotkeyDao.getAll()

    suspend fun getEnabledHotkeys(): List<HotkeyEntity> = hotkeyDao.getEnabled()

    suspend fun getHotkeyForAction(action: HotkeyAction): HotkeyEntity? =
        hotkeyDao.getByAction(action)

    suspend fun setHotkey(
        action: HotkeyAction,
        keyCodes: List<Int>,
        controllerId: String? = null,
        enabled: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val comboJson = encodeComboJson(keyCodes)

        val existing = hotkeyDao.getByActionAndController(action, controllerId)
        if (existing != null) {
            hotkeyDao.updateCombo(
                action = action,
                controllerId = controllerId,
                buttonComboJson = comboJson,
                isEnabled = enabled
            )
        } else {
            hotkeyDao.upsert(
                HotkeyEntity(
                    action = action,
                    buttonComboJson = comboJson,
                    controllerId = controllerId,
                    isEnabled = enabled
                )
            )
        }
        Logger.info(TAG, "Set hotkey for $action: ${keyCodes.map { KeyEvent.keyCodeToString(it) }}")
    }

    suspend fun enableHotkey(action: HotkeyAction, enabled: Boolean) = withContext(Dispatchers.IO) {
        val existing = hotkeyDao.getByAction(action) ?: return@withContext
        hotkeyDao.updateCombo(
            action = action,
            controllerId = existing.controllerId,
            buttonComboJson = existing.buttonComboJson,
            isEnabled = enabled
        )
    }

    suspend fun deleteHotkey(action: HotkeyAction) = withContext(Dispatchers.IO) {
        hotkeyDao.deleteByAction(action)
    }

    suspend fun clearAllHotkeys() = withContext(Dispatchers.IO) {
        hotkeyDao.deleteAll()
    }

    fun parseHotkeyCombo(entity: HotkeyEntity): List<Int> {
        return parseComboJson(entity.buttonComboJson)
    }

    suspend fun initializeDefaultHotkeys() = withContext(Dispatchers.IO) {
        val existingHotkeys = hotkeyDao.getAll()
        if (existingHotkeys.isNotEmpty()) return@withContext

        Logger.info(TAG, "Initializing default hotkeys")

        setHotkey(
            action = HotkeyAction.IN_GAME_MENU,
            keyCodes = listOf(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT)
        )

        setHotkey(
            action = HotkeyAction.FAST_FORWARD,
            keyCodes = listOf(KeyEvent.KEYCODE_BUTTON_R2)
        )

        setHotkey(
            action = HotkeyAction.REWIND,
            keyCodes = listOf(KeyEvent.KEYCODE_BUTTON_L2)
        )
    }

    fun getConnectedControllers(): List<ControllerInfo> {
        val controllers = mutableListOf<ControllerInfo>()
        val deviceIds = InputDevice.getDeviceIds()

        for (deviceId in deviceIds) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            val sources = device.sources

            if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            ) {
                val detection = ControllerDetector.detectFromDevice(device)
                controllers.add(
                    ControllerInfo(
                        deviceId = deviceId,
                        controllerId = getControllerId(device),
                        name = device.name ?: "Unknown",
                        vendorId = device.vendorId,
                        productId = device.productId,
                        detectedLayout = detection.layout
                    )
                )
            }
        }

        return controllers
    }

    private fun getControllerId(device: InputDevice): String {
        return "${device.vendorId}:${device.productId}:${device.descriptor}"
    }

    private fun parseMappingJson(jsonStr: String): Map<Int, Int> {
        return try {
            val result = mutableMapOf<Int, Int>()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val androidKeyCode = obj.getInt("androidKeyCode")
                val retroButton = obj.getInt("retroButton")
                result[androidKeyCode] = retroButton
            }
            result
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse mapping JSON: ${e.message}")
            emptyMap()
        }
    }

    private fun encodeMappingJson(mapping: Map<Int, Int>): String {
        val jsonArray = JSONArray()
        for ((android, retro) in mapping) {
            val obj = JSONObject()
            obj.put("androidKeyCode", android)
            obj.put("retroButton", retro)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun parseComboJson(jsonStr: String): List<Int> {
        return try {
            val result = mutableListOf<Int>()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                result.add(jsonArray.getInt(i))
            }
            result
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse combo JSON: ${e.message}")
            emptyList()
        }
    }

    private fun encodeComboJson(keyCodes: List<Int>): String {
        val jsonArray = JSONArray()
        for (keyCode in keyCodes) {
            jsonArray.put(keyCode)
        }
        return jsonArray.toString()
    }

    private fun parseExtendedMappingJson(jsonStr: String): Map<InputSource, Int> {
        return try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[")) {
                parseLegacyMappingToExtended(trimmed)
            } else {
                parseVersionedMappingJson(trimmed)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse extended mapping JSON: ${e.message}")
            emptyMap()
        }
    }

    private fun parseLegacyMappingToExtended(jsonStr: String): Map<InputSource, Int> {
        val result = mutableMapOf<InputSource, Int>()
        val jsonArray = JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val androidKeyCode = obj.getInt("androidKeyCode")
            val retroButton = obj.getInt("retroButton")
            result[InputSource.Button(androidKeyCode)] = retroButton
        }
        return result
    }

    private fun parseVersionedMappingJson(jsonStr: String): Map<InputSource, Int> {
        val result = mutableMapOf<InputSource, Int>()
        val root = JSONObject(jsonStr)
        val mappings = root.getJSONArray("mappings")

        for (i in 0 until mappings.length()) {
            val obj = mappings.getJSONObject(i)
            val retroButton = obj.getInt("retroButton")
            val type = obj.getString("type")

            val source = when (type) {
                "button" -> InputSource.Button(obj.getInt("keyCode"))
                "analog" -> InputSource.AnalogDirection(
                    axis = obj.getInt("axis"),
                    positive = obj.getBoolean("positive")
                )
                else -> continue
            }
            result[source] = retroButton
        }
        return result
    }

    private fun encodeExtendedMappingJson(mapping: Map<InputSource, Int>): String {
        val root = JSONObject()
        root.put("version", MAPPING_FORMAT_VERSION)

        val mappingsArray = JSONArray()
        for ((source, retroButton) in mapping) {
            val obj = JSONObject()
            obj.put("retroButton", retroButton)
            when (source) {
                is InputSource.Button -> {
                    obj.put("type", "button")
                    obj.put("keyCode", source.keyCode)
                }
                is InputSource.AnalogDirection -> {
                    obj.put("type", "analog")
                    obj.put("axis", source.axis)
                    obj.put("positive", source.positive)
                }
            }
            mappingsArray.put(obj)
        }
        root.put("mappings", mappingsArray)
        return root.toString()
    }

    fun extendedMappingToLegacy(mapping: Map<InputSource, Int>): Map<Int, Int> {
        return mapping.entries
            .filter { it.key is InputSource.Button }
            .associate { (it.key as InputSource.Button).keyCode to it.value }
    }

    companion object {
        const val MAX_PLAYERS = 4
    }
}
