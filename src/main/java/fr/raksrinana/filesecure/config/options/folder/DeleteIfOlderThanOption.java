package fr.raksrinana.filesecure.config.options.folder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.raksrinana.filesecure.config.options.FolderOption;
import fr.raksrinana.filesecure.config.options.FolderOptionPhase;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import static fr.raksrinana.filesecure.config.options.FolderOptionPhase.POST;

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
	
	@Override
	public Path apply(@NotNull Path folder, int depth, @NonNull FolderOptionPhase phase){
		if(phase == POST){
			try{
				if(depth >= this.depth){
					var folderTime = Files.getLastModifiedTime(folder);
					var date = LocalDateTime.ofInstant(folderTime.toInstant(), ZoneId.systemDefault());
					if(date.isBefore(LocalDateTime.now().minusDays(getDayOffset()))){
						log.info("Deleting folder {} because it is more than {} days old", folder, dayOffset);
						Files.delete(folder);
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
	
	@Override
	public int getPriority(){
		return Integer.MAX_VALUE;
	}
}
