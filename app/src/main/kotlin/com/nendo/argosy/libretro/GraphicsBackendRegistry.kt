package com.nendo.argosy.libretro

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifestRegistry
import com.swordfish.libretrodroid.Variable

enum class GraphicsApi(val token: String) {
    OPENGL("opengl"),
    VULKAN("vulkan");

    companion object {
        fun fromToken(token: String?): GraphicsApi? = entries.firstOrNull { it.token == token }
    }
}

/**
 * How a built-in core chooses its renderer. [CoreOption] cores pick it from one of their own
 * options, and [vulkanValue] is the upstream token that selects their Vulkan renderer, or null when
 * the core has no Vulkan renderer. [FrontendApi] cores have no such option and follow the context
 * the frontend prefers, which Argosy stores per platform as a [GraphicsApi] token.
 */
sealed interface GraphicsBackend {
    val coreId: String

    data class CoreOption(
        override val coreId: String,
        val optionKey: String,
        val vulkanValue: String?
    ) : GraphicsBackend

    data class FrontendApi(override val coreId: String) : GraphicsBackend
}

object GraphicsBackendRegistry {

    val FRONTEND_API_DEFAULT = GraphicsApi.VULKAN

    private val backends: Map<String, GraphicsBackend> = listOf(
        GraphicsBackend.CoreOption("ppsspp", "ppsspp_backend", "vulkan"),
        GraphicsBackend.CoreOption("mednafen_psx_hw", "beetle_psx_hw_renderer", "hardware_vk"),
        GraphicsBackend.CoreOption("azahar", "citra_graphics_api", "Vulkan"),
        GraphicsBackend.CoreOption("parallel_n64", "parallel-n64-gfxplugin", "parallel"),
        GraphicsBackend.CoreOption("mupen64plus_next_gles3", "mupen64plus-rdp-plugin", "parallel"),
        GraphicsBackend.CoreOption("mupen64plus_next_gles2", "mupen64plus-rdp-plugin", "parallel"),
        GraphicsBackend.CoreOption("armsx2", "armsx2_renderer", "Vulkan"),
        GraphicsBackend.FrontendApi("dolphin"),
        GraphicsBackend.FrontendApi("flycast")
    ).associateBy { it.coreId }

    private val systemVulkanDriverOnlyCores = setOf("armsx2")

    fun forCore(coreId: String?): GraphicsBackend? = coreId?.let { backends[it] }

    fun acceptsCustomVulkanDriver(coreId: String?): Boolean = coreId !in systemVulkanDriverOnlyCores

    fun supportsVulkan(backend: GraphicsBackend?): Boolean = when (backend) {
        is GraphicsBackend.CoreOption -> backend.vulkanValue != null
        is GraphicsBackend.FrontendApi -> true
        null -> false
    }

    fun optionDef(backend: GraphicsBackend.CoreOption): CoreOptionDef? =
        CoreOptionManifestRegistry.getManifest(backend.coreId)
            ?.options
            ?.firstOrNull { it.key == backend.optionKey }

    fun isVulkanValue(backend: GraphicsBackend, value: String?): Boolean = when (backend) {
        is GraphicsBackend.CoreOption -> backend.vulkanValue != null && value == backend.vulkanValue
        is GraphicsBackend.FrontendApi -> GraphicsApi.fromToken(value) == GraphicsApi.VULKAN
    }

    /**
     * The renderer value a launch runs with. [coreVariables] are the variables sent to the core;
     * an option missing from them runs at the core's own default. [frontendApi] is the stored
     * token for [GraphicsBackend.FrontendApi] cores, which run OpenGL when it is absent or unknown.
     */
    fun effectiveValue(
        backend: GraphicsBackend,
        coreVariables: Map<String, String>,
        frontendApi: String?
    ): String? = when (backend) {
        is GraphicsBackend.CoreOption -> coreVariables[backend.optionKey] ?: optionDef(backend)?.coreDefault
        is GraphicsBackend.FrontendApi -> (GraphicsApi.fromToken(frontendApi) ?: FRONTEND_API_DEFAULT).token
    }

    fun gameValue(backend: GraphicsBackend, stored: String?): String? {
        val token = stored?.takeIf { it.isNotBlank() } ?: return null
        return when (backend) {
            is GraphicsBackend.CoreOption -> optionDef(backend)?.resolveStored(token)
            is GraphicsBackend.FrontendApi -> GraphicsApi.fromToken(token)?.token
        }
    }

    fun applyGameBackend(
        coreId: String?,
        coreVariables: List<Variable>,
        storedGameBackend: String?
    ): List<Variable> {
        val backend = forCore(coreId) as? GraphicsBackend.CoreOption ?: return coreVariables
        val value = gameValue(backend, storedGameBackend) ?: return coreVariables
        return coreVariables.filterNot { it.key == backend.optionKey } + Variable(backend.optionKey, value)
    }

    /**
     * [coreVariables] with a Vulkan renderer choice swapped for the core's own default, for
     * devices that cannot host a Vulkan context.
     */
    fun withoutVulkan(coreId: String?, coreVariables: List<Variable>): List<Variable> {
        val backend = forCore(coreId) as? GraphicsBackend.CoreOption ?: return coreVariables
        val fallback = optionDef(backend)?.coreDefault ?: return coreVariables
        return coreVariables.map { variable ->
            if (variable.key == backend.optionKey && isVulkanValue(backend, variable.value)) {
                Variable(backend.optionKey, fallback)
            } else {
                variable
            }
        }
    }

    /**
     * [coreValues], keyed by option, with the same Vulkan swap as the [Variable] form.
     */
    fun withoutVulkan(coreId: String?, coreValues: Map<String, String>): Map<String, String> =
        withoutVulkan(coreId, coreValues.map { (key, value) -> Variable(key, value) })
            .associate { it.key.orEmpty() to it.value.orEmpty() }

    fun prefersVulkan(
        coreId: String?,
        coreVariables: Map<String, String>,
        frontendApi: String?
    ): Boolean {
        val backend = forCore(coreId) ?: return false
        return isVulkanValue(backend, effectiveValue(backend, coreVariables, frontendApi))
    }
}
