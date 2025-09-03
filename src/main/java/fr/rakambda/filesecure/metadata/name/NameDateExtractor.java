package fr.rakambda.filesecure.metadata.name;

import org.jspecify.annotations.NonNull;
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
	@NonNull Optional<ZonedDateTime> parse(@NonNull String name);
}
