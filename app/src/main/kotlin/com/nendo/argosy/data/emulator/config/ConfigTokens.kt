package com.nendo.argosy.data.emulator.config

import java.math.BigDecimal
import java.math.RoundingMode

internal object ConfigTokens {
    private const val TRUE = "true"
    private const val FALSE = "false"
    private val BOOL_TRUE = setOf("true", "1")
    private val BOOL_FALSE = setOf("false", "0")

    fun isAdjustable(option: ConfigOption): Boolean = when {
        option.values.isNotEmpty() -> true
        option.type == ConfigValueType.BOOL -> true
        option.type == ConfigValueType.INT || option.type == ConfigValueType.FLOAT -> option.range != null
        else -> false
    }

    /**
     * The token to write for [requested], spelled the way [currentRaw] (or the option's default)
     * spells its kind, or null when [requested] is not a value [option] may take. [dataRoot] bounds
     * a driver key's path.
     */
    fun resolve(option: ConfigOption, requested: String, currentRaw: String?, dataRoot: String?): String? {
        option.driverDir?.let { return driverPath(it, requested, dataRoot) }
        if (!option.editable || !isAdjustable(option)) return null
        if (option.values.isNotEmpty()) {
            return option.values.firstOrNull { ConfigOptionDecoder.same(option.type, it.token, requested) }?.token
        }
        val style = currentRaw?.trim()?.takeIf { it.isNotEmpty() } ?: option.defaultToken?.trim()
        return when (option.type) {
            ConfigValueType.BOOL -> bool(requested)?.let { spellBool(it, style) }
            ConfigValueType.INT -> requested.trim().toLongOrNull()
                ?.takeIf { inRange(option, it.toBigDecimal()) }
                ?.toString()
            ConfigValueType.FLOAT -> requested.trim().toBigDecimalOrNull()
                ?.takeIf { inRange(option, it) }
                ?.let { spellNumber(it, style) }
            else -> null
        }
    }

    /**
     * The registered value [direction] steps from [current], clamped at either end; null at the end.
     */
    fun cycled(option: ConfigOption, current: String?, direction: Int): String? {
        val values = option.values
        if (values.isEmpty()) return null
        val index = current?.let { token -> values.indexOfFirst { ConfigOptionDecoder.same(option.type, it.token, token) } } ?: -1
        val next = when {
            index < 0 && direction > 0 -> 0
            index < 0 -> values.lastIndex
            else -> index + direction
        }
        return values.getOrNull(next)?.token
    }

    fun toggled(current: String?): String = if (current?.let(::bool) == true) FALSE else TRUE

    fun isOn(current: String?): Boolean = current?.let(::bool) == true

    /**
     * [current] moved one step of the option's range in [direction], snapped to the step grid and
     * clamped; null when already at the end.
     */
    fun stepped(option: ConfigOption, current: String?, direction: Int): String? {
        val range = option.range ?: return null
        val min = BigDecimal.valueOf(range.min)
        val max = BigDecimal.valueOf(range.max)
        val step = BigDecimal.valueOf(range.step)
        val value = current?.trim()?.toBigDecimalOrNull() ?: option.defaultToken?.toBigDecimalOrNull() ?: min
        val offset = value.subtract(min).divide(step, 0, RoundingMode.HALF_UP)
        val next = min.add(step.multiply(offset.add(BigDecimal.valueOf(direction.toLong())))).max(min).min(max)
        if (next.compareTo(value) == 0) return null
        return if (option.type == ConfigValueType.INT) next.toBigInteger().toString() else next.stripTrailingZeros().toPlainString()
    }

    private fun driverPath(dir: String, requested: String, dataRoot: String?): String? {
        if (requested.isEmpty()) return requested
        val root = dataRoot?.trimEnd('/') ?: return null
        val prefix = "$root/$dir/"
        if (!requested.startsWith(prefix)) return null
        val rest = requested.removePrefix(prefix).trimEnd('/')
        if (rest.isEmpty() || rest.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        return requested
    }

    private fun inRange(option: ConfigOption, value: BigDecimal): Boolean {
        val range = option.range ?: return false
        return value >= BigDecimal.valueOf(range.min) && value <= BigDecimal.valueOf(range.max)
    }

    private fun bool(value: String): Boolean? = when (value.trim().lowercase()) {
        in BOOL_TRUE -> true
        in BOOL_FALSE -> false
        else -> null
    }

    private fun spellBool(value: Boolean, style: String?): String = when {
        style == "1" || style == "0" -> if (value) "1" else "0"
        style?.firstOrNull()?.isUpperCase() == true -> if (value) "True" else "False"
        else -> if (value) TRUE else FALSE
    }

    private fun spellNumber(value: BigDecimal, style: String?): String {
        val plain = value.stripTrailingZeros().toPlainString()
        return if ('.' !in plain && style?.contains('.') == true) "$plain.0" else plain
    }
}
