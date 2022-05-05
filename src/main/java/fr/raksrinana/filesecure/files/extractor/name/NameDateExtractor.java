package fr.raksrinana.filesecure.files.extractor.name;

import org.jetbrains.annotations.NotNull;
import java.time.ZonedDateTime;
import java.util.Optional;

public interface NameDateExtractor{
	/**
	 * Get the date from the filename
	 *
	 * @param name The filename
	 *
	 * @return The date if found.
	 */
	@NotNull Optional<ZonedDateTime> parse(@NotNull String name);
}
