package fr.rakambda.filesecure.metadata.name;

import org.jspecify.annotations.NonNull;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Pattern;

public abstract class DatePatternNameExtractor implements NameDateExtractor{
	@Override
	@NonNull
	public Optional<ZonedDateTime> parse(@NonNull String name){
		var matcher = getPattern().matcher(name);
		if(matcher.find()){
			return Optional.of(ZonedDateTime.parse(matcher.group(getCaptureGroupIndex()), getFormatter()));
		}
		return Optional.empty();
	}
	
	protected int getCaptureGroupIndex(){
		return 0;
	}
	
	protected abstract DateTimeFormatter getFormatter();
	
	protected abstract Pattern getPattern();
}
