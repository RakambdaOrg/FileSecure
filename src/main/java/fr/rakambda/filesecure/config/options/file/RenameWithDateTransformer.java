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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("RenameWithDateTransformer")
@Log4j2
@NoArgsConstructor
public class RenameWithDateTransformer implements FileTransformer{
	@JsonProperty
	private String format = "yyyy-MM-dd HH.mm.ss";
	@JsonProperty
	private String locale = "en";
	@JsonProperty
	@JsonDeserialize(contentUsing = PatternDeserializer.class)
	private List<Pattern> filters = new LinkedList<>();
	@JsonProperty
	@Setter(AccessLevel.PACKAGE)
	private String zone;
	
	@Override
	public int getPriority(){
		return 0;
	}
	
	@NotNull
	@Override
	public Optional<Path> apply(@NotNull Path sourceFile, @NotNull Path originalOutput, @NotNull Path baseOutput, @NotNull Path currentOutput, @NotNull FileMetadata metadata) throws AbandonBackupException{
		if(Objects.nonNull(filters) && !filters.isEmpty()){
			var matched = this.filters.stream()
					.map(Pattern::asMatchPredicate)
					.anyMatch(pred -> pred.test(sourceFile.getFileName().toString()));
			if(!matched){
				log.debug("Not applying transformer {} because file name {} didn't match any filters", this, sourceFile);
				return Optional.empty();
			}
		}
		var zoneId = Optional.ofNullable(zone).map(ZoneId::of).orElse(ZoneId.systemDefault());
		var formatter = DateTimeFormatter.ofPattern(format, new Locale.Builder().setLanguage(locale).build()).withZone(zoneId);
		
		var date = metadata.getDate();
		var fileName = currentOutput.getFileName().toString();
		var dotIndex = fileName.lastIndexOf('.');
		var extension = dotIndex < 0 ? "" : fileName.substring(dotIndex);
		
		return Optional.of(currentOutput.getParent().resolve(formatter.format(date) + extension));
	}
}
