package fr.rakambda.filesecure.stage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.util.regex.Pattern

class PathStagesTest{
	private val date = ZonedDateTime.of(2023, 5, 12, 10, 15, 12, 0, UTC)
	private val base = Path.of("/path")
	private val out = Path.of("/path/to/my/file.ext")

	@Test
	fun folderPerDay(){
		assertThat(FolderPerDay().transform(fileItem(base, out, date = date))).isEqualTo(Path.of("/path/2023/05/12/to/my/file.ext"))
	}

	@Test
	fun folderPerWeek(){
		assertThat(FolderPerWeek().transform(fileItem(base, out, date = date))).isEqualTo(Path.of("/path/2023/05/2/to/my/file.ext"))
	}

	@Test
	fun folderPerMonth(){
		assertThat(FolderPerMonth().transform(fileItem(base, out, date = date))).isEqualTo(Path.of("/path/2023/05/to/my/file.ext"))
	}

	@Test
	fun folderPerYear(){
		assertThat(FolderPerYear().transform(fileItem(base, out, date = date))).isEqualTo(Path.of("/path/2023/to/my/file.ext"))
	}

	@Test
	fun folderUniquePerDay(){
		assertThat(FolderUniquePerDay().transform(fileItem(base, out, date = date))).isEqualTo(Path.of("/path/2023-05-12/to/my/file.ext"))
	}

	@Test
	fun folderCanonical(){
		val item = fileItem(base, Path.of("/path/going/to/my/file.ext"))
		assertThat(FolderCanonical(3).transform(item)).isEqualTo(Path.of("/path/g/o/i/going/to/my/file.ext"))
	}

	@Test
	fun folderCanonicalWhenPartIsSmaller(){
		val item = fileItem(base, Path.of("/path/go/to/my/file.ext"))
		assertThat(FolderCanonical(3).transform(item)).isEqualTo(Path.of("/path/g/o/_/go/to/my/file.ext"))
	}

	@Test
	fun folderCanonicalReplacesForbiddenCharacters(){
		val item = fileItem(base, Path.of("/path/.git/file.ext"))
		assertThat(FolderCanonical(2).transform(item)).isEqualTo(Path.of("/path/_/g/.git/file.ext"))
	}

	@Test
	fun lowercaseOnlyChangesFolders(){
		val item = fileItem(Path.of("/pAth/tO"), Path.of("/pAth/tO/mY/sUper/fIle.ext"))
		assertThat(Lowercase().transform(item)).isEqualTo(Path.of("/pAth/tO/my/super/fIle.ext"))
	}

	@Test
	fun lowercaseFileAtRoot(){
		val item = fileItem(Path.of("/pAth"), Path.of("/pAth/fIle.ext"))
		assertThat(Lowercase().transform(item)).isEqualTo(Path.of("/pAth/fIle.ext"))
	}

	@Test
	fun noRename(){
		val item = fileItem(Path.of("/base"), out, source = Path.of("/path/to/source.other"))
		assertThat(NoRename().transform(item)).isEqualTo(Path.of("/path/to/my/source.other"))
	}

	@Test
	fun renameWithDate(){
		val item = fileItem(base, out, date = date)
		assertThat(RenameWithDate(zone = "UTC+2").transform(item)).isEqualTo(Path.of("/path/to/my/2023-05-12 12.15.12.ext"))
	}

	@Test
	fun renameWithDateWithoutExtension(){
		val item = fileItem(base, Path.of("/path/to/file"), date = date)
		assertThat(RenameWithDate(zone = "UTC").transform(item)).isEqualTo(Path.of("/path/to/2023-05-12 10.15.12"))
	}

	@Test
	fun renameWithDateIgnoresFilesNotMatchingFilters(){
		val item = fileItem(base, out, date = date)
		assertThat(RenameWithDate(filters = listOf(Pattern.compile("IMG_.*"))).transform(item)).isNull()
	}

	@Test
	fun renameWithDatePattern(){
		val stage = RenameWithDatePattern(
			regex = Pattern.compile("""Screenshot (?<date>\d{8}) (?<time>\d{6})\..*"""),
			dateFormat = "yyyyMMdd",
			timeFormat = "HHmmss",
			zone = "UTC",
		)
		val item = fileItem(base, Path.of("/path/Screenshot 20230512 101512.png"))

		assertThat(stage.process(item)).isEqualTo(StageResult.Continue)
		assertThat(item.destination).isEqualTo(Path.of("/path/2023-05-12 10.15.12.png"))
	}

	@Test
	fun renameWithDatePatternSkipsNamesWithoutDate(){
		val stage = RenameWithDatePattern(
			regex = Pattern.compile("""Screenshot (?<date>\d{8}) (?<time>\d{6})\..*"""),
			dateFormat = "yyyyMMdd",
			timeFormat = "HHmmss",
		)
		val item = fileItem(base, Path.of("/path/other.png"))

		assertThat(stage.process(item)).isInstanceOf(StageResult.Skip::class.java)
	}
}
