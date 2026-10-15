package fr.rakambda.filesecure.stage

import fr.rakambda.filesecure.pipeline.FileItem
import java.nio.file.Path
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Prefixes the destination with folders built from the first characters of its first path element, e.g. `going/file` with 3 elements becomes `g/o/i/going/file`.
 */
data class FolderCanonical(
	val elementCount: Int,
) : PathStage{
	override fun transform(item: FileItem): Path{
		val relative = item.relativeDestination
		val firstElement = relative.getName(0).toString()

		var canonical = item.outputBase
		for(index in 0 until elementCount){
			val part = firstElement.getOrNull(index)?.toString()?.lowercase()
			canonical = canonical.resolve(if(part == null || part in FORBIDDEN_PARTS) UNKNOWN_PART else part)
		}
		return canonical.resolve(relative)
	}

	private companion object{
		const val UNKNOWN_PART = "_"
		val FORBIDDEN_PARTS = setOf(".", "/", "\\")
	}
}

/**
 * Prefixes the destination with `yyyy/MM/dd` folders from the file date.
 */
class FolderPerDay : PathStage{
	override fun transform(item: FileItem): Path{
		val date = item.metadata.date
		return item.outputBase.resolve("%4d".format(date.year))
			.resolve("%02d".format(date.monthValue))
			.resolve("%02d".format(date.dayOfMonth))
			.resolve(item.relativeDestination)
	}
}

/**
 * Prefixes the destination with `yyyy/MM/w` folders from the file date, `w` being the aligned week of the month.
 */
class FolderPerWeek : PathStage{
	override fun transform(item: FileItem): Path{
		val date = item.metadata.date
		return item.outputBase.resolve("%4d".format(date.year))
			.resolve("%02d".format(date.monthValue))
			.resolve("%1d".format(date.get(ChronoField.ALIGNED_WEEK_OF_MONTH)))
			.resolve(item.relativeDestination)
	}
}

/**
 * Prefixes the destination with `yyyy/MM` folders from the file date.
 */
class FolderPerMonth : PathStage{
	override fun transform(item: FileItem): Path{
		val date = item.metadata.date
		return item.outputBase.resolve("%4d".format(date.year))
			.resolve("%02d".format(date.monthValue))
			.resolve(item.relativeDestination)
	}
}

/**
 * Prefixes the destination with a `yyyy` folder from the file date.
 */
class FolderPerYear : PathStage{
	override fun transform(item: FileItem): Path{
		val date = item.metadata.date
		return item.outputBase.resolve("%4d".format(date.year))
			.resolve(item.relativeDestination)
	}
}

/**
 * Prefixes the destination with a `yyyy-MM-dd` folder from the file date.
 */
class FolderUniquePerDay : PathStage{
	override fun transform(item: FileItem): Path{
		val date = item.metadata.date
		return item.outputBase.resolve("%4d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth))
			.resolve(item.relativeDestination)
	}
}

/**
 * Lowercases the folders of the destination below the output folder, the file name is kept as is.
 */
class Lowercase : PathStage{
	override fun transform(item: FileItem): Path{
		val folder = item.destination.parent
		val relativeFolder = item.outputBase.relativize(folder).toString().lowercase(Locale.ROOT)
		return item.outputBase.resolve(relativeFolder).resolve(item.destination.fileName)
	}
}

/**
 * Restores the original file name, keeping the current destination folder.
 */
class NoRename : PathStage{
	override fun transform(item: FileItem): Path = item.destination.withFileName(item.source.fileName.toString())
}
