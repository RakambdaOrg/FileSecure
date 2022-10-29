package fr.rakambda.filesecure.config.options.folder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.rakambda.filesecure.config.options.FolderOption;
import fr.rakambda.filesecure.config.options.FolderOptionPhase;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("DeleteIfOlderThanOption")
@Slf4j
@NoArgsConstructor
public class DeleteIfOlderThanOption implements FolderOption{
	@JsonProperty(value = "dayOffset", required = true)
	@Getter
	private int dayOffset = Integer.MAX_VALUE;
	@JsonProperty(value = "depth")
	@Getter
	private int depth = 0;
	@JsonProperty(value = "deleteChildren")
	@Getter
	private List<String> deleteChildren = new ArrayList<>();
	
	@Override
	public Path apply(@NotNull Path folder, int depth, @NonNull FolderOptionPhase phase){
		if(phase == FolderOptionPhase.POST){
			try{
				if(depth >= this.depth){
					var folderTime = Files.getLastModifiedTime(folder);
					var date = LocalDateTime.ofInstant(folderTime.toInstant(), ZoneId.systemDefault());
					if(date.isBefore(LocalDateTime.now().minusDays(getDayOffset()))){
						delete(folder);
					}
				}
			}
			catch(DirectoryNotEmptyException e){
				log.error("Failed to delete folder {}, not empty", folder);
			}
			catch(Exception e){
				log.error("Failed to determine if {} should be deleted, it will not be by default", folder, e);
			}
		}
		return folder;
	}
	
	private void delete(Path folder) throws IOException{
		log.info("Deleting folder {} because it is more than {} days old", folder, dayOffset);
		
		for(var child : deleteChildren){
			var childPath = folder.resolve(child);
			if(Files.deleteIfExists(childPath)){
				log.info("Deleted child file {}", childPath);
			}
		}
		
		Files.delete(folder);
	}
	
	@Override
	public int getPriority(){
		return Integer.MAX_VALUE;
	}
}
