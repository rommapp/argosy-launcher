package com.nendo.argosy.data.steam

import android.util.Log
import `in`.dragonbra.javasteam.enums.ELicenseFlags
import `in`.dragonbra.javasteam.enums.ELicenseType
import `in`.dragonbra.javasteam.enums.EPaymentMethod
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver
import org.json.JSONObject
import java.util.Date
import java.util.EnumSet

private const val TAG = "LicenseSerializer"

object LicenseSerializer {

    fun serialize(license: License): String {
        return try {
            JSONObject().apply {
                put("packageID", license.packageID)
                put("lastChangeNumber", license.lastChangeNumber)
                put("timeCreated", license.timeCreated.time)
                put("timeNextProcess", license.timeNextProcess.time)
                put("minuteLimit", license.minuteLimit)
                put("minutesUsed", license.minutesUsed)
                put("paymentMethod", license.paymentMethod.code())
                put("licenseFlags", org.json.JSONArray(license.licenseFlags.map { it.code() }))
                put("purchaseCode", license.purchaseCode)
                put("licenseType", license.licenseType.code())
                put("territoryCode", license.territoryCode)
                put("accessToken", license.accessToken)
                put("ownerAccountID", license.ownerAccountID)
                put("masterPackageID", license.masterPackageID)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize license: ${e.message}")
            ""
        }
    }

    fun deserialize(jsonStr: String): License? {
        if (jsonStr.isEmpty()) return null
        return try {
            val obj = JSONObject(jsonStr)

            val licenseFlagsArray = obj.optJSONArray("licenseFlags")
            val licenseFlags = if (licenseFlagsArray != null) {
                val flags = EnumSet.noneOf(ELicenseFlags::class.java)
                for (i in 0 until licenseFlagsArray.length()) {
                    flags.add(ELicenseFlags.from(licenseFlagsArray.optInt(i)).first())
                }
                flags
            } else {
                EnumSet.noneOf(ELicenseFlags::class.java)
            }

            val proto = SteammessagesClientserver.CMsgClientLicenseList.License.newBuilder()
                .setPackageId(obj.optInt("packageID", 0))
                .setChangeNumber(obj.optInt("lastChangeNumber", 0))
                .setTimeCreated((obj.optLong("timeCreated", 0L) / 1000).toInt())
                .setTimeNextProcess((obj.optLong("timeNextProcess", 0L) / 1000).toInt())
                .setMinuteLimit(obj.optInt("minuteLimit", 0))
                .setMinutesUsed(obj.optInt("minutesUsed", 0))
                .setPaymentMethod(EPaymentMethod.from(obj.optInt("paymentMethod", 0)).code())
                .setFlags(ELicenseFlags.code(licenseFlags))
                .setPurchaseCountryCode(obj.optString("purchaseCode", ""))
                .setLicenseType(ELicenseType.from(obj.optInt("licenseType", 0)).code())
                .setTerritoryCode(obj.optInt("territoryCode", 0))
                .setAccessToken(obj.optLong("accessToken", 0L))
                .setOwnerId(obj.optInt("ownerAccountID", 0))
                .setMasterPackageId(obj.optInt("masterPackageID", 0))
                .build()

            License(proto)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize license: ${e.message}")
            null
        }
    }
}
