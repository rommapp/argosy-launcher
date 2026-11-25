package com.nendo.argosy.data.emulator.config

import android.content.Context
import com.nendo.argosy.data.emulator.PackageDataAccess
import com.nendo.argosy.data.emulator.SavePathValidator
import com.nendo.argosy.data.storage.AndroidDataMirror
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.PServerExecutor
import com.nendo.argosy.util.RootShell
import com.nendo.argosy.util.XsuExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What backs a capability: the file access layer on its plain or alt-access tier, the Android/data
 * mirror or a direct script through a uid-0 root route ([ROOT]), or the same through the
 * shell-level app-data route ([APP_DATA_SHELL]).
 */
enum class EmulatorDataRoute { FILE_ACCESS, ALT_ACCESS, ROOT, APP_DATA_SHELL }

/**
 * Why a capability has no edit route: no uid-0 root route on the device ([NO_ROOT]), or a root
 * route that failed the relabel and chown probe ([ROOT_PROBE_FAILED]).
 */
enum class EmulatorEditBlock { NO_ROOT, ROOT_PROBE_FAILED }

/**
 * Argosy's reach into one emulator's data folder. Each route is null when the capability is not
 * available. [dataRoot] is the emulator's `Android/data/<package>/files` on the volume chosen by
 * [EmulatorAccessRules.chooseDataRoot], null only when the device lists no storage volume.
 * [editBlock] is set exactly when [editRoute] is null.
 */
data class EmulatorDataCapabilities(
    val dataRoot: String?,
    val readRoute: EmulatorDataRoute?,
    val writeAppDataRoute: EmulatorDataRoute?,
    val editRoute: EmulatorDataRoute?,
    val editBlock: EmulatorEditBlock? = null
) {
    val canRead: Boolean get() = readRoute != null
    val canWriteAppData: Boolean get() = writeAppDataRoute != null
    val canEdit: Boolean get() = editRoute != null
}

/**
 * The device facts the access decision rests on, gathered once per [EmulatorDataAccess.capabilities]
 * call. [mirrored] is true when the data root lies under an Android/data the mirror is copying.
 */
data class EmulatorAccessFacts(
    val fileAccessReadable: Boolean,
    val fileAccessWritable: Boolean,
    val altAccess: Boolean,
    val mirrored: Boolean,
    val rootAvailable: Boolean,
    val appDataShellAvailable: Boolean,
    val labelProbePassed: Boolean
)

data class DataRootCandidate(val root: String, val primaryModifiedAt: Long?)

internal object EmulatorAccessRules {
    const val PROBE_LABEL = "u:object_r:system_data_file:s0"
    private const val PROBE_OWNER = "1000:1000"
    private const val PROBE_PASSED = "probe=0"

    fun decide(dataRoot: String?, facts: EmulatorAccessFacts): EmulatorDataCapabilities {
        val fileRoute = when {
            facts.mirrored && facts.rootAvailable -> EmulatorDataRoute.ROOT
            facts.mirrored && facts.appDataShellAvailable -> EmulatorDataRoute.APP_DATA_SHELL
            facts.altAccess -> EmulatorDataRoute.ALT_ACCESS
            else -> EmulatorDataRoute.FILE_ACCESS
        }
        val write = when {
            facts.fileAccessWritable -> fileRoute
            facts.rootAvailable -> EmulatorDataRoute.ROOT
            facts.appDataShellAvailable -> EmulatorDataRoute.APP_DATA_SHELL
            else -> null
        }
        val edit = EmulatorDataRoute.ROOT.takeIf { facts.rootAvailable && facts.labelProbePassed }
        val editBlock = when {
            edit != null -> null
            facts.rootAvailable -> EmulatorEditBlock.ROOT_PROBE_FAILED
            else -> EmulatorEditBlock.NO_ROOT
        }
        return EmulatorDataCapabilities(
            dataRoot = dataRoot,
            readRoute = fileRoute.takeIf { facts.fileAccessReadable },
            writeAppDataRoute = write,
            editRoute = edit,
            editBlock = editBlock
        )
    }

    /**
     * The candidate whose volume holds the primary config file, the newest file first and
     * candidate order on a tie; the first candidate when none holds it.
     */
    fun chooseDataRoot(candidates: List<DataRootCandidate>): String? =
        (candidates.filter { it.primaryModifiedAt != null }.maxByOrNull { it.primaryModifiedAt ?: 0L }
            ?: candidates.firstOrNull())?.root

    /**
     * A script that, run through a root route, proves it can relabel and chown a file the way an
     * edit restores an emulator's config: uid 0, then touch, `chcon`, `ls -Z`, `chown` on
     * [scratchPath], which it always deletes. Its last line is `probe=0` when every step held.
     */
    fun probeScript(scratchPath: String): String {
        val f = RootShell.quote(scratchPath)
        val label = RootShell.quote(PROBE_LABEL)
        return "rc=0\n" +
            "rm -f $f\n" +
            "if [ \"\$(id -u)\" != \"0\" ]; then rc=1\n" +
            "elif ! touch $f; then rc=2\n" +
            "elif ! chcon $label $f; then rc=3\n" +
            "elif ! ls -Z $f | grep -qF $label; then rc=4\n" +
            "elif ! chown $PROBE_OWNER $f; then rc=5\n" +
            "fi\n" +
            "rm -f $f\n" +
            "echo probe=\$rc\n" +
            "exit \$rc\n"
    }

    /**
     * Whether [result] of [probeScript] shows every step held. Null when the route refused to run
     * the script, which settles nothing.
     */
    fun probePassed(result: RootShell.Result?): Boolean? {
        result ?: return null
        return result.succeeded && result.output.any { it.trim() == PROBE_PASSED }
    }
}

@Singleton
class EmulatorDataAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val savePathValidator: SavePathValidator,
    private val mirror: AndroidDataMirror
) {
    private val probeLock = Any()

    @Volatile
    private var probe: Pair<Any, Boolean>? = null

    suspend fun capabilities(emulatorId: String, packageName: String): EmulatorDataCapabilities =
        withContext(Dispatchers.IO) {
            val spec = EmulatorConfigRegistry.specFor(emulatorId)
            val dataRoot = dataRoot(spec, packageName)
            val rootAvailable = RootShell.isAvailable
            val facts = EmulatorAccessFacts(
                fileAccessReadable = dataRoot != null && (fal.isListable(dataRoot) || fal.isListable(packageDir(dataRoot))),
                fileAccessWritable = dataRoot != null && fal.canWrite(dataRoot),
                altAccess = savePathValidator.packageDataAccess(emulatorId, packageName) == PackageDataAccess.UNICODE,
                mirrored = dataRoot != null && AndroidDataMirror.lowerOf(dataRoot) != null && mirror.isActive,
                rootAvailable = rootAvailable,
                appDataShellAvailable = RootShell.appDataShell != null,
                labelProbePassed = rootAvailable && labelProbePassed()
            )
            EmulatorAccessRules.decide(dataRoot, facts)
        }

    private fun dataRoot(spec: EmulatorConfigSpec?, packageName: String): String? {
        val primary = spec?.primaryFile?.relativePaths.orEmpty()
        val candidates = fal.externalStorageRoots()
            .map { "${it.trimEnd('/')}/$ANDROID_DATA/$packageName/$FILES_DIR" }
            .distinct()
            .map { root ->
                val modified = primary.map { "$root/$it" }.filter { fal.exists(it) }.maxOfOrNull { fal.lastModified(it) }
                DataRootCandidate(root, modified)
            }
        return EmulatorAccessRules.chooseDataRoot(candidates)
    }

    private fun packageDir(dataRoot: String): String = dataRoot.removeSuffix("/$FILES_DIR")

    private fun labelProbePassed(): Boolean {
        val route = currentRootRoute() ?: return false
        probe?.takeIf { it.first === route }?.let { return it.second }
        return synchronized(probeLock) {
            probe?.takeIf { it.first === route }?.let { return@synchronized it.second }
            val scratch = File(context.cacheDir, PROBE_FILE).absolutePath
            val passed = EmulatorAccessRules.probePassed(RootShell.run(context, EmulatorAccessRules.probeScript(scratch)))
            Logger.info(TAG, "label probe | route=${route.javaClass.simpleName} passed=$passed")
            if (passed != null) probe = route to passed
            passed == true
        }
    }

    private fun currentRootRoute(): Any? = when {
        PServerExecutor.isAvailable -> PServerExecutor
        XsuExecutor.isAvailable -> XsuExecutor
        else -> RootShell.enabledRoot
    }

    private companion object {
        const val TAG = "EmulatorDataAccess"
        const val ANDROID_DATA = "Android/data"
        const val FILES_DIR = "files"
        const val PROBE_FILE = "emulator-config-probe"
    }
}
