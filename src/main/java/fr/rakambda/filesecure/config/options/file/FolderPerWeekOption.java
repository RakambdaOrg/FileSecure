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
import java.time.temporal.ChronoField;

/**
 * Move a file into a folder yyyy/mm/ww.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("FolderPerWeekOption")
@Log4j2
@NoArgsConstructor
public class FolderPerWeekOption implements FileOption{
	@Override
	public void apply(@NotNull Path originFile, @NotNull DesiredTarget desiredTarget, @NotNull NewFile fileName, @NotNull Path folder){
		try{
			var date = fileName.getDate();
			var year = "%4d".formatted(date.getYear());
			var month = "%02d".formatted(date.getMonthValue());
			var week = "%1d".formatted(date.get(ChronoField.ALIGNED_WEEK_OF_MONTH));
			desiredTarget.setTargetFolder(folder.resolve(year).resolve(month).resolve(week));
		}
		catch(Exception e){
			log.error("Failed to build week folder for {} in {}", fileName, folder, e);
		}
	}
	
	@Override
	public int getPriority(){
		return 12;
	}
}
