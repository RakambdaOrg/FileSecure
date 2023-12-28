package fr.rakambda.filesecure.config.options.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.rakambda.filesecure.config.options.FileTransformer;
import fr.rakambda.filesecure.exceptions.AbandonBackupException;
import fr.rakambda.filesecure.processor.FileMetadata;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.util.Optional;

@Log4j2
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("FolderPerDayTransformer")
@NoArgsConstructor
public class FolderPerDayTransformer implements FileTransformer{
	@NotNull
	@Override
	public Optional<Path> apply(@NotNull Path sourceFile, @NotNull Path originalOutput, @NotNull Path baseOutput, @NotNull Path currentOutput, @NotNull FileMetadata metadata) throws AbandonBackupException{
		var date = metadata.getDate();
		var year = "%4d".formatted(date.getYear());
		var month = "%02d".formatted(date.getMonthValue());
		var day = "%02d".formatted(date.getDayOfMonth());
		
		var relative = baseOutput.relativize(currentOutput);
		return Optional.of(baseOutput.resolve(year).resolve(month).resolve(day).resolve(relative));
	}
	
	@Override
	public int getPriority(){
		return 11;
	}
}
