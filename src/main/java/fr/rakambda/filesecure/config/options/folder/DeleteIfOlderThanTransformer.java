package fr.rakambda.filesecure.config.options.folder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.rakambda.filesecure.config.options.FolderTransformer;
import fr.rakambda.filesecure.utils.FileOperations;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("DeleteIfOlderThanTransformer")
@NoArgsConstructor
public class DeleteIfOlderThanTransformer implements FolderTransformer{
	@JsonProperty(required = true)
	private int dayOffset = Integer.MAX_VALUE;
	@JsonProperty
	private int depth = 1;
	
	@Override
	public int getPriority(){
		return Integer.MAX_VALUE;
	}
	
	@Override
	public void apply(@NotNull FileOperations fileOperations, @NotNull Path folder, @NotNull Path baseFolder){
		try{
			var relativize = baseFolder.relativize(folder);
			var relativeDepth = relativize.getNameCount();
			
			if(baseFolder.equals(folder)){
				relativeDepth = 0;
			}
			
			if(relativeDepth < depth){
				return;
			}
			
			var folderTime = Files.getLastModifiedTime(folder);
			var date = LocalDateTime.ofInstant(folderTime.toInstant(), ZoneId.systemDefault());
			if(date.isBefore(LocalDateTime.now().minusDays(dayOffset))){
				delete(fileOperations, folder);
			}
		}
		catch(DirectoryNotEmptyException e){
			log.error("Failed to delete folder {}, not empty", folder);
		}
		catch(Exception e){
			log.error("Failed to determine if {} should be deleted, it will not be by default", folder, e);
		}
	}
	
	private void delete(@NotNull FileOperations fileOperations, @NotNull Path folder) throws IOException{
		if(Files.list(folder).toList().isEmpty()){
			log.info("Deleting folder {} because it is more than {} days old and empty", folder, dayOffset);
			fileOperations.delete(folder);
		}
		else{
			log.debug("Tried deleting folder {} because it is more than {} days old but wasn't empty", folder, dayOffset);
		}
	}
}
