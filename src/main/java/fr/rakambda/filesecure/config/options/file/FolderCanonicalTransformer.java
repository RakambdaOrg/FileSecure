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
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Log4j2
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("FolderCanonicalTransformer")
@NoArgsConstructor
@AllArgsConstructor
public class FolderCanonicalTransformer implements FileTransformer{
	private static final String UNKNOWN_PART = "_";
	private static final Set<String> FORBIDDEN_PART = Set.of(".", "/", "\\");
	
	@JsonProperty(required = true)
	private int elementCount = Integer.MAX_VALUE;
	
	@NotNull
	@Override
	public Optional<Path> apply(@NotNull Path sourceFile, @NotNull Path originalOutput, @NotNull Path baseOutput, @NotNull Path currentOutput, @NotNull FileMetadata metadata) throws AbandonBackupException{
		var relative = baseOutput.relativize(currentOutput);
		var base = getBase(relative);
		var canonical = getCanonicalPath(base.getFileName().toString());
		return Optional.of(baseOutput.resolve(canonical).resolve(relative));
	}
	
	@NotNull
	private Path getBase(@NotNull Path path){
		var parent = path.getParent();
		if(Objects.isNull(parent) || Objects.equals(parent, path)){
			return path;
		}
		return getBase(parent);
	}
	
	private Path getCanonicalPath(String name){
		var path = Paths.get("");
		var max = Math.min(elementCount, name.length());
		for(var i = 0; i < max; i++){
			var part = String.valueOf(name.charAt(i)).toLowerCase();
			if(FORBIDDEN_PART.contains(part)){
				part = UNKNOWN_PART;
			}
			path = path.resolve(part);
		}
		while(path.getNameCount() < elementCount){
			path = path.resolve(UNKNOWN_PART);
		}
		return path;
	}
	
	@Override
	public int getPriority(){
		return 12;
	}
}
