package fr.raksrinana.filesecure.config.options;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.raksrinana.filesecure.config.options.folder.DeleteIfOlderThanOption;
import fr.raksrinana.filesecure.config.options.folder.LowercaseOption;
import fr.raksrinana.filesecure.exceptions.AbandonBackupException;
import fr.raksrinana.filesecure.exceptions.FlagsProcessingException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "name")
@JsonSubTypes(value = {
		@JsonSubTypes.Type(value = DeleteIfOlderThanOption.class, name = "DeleteIfOlderThanOption"),
		@JsonSubTypes.Type(value = LowercaseOption.class, name = "LowercaseOption"),
})
public interface FolderOption extends Option{
	Logger log = LoggerFactory.getLogger(FileOption.class);
	
	/**
	 * @param originFolder The folder to process.
	 * @param depth
	 */
	Path apply(@NotNull Path originFolder, int depth, @NotNull FolderOptionPhase phase) throws AbandonBackupException;
	
	@NotNull
	static Path applyFlags(@NotNull Set<FolderOption> flags, @NotNull Path originFolder, int depth, @NotNull FolderOptionPhase phase) throws FlagsProcessingException, AbandonBackupException{
		var path = originFolder;
		try{
			for(var flag : flags){
				path = flag.apply(path, depth, phase);
			}
		}
		catch(AbandonBackupException e){
			throw e;
		}
		catch(Exception e){
			log.error("Error applying strategy to folder {}", originFolder, e);
			throw new FlagsProcessingException("Error applying strategy to folder " + originFolder);
		}
		return path;
	}
}
