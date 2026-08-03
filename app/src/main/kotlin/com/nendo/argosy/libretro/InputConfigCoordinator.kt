package com.nendo.argosy.libretro

import android.util.Log
import android.view.InputDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.repository.CoreDeviceProfiles
import com.nendo.argosy.data.repository.InputConfigRepository
import com.nendo.argosy.data.repository.InputSource
import com.nendo.argosy.data.repository.MappingPlatform
import com.nendo.argosy.data.repository.MappingPlatforms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InputConfigCoordinator(
    val inputConfigRepository: InputConfigRepository,
    private val portResolver: ControllerPortResolver,
    private val inputMapper: ControllerInputMapper,
    private val platformSlug: String,
    private val coreId: String?,
    private var gameId: Long?,
    private val limitHotkeysToPlayer1: Boolean,
    private val controllerTypeForPort: (Int) -> Int?,
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
        hotkeyManager = HotkeyManager(inputConfigRepository, scope)

        scope.launch {
            inputConfigRepository.clearAutoDetectedMappings()

            val controllerOrder = inputConfigRepository.getControllerOrder()
            controllerOrderList = controllerOrder
            controllerOrderCount = controllerOrder.size
            portResolver.setControllerOrder(controllerOrder)

            val mappings = mutableMapOf<String, Map<InputSource, Int>>()
            for (controller in inputConfigRepository.getConnectedControllers()) {
                val device = InputDevice.getDevice(controller.deviceId) ?: continue
                val mapping = inputConfigRepository.getOrCreateExtendedMappingForDevice(
                    device,
                    profileIdForDevice(device),
                    gameId
                )
                mappings[controller.controllerId] = mapping
            }
            inputMapper.setExtendedMappings(mappings)
            inputMapper.setPortResolver { device -> portResolver.getPort(device) }

            inputConfigRepository.initializeDefaultHotkeys()
            val hotkeys = inputConfigRepository.getEnabledHotkeys()
            hotkeyManager.setHotkeys(resolveScopedHotkeys(hotkeys))
            hotkeyList = inputConfigRepository.getHotkeys()
            hotkeyManager.setPlatformMappedButtons(platformMappedButtons(mappings))
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

    fun setGameId(newGameId: Long?) {
        if (gameId == newGameId) return
        gameId = newGameId
        scope.launch { refreshInputMappings() }
    }

    suspend fun refreshInputMappings() {
        val mappings = mutableMapOf<String, Map<InputSource, Int>>()
        for (controller in inputConfigRepository.getConnectedControllers()) {
            val device = InputDevice.getDevice(controller.deviceId) ?: continue
            val mapping = inputConfigRepository.getOrCreateExtendedMappingForDevice(
                device,
                profileIdForDevice(device),
                gameId
            )
            mappings[controller.controllerId] = mapping
        }
        inputMapper.setExtendedMappings(mappings)
        hotkeyManager.setPlatformMappedButtons(platformMappedButtons(mappings))
    }

    /**
     * The profile a physical pad is mapped against. Where the core exposes several port devices the
     * running device decides which console controller the port speaks, so the mapping has to follow
     * that rather than the platform alone; a port with no recorded pairing keeps the platform's.
     */
    fun profileIdForDevice(device: InputDevice): String? {
        val deviceProfile = CoreDeviceProfiles.profileIdFor(
            coreId = coreId,
            platformSlug = platformSlug,
            deviceId = controllerTypeForPort(portResolver.getPort(device))
        )
        return deviceProfile ?: MappingPlatforms.dbPlatformIdForSlug(platformSlug)
    }

    fun profileForDevice(device: InputDevice): MappingPlatform =
        profileIdForDevice(device)
            ?.let { id -> MappingPlatforms.ALL.firstOrNull { it.id == id } }
            ?: MappingPlatforms.profileForSlug(platformSlug)

    suspend fun refreshHotkeys() {
        hotkeyList = inputConfigRepository.getHotkeys()
        val enabledHotkeys = inputConfigRepository.getEnabledHotkeys()
        hotkeyManager.setHotkeys(resolveScopedHotkeys(enabledHotkeys))
    }

    private fun resolveScopedHotkeys(hotkeys: List<HotkeyEntity>): List<HotkeyEntity> =
        HotkeyScopeResolver.resolve(
            all = hotkeys,
            platformSlug = platformSlug,
            coreId = coreId,
            parseCombo = inputConfigRepository::parseHotkeyCombo
        )

    private fun platformMappedButtons(
        mappings: Map<String, Map<InputSource, Int>>
    ): Map<String, Set<Int>> {
        val profilesByController = inputConfigRepository.getConnectedControllers()
            .mapNotNull { controller ->
                InputDevice.getDevice(controller.deviceId)?.let { controller.controllerId to profileForDevice(it) }
            }
            .toMap()
        val fallback = MappingPlatforms.profileForSlug(platformSlug)
        return mappings.mapValues { (controllerId, mapping) ->
            val blockingButtons = (profilesByController[controllerId] ?: fallback).hotkeyBlockingButtons
            mapping
                .filter { (_, retroButton) -> retroButton in blockingButtons }
                .keys
                .filterIsInstance<InputSource.Button>()
                .mapTo(mutableSetOf()) { it.keyCode }
        }
    }

    companion object {
        private const val TAG = "InputConfigCoordinator"
    }
}
