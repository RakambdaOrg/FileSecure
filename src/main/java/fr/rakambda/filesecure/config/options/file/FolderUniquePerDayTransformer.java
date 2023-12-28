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
@JsonTypeName("FolderUniquePerDayTransformer")
@NoArgsConstructor
public class FolderUniquePerDayTransformer implements FileTransformer {
	@Override
	public int getPriority(){
		return 10;
	}
	
	@NotNull
	@Override
	public Optional<Path> apply(@NotNull Path sourceFile, @NotNull Path originalOutput, @NotNull Path baseOutput, @NotNull Path currentOutput, @NotNull FileMetadata metadata) throws AbandonBackupException{
		var date = metadata.getDate();
		var dateFormat = "%4d-%02d-%02d".formatted(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
		
		var relative = baseOutput.relativize(currentOutput);
		return Optional.of(baseOutput.resolve(dateFormat).resolve(relative));
	}
}
