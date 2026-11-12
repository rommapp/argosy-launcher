package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.ssl.ClientCertImportException
import com.nendo.argosy.data.remote.ssl.ClientCertInfo
import com.nendo.argosy.data.remote.ssl.ClientCertTarget
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryRow
import java.io.File
import java.text.DateFormat
import java.util.Date

data class ClientCertSummary(
    val displayName: String,
    val expiresAtMillis: Long,
    val forRomm: Boolean,
    val forJellyfin: Boolean
)

fun ClientCertInfo.toSummary(): ClientCertSummary = ClientCertSummary(
    displayName = displayName,
    expiresAtMillis = expiresAtMillis,
    forRomm = ClientCertTarget.ROMM in targets,
    forJellyfin = ClientCertTarget.JELLYFIN in targets
)

enum class ClientCertImportStatus {
    IDLE, IMPORTING, NOT_A_FILE, WRONG_PASSWORD, NO_PRIVATE_KEY, UNREADABLE, EXPIRED, UNSUPPORTED_KEY
}

fun Throwable.toImportStatus(): ClientCertImportStatus =
    when ((this as? ClientCertImportException)?.reason) {
        ClientCertImportException.Reason.NOT_A_FILE -> ClientCertImportStatus.NOT_A_FILE
        ClientCertImportException.Reason.WRONG_PASSWORD -> ClientCertImportStatus.WRONG_PASSWORD
        ClientCertImportException.Reason.NO_PRIVATE_KEY -> ClientCertImportStatus.NO_PRIVATE_KEY
        ClientCertImportException.Reason.EXPIRED -> ClientCertImportStatus.EXPIRED
        ClientCertImportException.Reason.UNSUPPORTED_KEY -> ClientCertImportStatus.UNSUPPORTED_KEY
        ClientCertImportException.Reason.UNREADABLE, null -> ClientCertImportStatus.UNREADABLE
    }

data class ClientCertPasswordEntry(
    val path: String,
    val draft: String = "",
    val row: TextEntryRow = TextEntryRow.FIELD,
    val buttonIndex: Int = TEXT_ENTRY_CONFIRM_BUTTON,
    val status: ClientCertImportStatus = ClientCertImportStatus.IDLE,
    val keyboardRequest: Int = 0
) {
    val fileName: String get() = File(path).name
}

@get:StringRes
val ClientCertImportStatus.errorRes: Int?
    get() = when (this) {
        ClientCertImportStatus.IDLE, ClientCertImportStatus.IMPORTING -> null
        ClientCertImportStatus.NOT_A_FILE -> R.string.ui_client_cert_password_error_not_a_file
        ClientCertImportStatus.WRONG_PASSWORD -> R.string.ui_client_cert_password_error_wrong_password
        ClientCertImportStatus.NO_PRIVATE_KEY -> R.string.ui_client_cert_password_error_no_private_key
        ClientCertImportStatus.UNREADABLE -> R.string.ui_client_cert_password_error_unreadable
        ClientCertImportStatus.EXPIRED -> R.string.ui_client_cert_password_error_expired
        ClientCertImportStatus.UNSUPPORTED_KEY -> R.string.ui_client_cert_password_error_unsupported_key
    }

@Composable
fun rememberCertificateDate(millis: Long): String =
    remember(millis) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis)) }
