package fr.rakambda.filesecure.metadata.name;

import org.jetbrains.annotations.NotNull;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Pattern;

public abstract class DatePatternNameExtractor implements NameDateExtractor{
	@Override
	@NotNull
	public Optional<ZonedDateTime> parse(@NotNull String name){
		var matcher = getPattern().matcher(name);
		if(matcher.find()){
			return Optional.of(ZonedDateTime.parse(matcher.group(0), getFormatter()));
		}
		return Optional.empty();
	}
	
	protected abstract DateTimeFormatter getFormatter();
	
	protected abstract Pattern getPattern();
}
