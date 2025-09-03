package fr.rakambda.filesecure.metadata.media;

import com.drew.metadata.Directory;
import org.jspecify.annotations.NonNull;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.TimeZone;

/**
 * @param <T> The directory type.
 */
public interface MediaDateExtractor<T extends Directory>{
	/**
	 * Get the date from the given {@link Directory}.
	 *
	 * @param directory The directory to get from.
	 * @param tz        The timezone of the date.
	 *
	 * @return The date or null if not found.
	 */
	@NonNull Optional<ZonedDateTime> parse(@NonNull Directory directory, @NonNull TimeZone tz);
	
	/**
	 * Get the class of the directory.
	 *
	 * @return The directory's class.
	 */
	@NonNull Class<T> getKlass();
}
