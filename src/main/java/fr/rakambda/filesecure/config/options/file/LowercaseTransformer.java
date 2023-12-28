package fr.rakambda.filesecure.config.options.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.rakambda.filesecure.config.options.FileTransformer;
import fr.rakambda.filesecure.exceptions.AbandonBackupException;
import fr.rakambda.filesecure.processor.FileMetadata;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
	
	@NotNull
	@Override
	public Optional<Path> apply(@NotNull Path sourceFile, @NotNull Path originalOutput, @NotNull Path baseOutput, @NotNull Path currentOutput, @NotNull FileMetadata metadata) throws AbandonBackupException{
		var relative = baseOutput.relativize(currentOutput.getParent());
		return Optional.of(baseOutput.resolve(relative.toString().toLowerCase(Locale.ROOT)).resolve(currentOutput.getFileName()));
	}
}
