package fr.rakambda.filesecure.conversion

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.github.kokorin.jaffree.ffprobe.FFprobeResult

/**
 * Filters files on their probe result, files that couldn't be probed never pass.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
	JsonSubTypes.Type(value = LengthMoreThan::class, name = "LengthMoreThan"),
	JsonSubTypes.Type(value = LengthLessThan::class, name = "LengthLessThan"),
)
sealed interface ProbeFilter{
	fun test(probeResult: FFprobeResult?): Boolean
}

/**
 * Keeps media lasting at least [value] seconds.
 */
data class LengthMoreThan(val value: Long) : ProbeFilter{
	override fun test(probeResult: FFprobeResult?): Boolean = probeResult?.format?.duration?.let { it >= value } ?: false
}

/**
 * Keeps media lasting at most [value] seconds.
 */
data class LengthLessThan(val value: Long) : ProbeFilter{
	override fun test(probeResult: FFprobeResult?): Boolean = probeResult?.format?.duration?.let { it <= value } ?: false
}
