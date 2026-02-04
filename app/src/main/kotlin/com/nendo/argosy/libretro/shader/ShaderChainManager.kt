package com.nendo.argosy.libretro.shader

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nendo.argosy.ui.screens.settings.ShaderParamDef
import com.nendo.argosy.ui.screens.settings.ShaderStackEntry
import com.nendo.argosy.ui.screens.settings.ShaderStackState
import com.swordfish.libretrodroid.ShaderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShaderChainManager(
    private val shaderRegistry: ShaderRegistry,
    private val shaderDownloader: ShaderDownloader,
    private val previewRenderer: ShaderPreviewRenderer,
    private val scope: CoroutineScope,
    private val previewInputProvider: suspend () -> Bitmap?,
    private val onChainChanged: ((ShaderChainConfig) -> Unit)? = null
) {
    companion object {
        private const val TAG = "ShaderChainManager"
        private const val PREVIEW_SIM_HEIGHT = 224
        private const val PREVIEW_UPSCALE = 3
    }

    var shaderStack by mutableStateOf(ShaderStackState())
        private set

    var previewBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    private var previewJob: Job? = null

    fun getShaderRegistry(): ShaderRegistry = shaderRegistry

    fun loadChain(chainJson: String) {
        shaderRegistry.ensureDirectoriesExist()
        val chain = ShaderChainConfig.fromJson(chainJson)
        val entries = chain.entries.map { entry ->
            val displayName = shaderRegistry.findById(entry.shaderId)?.displayName
                ?: entry.shaderId.removePrefix("custom:").replace('-', ' ')
                    .split(' ').joinToString(" ") {
                        it.replaceFirstChar { c -> c.uppercase() }
                    }
            ShaderStackEntry(
                shaderId = entry.shaderId,
                displayName = displayName,
                params = entry.params
            )
        }
        shaderStack = shaderStack.copy(
            entries = entries,
            selectedIndex = 0,
            paramFocusIndex = 0
        )
        loadParamsForSelectedShader()
        syncPreInstalls()
        renderPreview()
    }

    fun getChainConfig(): ShaderChainConfig = ShaderChainConfig(
        entries = shaderStack.entries.map { entry ->
            ShaderChainConfig.Entry(
                shaderId = entry.shaderId,
                params = entry.params
            )
        }
    )

    fun addShaderToStack(shaderId: String, displayName: String) {
        val newEntries = shaderStack.entries + ShaderStackEntry(shaderId, displayName)
        shaderStack = shaderStack.copy(
            entries = newEntries,
            selectedIndex = newEntries.size - 1,
            paramFocusIndex = 0,
            showShaderPicker = false
        )
        loadParamsForSelectedShader()
        notifyChainChanged()
        renderPreview()
    }

    fun removeShaderFromStack() {
        if (shaderStack.entries.isEmpty()) return
        val newEntries = shaderStack.entries.toMutableList().apply {
            removeAt(shaderStack.selectedIndex)
        }
        val newIndex = shaderStack.selectedIndex
            .coerceAtMost((newEntries.size - 1).coerceAtLeast(0))
        shaderStack = shaderStack.copy(
            entries = newEntries,
            selectedIndex = newIndex,
            paramFocusIndex = 0
        )
        loadParamsForSelectedShader()
        notifyChainChanged()
        renderPreview()
    }

    fun reorderShaderInStack(direction: Int) {
        val fromIndex = shaderStack.selectedIndex
        val toIndex = fromIndex + direction
        if (toIndex < 0 || toIndex >= shaderStack.entries.size) return
        val newEntries = shaderStack.entries.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }
        shaderStack = shaderStack.copy(
            entries = newEntries,
            selectedIndex = toIndex
        )
        notifyChainChanged()
        renderPreview()
    }

    fun selectShaderInStack(index: Int) {
        if (index < 0 || index >= shaderStack.entries.size) return
        if (index == shaderStack.selectedIndex) return
        shaderStack = shaderStack.copy(
            selectedIndex = index,
            paramFocusIndex = 0
        )
        loadParamsForSelectedShader()
    }

    fun cycleShaderTab(direction: Int) {
        if (shaderStack.entries.isEmpty()) return
        val newIndex = (shaderStack.selectedIndex + direction).let {
            when {
                it < 0 -> shaderStack.entries.size - 1
                it >= shaderStack.entries.size -> 0
                else -> it
            }
        }
        selectShaderInStack(newIndex)
    }

    fun moveShaderParamFocus(delta: Int) {
        if (shaderStack.selectedShaderParams.isEmpty()) return
        val newIndex = (shaderStack.paramFocusIndex + delta)
            .coerceIn(0, shaderStack.maxParamFocusIndex)
        shaderStack = shaderStack.copy(paramFocusIndex = newIndex)
    }

    fun adjustShaderParam(direction: Int) {
        val entry = shaderStack.selectedEntry ?: return
        val paramDef = shaderStack.selectedShaderParams
            .getOrNull(shaderStack.paramFocusIndex) ?: return

        val currentValue = entry.params[paramDef.name]?.toFloatOrNull()
            ?: paramDef.initial
        val newValue = (currentValue + direction * paramDef.step)
            .coerceIn(paramDef.min, paramDef.max)
        val roundedValue = (Math.round(newValue / paramDef.step) * paramDef.step)
            .coerceIn(paramDef.min, paramDef.max)

        val newParams = entry.params + (paramDef.name to roundedValue.toString())
        val newEntry = entry.copy(params = newParams)
        val newEntries = shaderStack.entries.toMutableList().apply {
            set(shaderStack.selectedIndex, newEntry)
        }
        shaderStack = shaderStack.copy(entries = newEntries)
        notifyChainChanged()
        debouncedRenderPreview()
    }

    fun resetShaderParam() {
        val entry = shaderStack.selectedEntry ?: return
        val paramDef = shaderStack.selectedShaderParams
            .getOrNull(shaderStack.paramFocusIndex) ?: return

        val newParams = entry.params + (paramDef.name to paramDef.initial.toString())
        val newEntry = entry.copy(params = newParams)
        val newEntries = shaderStack.entries.toMutableList().apply {
            set(shaderStack.selectedIndex, newEntry)
        }
        shaderStack = shaderStack.copy(entries = newEntries)
        notifyChainChanged()
        debouncedRenderPreview()
    }

    fun showShaderPicker() {
        shaderStack = shaderStack.copy(
            showShaderPicker = true,
            shaderPickerFocusIndex = 0,
            shaderPickerCategory = null
        )
    }

    fun dismissShaderPicker() {
        shaderStack = shaderStack.copy(showShaderPicker = false)
    }

    fun setShaderPickerFocusIndex(index: Int) {
        shaderStack = shaderStack.copy(shaderPickerFocusIndex = index)
    }

    fun moveShaderPickerFocus(delta: Int) {
        val maxIndex = (shaderRegistry.getShadersForPicker().size - 1)
            .coerceAtLeast(0)
        val newIndex = (shaderStack.shaderPickerFocusIndex + delta)
            .coerceIn(0, maxIndex)
        shaderStack = shaderStack.copy(shaderPickerFocusIndex = newIndex)
    }

    fun confirmShaderPickerSelection() {
        val shaders = shaderRegistry.getShadersForPicker()
        val grouped = shaders.groupBy { it.category }
        val categoryOrdered = buildList {
            for (category in ShaderRegistry.Category.entries) {
                addAll(grouped[category] ?: continue)
            }
        }
        val entry = categoryOrdered
            .getOrNull(shaderStack.shaderPickerFocusIndex) ?: return

        if (shaderRegistry.isInstalled(entry)) {
            addShaderToStack(entry.id, entry.displayName)
        } else {
            downloadAndAddShader(entry)
        }
    }

    fun renderPreview() {
        previewJob?.cancel()
        previewJob = scope.launch(Dispatchers.Default) {
            val entries = shaderStack.entries
            Log.d(TAG, "renderPreview: ${entries.size} entries, calling previewInputProvider")
            val sourceBitmap = previewInputProvider()
            Log.d(TAG, "renderPreview: sourceBitmap=${sourceBitmap?.let { "${it.width}x${it.height} recycled=${it.isRecycled}" } ?: "null"}")
            if (sourceBitmap == null) {
                withContext(Dispatchers.Main) { previewBitmap = null }
                return@launch
            }

            if (entries.isEmpty()) {
                Log.d(TAG, "renderPreview: no entries, showing raw frame")
                val imageBitmap = sourceBitmap.asImageBitmap()
                withContext(Dispatchers.Main) { previewBitmap = imageBitmap }
                return@launch
            }

            val allPasses = mutableListOf<ShaderConfig.Custom.ShaderPass>()
            val allPassParams = mutableListOf<Map<String, Float>>()

            for (entry in entries) {
                val registryEntry = shaderRegistry.findById(entry.shaderId)
                    ?: continue
                try {
                    val passes = shaderRegistry.loadShader(registryEntry).passes
                    val defaults = shaderRegistry
                        .loadShaderParameters(registryEntry)
                        .associate { it.name to it.initial }
                    val paramMap = defaults + entry.params.mapNotNull { (k, v) ->
                        v.toFloatOrNull()?.let { k to it }
                    }.toMap()
                    for (pass in passes) {
                        allPasses.add(ShaderConfig.Custom.ShaderPass(
                            vertex = shaderRegistry.prepareForUniformParams(pass.vertex),
                            fragment = shaderRegistry.prepareForUniformParams(pass.fragment),
                            linear = pass.linear,
                            scale = pass.scale
                        ))
                        allPassParams.add(paramMap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load shader ${entry.shaderId}", e)
                    continue
                }
            }

            val result = if (allPasses.isNotEmpty()) {
                val simHeight = PREVIEW_SIM_HEIGHT
                val aspect = sourceBitmap.width.toFloat() /
                    sourceBitmap.height.toFloat()
                val simWidth = (simHeight * aspect).toInt().coerceAtLeast(1)
                val inputBitmap = Bitmap.createScaledBitmap(
                    sourceBitmap, simWidth, simHeight, true
                )
                if (inputBitmap !== sourceBitmap) sourceBitmap.recycle()

                val outputWidth = simWidth * PREVIEW_UPSCALE
                val outputHeight = simHeight * PREVIEW_UPSCALE
                val shaderConfig = ShaderConfig.Custom(passes = allPasses)
                val rendered = previewRenderer.render(
                    inputBitmap = inputBitmap,
                    shaderConfig = shaderConfig,
                    passParams = allPassParams,
                    outputWidth = outputWidth,
                    outputHeight = outputHeight
                )
                inputBitmap.recycle()
                rendered
            } else {
                sourceBitmap
            }

            Log.d(TAG, "renderPreview: result=${result?.let { "${it.width}x${it.height}" } ?: "null"}")
            val imageBitmap = result?.asImageBitmap()
            withContext(Dispatchers.Main) {
                previewBitmap = imageBitmap
                Log.d(TAG, "renderPreview: previewBitmap set, isNull=${imageBitmap == null}")
            }
        }
    }

    fun debouncedRenderPreview() {
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(150)
            renderPreview()
        }
    }

    fun destroy() {
        previewJob?.cancel()
        previewRenderer.destroy()
    }

    private fun syncPreInstalls() {
        if (shaderStack.preInstallsSynced) return
        scope.launch {
            val result = shaderDownloader.syncPreInstalls(shaderRegistry)
            shaderStack = shaderStack.copy(preInstallsSynced = true)
            if (result.downloaded > 0) {
                shaderRegistry.invalidateInstalledCache()
            }
        }
    }

    private fun downloadAndAddShader(entry: ShaderRegistry.ShaderEntry) {
        shaderStack = shaderStack.copy(downloadingShaderId = entry.id)
        scope.launch {
            val result = shaderDownloader.downloadShader(entry)
            shaderStack = shaderStack.copy(downloadingShaderId = null)
            if (result.isSuccess) {
                shaderRegistry.invalidateInstalledCache()
                addShaderToStack(entry.id, entry.displayName)
            }
        }
    }

    private fun loadParamsForSelectedShader() {
        val entry = shaderStack.selectedEntry
        if (entry == null) {
            shaderStack = shaderStack.copy(
                selectedShaderParams = emptyList()
            )
            return
        }
        val registryEntry = shaderRegistry.findById(entry.shaderId)
        val params = if (registryEntry != null) {
            shaderRegistry.loadShaderParameters(registryEntry).map { p ->
                ShaderParamDef(
                    name = p.name,
                    description = p.description,
                    initial = p.initial,
                    min = p.min,
                    max = p.max,
                    step = p.step
                )
            }
        } else {
            emptyList()
        }
        shaderStack = shaderStack.copy(selectedShaderParams = params)
    }

    private fun notifyChainChanged() {
        onChainChanged?.invoke(getChainConfig())
    }
}
