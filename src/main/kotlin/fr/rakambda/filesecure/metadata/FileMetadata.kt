package fr.rakambda.filesecure.metadata

import java.time.ZonedDateTime

data class FileMetadata(
	val takenDate: ZonedDateTime? = null,
	val createdDate: ZonedDateTime? = null,
	val nameDate: ZonedDateTime? = null,
){
	/**
	 * The best known date of the file: from its name, then its media metadata, then its last modification.
	 */
	val date: ZonedDateTime
		get() = nameDate ?: takenDate ?: createdDate ?: ZonedDateTime.now()
}
