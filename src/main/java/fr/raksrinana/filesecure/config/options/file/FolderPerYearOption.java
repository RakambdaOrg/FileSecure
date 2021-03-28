package fr.raksrinana.filesecure.config.options.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.raksrinana.filesecure.config.FileOption;
import fr.raksrinana.filesecure.files.DesiredTarget;
import fr.raksrinana.nameascreated.NewFile;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;

/**
 * Move a file into a folder yyyy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("FolderPerYearOption")
@Slf4j
@NoArgsConstructor
public class FolderPerYearOption implements FileOption{
	@Override
	public void apply(@NotNull Path originFile, @NotNull DesiredTarget desiredTarget, @NotNull NewFile fileName, @NotNull Path folder){
		try{
			var date = fileName.getDate();
			var year = "%4d".formatted(date.getYear());
			desiredTarget.setTargetFolder(folder.resolve(year));
		}
		catch(Exception e){
			log.error("Failed to build year folder for {} in {}", fileName, folder, e);
		}
	}
	
	@Override
	public int getPriority(){
		return 13;
	}
}
