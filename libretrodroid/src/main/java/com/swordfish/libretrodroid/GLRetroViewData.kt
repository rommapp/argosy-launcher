/*
 *     Copyright (C) 2022  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.swordfish.libretrodroid

import android.content.Context

class GLRetroViewData(context: Context) {
    var coreFilePath: String? = null
    var gameFilePath: String? = null
    var gameFileBytes: ByteArray? = null
    var gameVirtualFiles: List<VirtualFile> = listOf()
    var systemDirectory: String = context.filesDir.absolutePath
    var savesDirectory: String = context.filesDir.absolutePath
    var variables: Array<Variable> = arrayOf()
    var saveRAMState: ByteArray? = null
    var rtcState: ByteArray? = null
    var shader: ShaderConfig = ShaderConfig.Default
    var rumbleEventsEnabled: Boolean = true
    var motionSensorsEnabled: Boolean = true
    var sunSensorEnabled: Boolean = false
    var coreAccelerometerScale: Float = 1f
    var preferLowLatencyAudio: Boolean = true
    var audioLatencyMs: Int = 0
    var forceSoftwareTiming: Boolean = false
    var skipDuplicateFrames: Boolean = false
    var timingFollowsGeometry: Boolean = false
    var enableMicrophone: Boolean = false
    var immersiveMode: ImmersiveMode? = null
    var preferVulkan: Boolean = false
    var vulkanDriver: VulkanDriver? = null
}
