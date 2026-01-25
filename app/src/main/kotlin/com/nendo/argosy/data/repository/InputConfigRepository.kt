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

    suspend fun getMappingForController(controllerId: String): Map<Int, Int>? =
        withContext(Dispatchers.IO) {
            val entity = controllerMappingDao.getByControllerId(controllerId) ?: return@withContext null
            parseMappingJson(entity.mappingJson)
        }

    suspend fun getMappingForDevice(device: InputDevice): Map<Int, Int>? =
        getMappingForController(getControllerId(device))

    suspend fun getOrCreateMappingForDevice(device: InputDevice): Map<Int, Int> =
        withContext(Dispatchers.IO) {
            val controllerId = getControllerId(device)
            val existing = controllerMappingDao.getByControllerId(controllerId)

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
        isAutoDetected: Boolean
    ) = withContext(Dispatchers.IO) {
        val controllerId = getControllerId(device)
        val mappingJson = encodeMappingJson(mapping)

        val existing = controllerMappingDao.getByControllerId(controllerId)
        if (existing != null) {
            controllerMappingDao.updateMapping(
                controllerId = controllerId,
                mappingJson = mappingJson,
                presetName = presetName,
                isAutoDetected = isAutoDetected,
                updatedAt = Instant.now()
            )
        } else {
            controllerMappingDao.upsert(
                ControllerMappingEntity(
                    controllerId = controllerId,
                    controllerName = device.name ?: "Unknown",
                    vendorId = device.vendorId,
                    productId = device.productId,
                    mappingJson = mappingJson,
                    presetName = presetName,
                    isAutoDetected = isAutoDetected,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            )
        }
        Logger.info(TAG, "Saved mapping for ${device.name} (preset: $presetName)")
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

    companion object {
        const val MAX_PLAYERS = 4
    }
}
