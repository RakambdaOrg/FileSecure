package fr.raksrinana.filesecure.config.options.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.raksrinana.filesecure.config.FileOption;
import fr.raksrinana.filesecure.files.DesiredTarget;
import fr.raksrinana.nameascreated.NewFile;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Path;

/**
 * Move a file into a folder yyyy/mm/dd.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("FolderPerDayOption")
@Slf4j
@NoArgsConstructor
public class FolderPerDayOption implements FileOption{
	@Override
	public void apply(@NonNull final Path originFile, @NonNull final DesiredTarget desiredTarget, @NonNull final NewFile fileName, @NonNull final Path folder){
		try{
			final var date = fileName.getDate();
			desiredTarget.setTargetFolder(folder.resolve(String.format("%4d", date.getYear()))
					.resolve(String.format("%02d", date.getMonthValue()))
					.resolve(String.format("%02d", date.getDayOfMonth())));
		}
		catch(final Exception e){
			log.error("Failed to build day folder for {} in {}", fileName, folder, e);
		}
	}
	
	@Override
	public int getPriority(){
		return 11;
	}
}
