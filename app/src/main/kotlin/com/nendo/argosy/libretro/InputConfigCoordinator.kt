package com.nendo.argosy.libretro

import android.util.Log
import android.view.InputDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.core.input.controllerIdOf
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
    private val scope: CoroutineScope,
    private val onHotkeysResolved: (List<HotkeyEntity>) -> Unit = {}
) {
    lateinit var hotkeyManager: HotkeyManager
        private set

    var controllerOrderCount by mutableIntStateOf(0)
        private set
    var hotkeyList by mutableStateOf<List<HotkeyEntity>>(emptyList())
        private set

    fun initialize(onControllerOrderLoaded: (List<ControllerOrderEntity>) -> Unit = {}) {
        hotkeyManager = HotkeyManager(inputConfigRepository, scope)

        scope.launch {
            inputConfigRepository.clearAutoDetectedMappings()

            val connectedControllers = inputConfigRepository.getConnectedControllers()
            val controllerOrder = inputConfigRepository.getControllerOrder()
            controllerOrderCount = controllerOrder.size
            portResolver.setControllerOrder(
                ControllerSeating.presentSeats(controllerOrder, connectedControllers.map { it.controllerId }.toSet())
            )
            onControllerOrderLoaded(controllerOrder)

            val connectedDevices = connectedControllers.mapNotNull { InputDevice.getDevice(it.deviceId) }
            portResolver.addPortClaimedListener { _, _ ->
                hotkeyManager.setPlayer1ControllerId(portResolver.playerOneControllerId())
            }

            val mappings = mutableMapOf<String, Map<InputSource, Int>>()
            for (device in connectedDevices) {
                val mapping = inputConfigRepository.getOrCreateExtendedMappingForDevice(
                    device,
                    profileIdForDevice(device),
                    gameId
                )
                mappings[controllerIdOf(device)] = mapping
            }
            inputMapper.setExtendedMappings(mappings)
            inputMapper.setPortResolver { device -> portResolver.getPort(device) }

            inputConfigRepository.initializeDefaultHotkeys()
            hotkeyList = inputConfigRepository.getHotkeys()
            val hotkeys = resolveScopedHotkeys(hotkeyList)
            hotkeyManager.setHotkeys(hotkeys)
            onHotkeysResolved(hotkeys)
            hotkeyManager.setPlatformMappedButtons(platformMappedButtons(mappings))
            hotkeyManager.setLimitToPlayer1(limitHotkeysToPlayer1)

            hotkeyManager.setPlayer1ControllerId(portResolver.playerOneControllerId())

            Log.d(TAG, "Input config loaded: ${controllerOrder.size} port assignments, ${mappings.size} mappings, ${hotkeys.size} hotkeys")
        }
    }

    suspend fun refreshControllerOrder() {
        val order = inputConfigRepository.getControllerOrder()
        controllerOrderCount = order.size
        portResolver.setControllerOrder(order)
        hotkeyManager.setPlayer1ControllerId(portResolver.playerOneControllerId())
    }

    fun releaseDisconnectedControllers(): Map<String, Int> {
        val connected = inputConfigRepository.getConnectedControllers()
            .map { it.controllerId }
            .toSet()
        val released = portResolver.releaseDisconnected(connected)
        hotkeyManager.setPlayer1ControllerId(portResolver.playerOneControllerId())
        return released
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
            deviceId = controllerTypeForPort(portResolver.peekPort(device) ?: 0)
        )
        return deviceProfile ?: MappingPlatforms.dbPlatformIdForSlug(platformSlug)
    }

    fun profileForDevice(device: InputDevice): MappingPlatform =
        profileIdForDevice(device)
            ?.let { id -> MappingPlatforms.ALL.firstOrNull { it.id == id } }
            ?: MappingPlatforms.profileForSlug(platformSlug)

    suspend fun refreshHotkeys() {
        hotkeyList = inputConfigRepository.getHotkeys()
        val hotkeys = resolveScopedHotkeys(hotkeyList)
        hotkeyManager.setHotkeys(hotkeys)
        onHotkeysResolved(hotkeys)
    }

    /**
     * Disabled rows go in so a scoped one can suppress the tier below it, and come out after
     * resolving so nothing disabled ever reaches the matcher.
     */
    private fun resolveScopedHotkeys(hotkeys: List<HotkeyEntity>): List<HotkeyEntity> =
        HotkeyScopeResolver.resolve(
            all = hotkeys,
            platformSlug = platformSlug,
            coreId = coreId,
            parseCombo = inputConfigRepository::parseHotkeyCombo
        ).filter { it.isEnabled }

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
