package fr.rakambda.filesecure.config.options.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.rakambda.filesecure.config.options.FileOption;
import fr.rakambda.filesecure.files.DesiredTarget;
import fr.rakambda.filesecure.files.NewFile;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;

/**
 * Move a file into a folder yyyy/mm.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("FolderPerMonthOption")
@Log4j2
@NoArgsConstructor
public class FolderPerMonthOption implements FileOption{
	@Override
	public void apply(@NotNull Path originFile, @NotNull DesiredTarget desiredTarget, @NotNull NewFile fileName, @NotNull Path folder){
		try{
			var date = fileName.getDate();
			var year = "%4d".formatted(date.getYear());
			var month = "%02d".formatted(date.getMonthValue());
			desiredTarget.setTargetFolder(folder.resolve(year).resolve(month));
		}
		catch(Exception e){
			log.error("Failed to build month folder for {} in {}", fileName, folder, e);
		}
	}
	
	@Override
	public int getPriority(){
		return 13;
	}
}
