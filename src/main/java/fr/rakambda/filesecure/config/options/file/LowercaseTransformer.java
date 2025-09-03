package fr.rakambda.filesecure.config.options.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.rakambda.filesecure.config.options.FileTransformer;
import fr.rakambda.filesecure.exceptions.AbandonBackupException;
import fr.rakambda.filesecure.processor.FileMetadata;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("LowercaseTransformer")
@NoArgsConstructor
public class LowercaseTransformer implements FileTransformer {
	@Override
	public int getPriority(){
		return Integer.MAX_VALUE;
	}
	
	@NonNull
	@Override
	public Optional<Path> apply(@NonNull Path sourceFile, @NonNull Path originalOutput, @NonNull Path baseOutput, @NonNull Path currentOutput, @NonNull FileMetadata metadata) throws AbandonBackupException{
		var relative = baseOutput.relativize(currentOutput.getParent());
		return Optional.of(baseOutput.resolve(relative.toString().toLowerCase(Locale.ROOT)).resolve(currentOutput.getFileName()));
	}
}
