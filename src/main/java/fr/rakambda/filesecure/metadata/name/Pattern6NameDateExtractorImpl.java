package fr.rakambda.filesecure.metadata.name;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

public class Pattern6NameDateExtractorImpl extends DatePatternNameExtractor{
	private static final Pattern NAME_PATTERN = Pattern.compile("Video-(\\d{8}-\\d{6})");
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withLocale(Locale.ENGLISH)
			.withZone(ZoneId.systemDefault());
	
	@Override
	protected DateTimeFormatter getFormatter(){
		return DATE_TIME_FORMATTER;
	}
	
	@Override
	protected Pattern getPattern(){
		return NAME_PATTERN;
	}
	
	@Override
	protected int getCaptureGroupIndex(){
		return 1;
	}
}
