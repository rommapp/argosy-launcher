package com.nendo.argosy.libretro

import android.util.Log
import android.view.InputDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.repository.InputConfigRepository
import com.nendo.argosy.data.repository.InputSource
import com.nendo.argosy.data.repository.MappingPlatforms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InputConfigCoordinator(
    val inputConfigRepository: InputConfigRepository,
    private val portResolver: ControllerPortResolver,
    private val inputMapper: ControllerInputMapper,
    private val platformSlug: String,
    private val limitHotkeysToPlayer1: Boolean,
    private val scope: CoroutineScope
) {
    lateinit var hotkeyManager: HotkeyManager
        private set

    var controllerOrderCount by mutableIntStateOf(0)
        private set
    var controllerOrderList by mutableStateOf<List<ControllerOrderEntity>>(emptyList())
        private set
    var hotkeyList by mutableStateOf<List<HotkeyEntity>>(emptyList())
        private set

    fun initialize() {
        hotkeyManager = HotkeyManager(inputConfigRepository)

        scope.launch {
            inputConfigRepository.clearAutoDetectedMappings()

            val controllerOrder = inputConfigRepository.getControllerOrder()
            controllerOrderList = controllerOrder
            controllerOrderCount = controllerOrder.size
            portResolver.setControllerOrder(controllerOrder)

            val mappingPlatformId = MappingPlatforms.dbPlatformIdForSlug(platformSlug)
            val mappings = mutableMapOf<String, Map<InputSource, Int>>()
            for (controller in inputConfigRepository.getConnectedControllers()) {
                val mapping = inputConfigRepository.getOrCreateExtendedMappingForDevice(
                    InputDevice.getDevice(controller.deviceId)!!,
                    mappingPlatformId
                )
                mappings[controller.controllerId] = mapping
            }
            inputMapper.setExtendedMappings(mappings)
            inputMapper.setPortResolver { device -> portResolver.getPort(device) }

            val mappedButtons = mappings.mapValues { (_, mapping) ->
                mapping.keys
                    .filterIsInstance<InputSource.Button>()
                    .map { it.keyCode }
                    .toSet()
            }

            inputConfigRepository.initializeDefaultHotkeys()
            val hotkeys = inputConfigRepository.getEnabledHotkeys()
            hotkeyManager.setHotkeys(hotkeys)
            hotkeyList = inputConfigRepository.getHotkeys()
            hotkeyManager.setControllerMappedButtons(mappedButtons)
            hotkeyManager.setLimitToPlayer1(limitHotkeysToPlayer1)

            if (controllerOrder.isNotEmpty()) {
                hotkeyManager.setPlayer1ControllerId(controllerOrder.first().controllerId)
            }

            Log.d(TAG, "Input config loaded: ${controllerOrder.size} port assignments, ${mappings.size} mappings, ${hotkeys.size} hotkeys")
        }
    }

    suspend fun refreshControllerOrder() {
        val order = inputConfigRepository.getControllerOrder()
        controllerOrderList = order
        controllerOrderCount = order.size
        portResolver.setControllerOrder(order)
        if (order.isNotEmpty()) {
            hotkeyManager.setPlayer1ControllerId(order.first().controllerId)
        }
    }

    suspend fun refreshInputMappings() {
        val mappingPlatformId = MappingPlatforms.dbPlatformIdForSlug(platformSlug)
        val mappings = mutableMapOf<String, Map<InputSource, Int>>()
        for (controller in inputConfigRepository.getConnectedControllers()) {
            val device = InputDevice.getDevice(controller.deviceId) ?: continue
            val mapping = inputConfigRepository.getOrCreateExtendedMappingForDevice(device, mappingPlatformId)
            mappings[controller.controllerId] = mapping
        }
        inputMapper.setExtendedMappings(mappings)

        val mappedButtons = mappings.mapValues { (_, mapping) ->
            mapping.keys
                .filterIsInstance<InputSource.Button>()
                .map { it.keyCode }
                .toSet()
        }
        hotkeyManager.setControllerMappedButtons(mappedButtons)
    }

    suspend fun refreshHotkeys() {
        hotkeyList = inputConfigRepository.getHotkeys()
        val enabledHotkeys = inputConfigRepository.getEnabledHotkeys()
        hotkeyManager.setHotkeys(enabledHotkeys)
    }

    companion object {
        private const val TAG = "InputConfigCoordinator"
    }
}
