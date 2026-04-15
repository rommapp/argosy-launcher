package com.nendo.argosy.data.steam

import android.content.Context
import android.os.Environment
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GnInstallProbe"
private const val GN_PACKAGE = "app.gamenative"
private const val PROBE_FILENAME = ".argosy-access-probe"
private const val PROBE_CONTENT = "Verifying write access for Argosy Steam installation.\n" +
    "Safe to delete -- Argosy will recreate as needed.\n"

data class GnProbeResult(
    val target: SteamInstallTarget,
    val writable: Boolean,
    val resolvedPackageRoot: String?
)

@Singleton
class GnInstallProbe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val androidDataAccessor: AndroidDataAccessor
) {

    fun probe(target: SteamInstallTarget): GnProbeResult {
        return when (target) {
            is SteamInstallTarget.Internal -> probeInternal()
            is SteamInstallTarget.ExternalAuto -> probeExternalAuto()
            is SteamInstallTarget.CustomVolume -> probeExternal(target.path)
        }
    }

    private fun probeInternal(): GnProbeResult {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val packageRoot = "$root/Android/data/$GN_PACKAGE"
        val resolvedRoot = androidDataAccessor.transformPath(packageRoot)
        val probeFile = File(resolvedRoot, PROBE_FILENAME)
        if (!File(resolvedRoot).exists()) {
            Logger.warn(TAG, "Internal package dir missing: $resolvedRoot (raw=$packageRoot)")
            return GnProbeResult(SteamInstallTarget.Internal, false, null)
        }
        val expectedLen = PROBE_CONTENT.toByteArray().size.toLong()
        val ok = try {
            probeFile.writeText(PROBE_CONTENT)
            true
        } catch (e: Exception) {
            val landed = probeFile.exists() && probeFile.length() == expectedLen
            if (landed) {
                Logger.info(TAG, "Internal probe close threw but data landed (${e.javaClass.simpleName}: ${e.message}); treating as writable")
                true
            } else {
                Logger.warn(TAG, "Internal probe write failed at ${probeFile.absolutePath}: ${e.javaClass.simpleName}: ${e.message}")
                false
            }
        }
        return GnProbeResult(SteamInstallTarget.Internal, ok, resolvedRoot)
    }

    private fun probeExternalAuto(): GnProbeResult {
        for (root in removableRootCandidates()) {
            val result = probeExternal(root)
            if (result.writable) return result.copy(target = SteamInstallTarget.ExternalAuto)
        }
        val internal = probeInternal()
        if (internal.writable) return internal.copy(target = SteamInstallTarget.ExternalAuto)
        return GnProbeResult(SteamInstallTarget.ExternalAuto, false, null)
    }

    private fun probeExternal(volumeRoot: String): GnProbeResult {
        val target = SteamInstallTarget.CustomVolume(volumeRoot)
        val packageRoot = "$volumeRoot/Android/data/$GN_PACKAGE"
        val packageDir = File(packageRoot)
        if (!packageDir.exists()) {
            return GnProbeResult(target, false, null)
        }
        val probeFile = File(packageDir, PROBE_FILENAME)
        val ok = try {
            probeFile.writeText(PROBE_CONTENT)
            probeFile.exists()
        } catch (e: Exception) {
            Logger.warn(TAG, "External probe write failed at ${probeFile.absolutePath}: ${e.message}")
            false
        }
        return GnProbeResult(target, ok, packageRoot)
    }

    private fun removableRootCandidates(): List<String> {
        val roots = mutableListOf<String>()
        try {
            File("/storage").listFiles()?.forEach { vol ->
                if (vol.isDirectory && vol.name != "emulated" && vol.name != "self") {
                    roots.add(vol.absolutePath)
                }
            }
        } catch (_: Exception) {}
        return roots
    }
}
