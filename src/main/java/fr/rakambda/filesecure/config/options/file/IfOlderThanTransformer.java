package fr.rakambda.filesecure.config.options.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.rakambda.filesecure.config.options.FileTransformer;
import fr.rakambda.filesecure.exceptions.AbandonBackupException;
import fr.rakambda.filesecure.processor.FileMetadata;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;

@Log4j2
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("IfOlderThanTransformer")
@NoArgsConstructor
@AllArgsConstructor
public class IfOlderThanTransformer implements FileTransformer {
	@JsonProperty
	private int dayOffset = 0;
	@JsonProperty
	private int minuteOffset = 0;
	
	@Override
	public int getPriority(){
		return 0;
	}
	
	@NotNull
	@Override
	public Optional<Path> apply(@NotNull Path sourceFile, @NotNull Path originalOutput, @NotNull Path baseOutput, @NotNull Path currentOutput, @NotNull FileMetadata metadata) throws AbandonBackupException{
		var date = metadata.getDate();
		if(date.isAfter(ZonedDateTime.now().minusDays(dayOffset).minusMinutes(minuteOffset))){
			throw new AbandonBackupException(sourceFile);
		}
		return Optional.empty();
	}
}
