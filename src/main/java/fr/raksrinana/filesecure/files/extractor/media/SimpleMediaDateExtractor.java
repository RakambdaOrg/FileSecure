package fr.raksrinana.filesecure.files.extractor.media;

import com.drew.metadata.Directory;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

public class SimpleMediaDateExtractor<T extends Directory> implements MediaDateExtractor<T>{
	private final int tag;
	@Getter
	private final Class<T> klass;
	
	/**
	 * Constructor.
	 *
	 * @param klass The class of the directory.
	 * @param tag   The tag to fetch the date from.
	 */
	public SimpleMediaDateExtractor(@NotNull Class<T> klass, int tag){
		this.klass = klass;
		this.tag = tag;
	}
	
	@NotNull
	@Override
	public Optional<ZonedDateTime> parse(@NotNull Directory directory, @NotNull TimeZone tz){
		return Optional.ofNullable(directory.getDate(tag, tz))
				.map(Date::toInstant)
				.map(date -> date.atZone(tz.toZoneId()));
	}
}
