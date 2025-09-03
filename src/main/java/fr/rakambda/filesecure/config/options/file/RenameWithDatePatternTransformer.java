package fr.rakambda.filesecure.config.options.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import fr.rakambda.filesecure.config.options.FileTransformer;
import fr.rakambda.filesecure.exceptions.AbandonBackupException;
import fr.rakambda.filesecure.processor.FileMetadata;
import fr.rakambda.filesecure.utils.json.PatternDeserializer;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NonNull;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("RenameWithDatePatternTransformer")
@Log4j2
@NoArgsConstructor
public class RenameWithDatePatternTransformer implements FileTransformer{
	@JsonProperty
	private String outputFormat = "yyyy-MM-dd HH.mm.ss";
	@JsonProperty(required = true)
	@JsonDeserialize(using = PatternDeserializer.class)
	private Pattern regex;
	@JsonProperty(required = true)
	private String dateFormat;
	@JsonProperty(required = true)
	private String timeFormat;
	@JsonProperty
	private String locale = "en";
	@JsonProperty
	private String zone;
	
	@Override
	public int getPriority(){
		return 0;
	}
	
	@NonNull
	@Override
	public Optional<Path> apply(@NonNull Path sourceFile, @NonNull Path originalOutput, @NonNull Path baseOutput, @NonNull Path currentOutput, @NonNull FileMetadata metadata) throws AbandonBackupException{
		var date = extractDate(currentOutput.getFileName().toString())
				.orElseThrow(() -> new AbandonBackupException(sourceFile));
		
		var zoneId = Optional.ofNullable(zone).map(ZoneId::of).orElse(ZoneId.systemDefault());
		var formatter = DateTimeFormatter.ofPattern(outputFormat, new Locale.Builder().setLanguage(locale).build()).withZone(zoneId);
		
		var fileName = currentOutput.getFileName().toString();
		var dotIndex = fileName.lastIndexOf('.');
		var extension = dotIndex < 0 ? "" : fileName.substring(dotIndex);
		
		return Optional.of(currentOutput.getParent().resolve(formatter.format(date) + extension));
	}
	
	@NonNull
	private Optional<ZonedDateTime> extractDate(@NonNull String name){
		try{
			var matcher = regex.matcher(name);
			if(!matcher.matches()){
				return Optional.empty();
			}
			var dateString = "%s %s".formatted(matcher.group("date"), matcher.group("time"));
			
			var zoneId = Optional.ofNullable(zone).map(ZoneId::of).orElse(ZoneId.systemDefault());
			var formatter = DateTimeFormatter.ofPattern("%s %s".formatted(dateFormat, timeFormat), new Locale.Builder().setLanguage(locale).build()).withZone(zoneId);
			
			return Optional.of(ZonedDateTime.parse(dateString, formatter));
		}
		catch(Exception e){
			log.error("Failed to decode date from value `{}` and format `{} {}`", name, dateFormat, timeFormat, e);
			return Optional.empty();
		}
	}
}
