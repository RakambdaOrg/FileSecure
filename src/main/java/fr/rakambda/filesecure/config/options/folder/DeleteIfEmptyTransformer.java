package fr.rakambda.filesecure.config.options.folder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.rakambda.filesecure.config.options.FolderTransformer;
import fr.rakambda.filesecure.utils.FileOperations;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("DeleteIfEmptyTransformer")
@NoArgsConstructor
@AllArgsConstructor
public class DeleteIfEmptyTransformer implements FolderTransformer{
	@JsonProperty
	private int depth = 1;
	
	@Override
	public int getPriority(){
		return Integer.MAX_VALUE;
	}
	
	@Override
	public void apply(@NotNull FileOperations fileOperations, @NotNull Path folder, @NotNull Path baseFolder){
		try{
			var relativeDepth = baseFolder.relativize(folder).getNameCount();
			if(relativeDepth < depth){
				return;
			}
			
			if(Files.list(folder).toList().isEmpty()){
				log.info("Deleting folder {} because it is empty", folder);
				fileOperations.delete(folder);
			}
		}
		catch(DirectoryNotEmptyException e){
			log.error("Failed to delete folder {}, not empty", folder);
		}
		catch(Exception e){
			log.error("Failed to determine if {} should be deleted, it will not be by default", folder, e);
		}
	}
}
