package com.nendo.argosy.libretro

/**
 * Built-in cores that read the host microphone through RETRO_ENVIRONMENT_GET_MICROPHONE_INTERFACE.
 * The legacy melonds core, which feeds only simulated blow or noise samples, is absent.
 */
object MicrophoneCores {
    private val HOST_MICROPHONE_CORE_IDS = setOf("melondsds")

    fun usesHostMicrophone(coreId: String?): Boolean = coreId in HOST_MICROPHONE_CORE_IDS

    fun shouldRequest(coreId: String?, granted: Boolean, alreadyAsked: Boolean): Boolean =
        !granted && !alreadyAsked && usesHostMicrophone(coreId)
}
