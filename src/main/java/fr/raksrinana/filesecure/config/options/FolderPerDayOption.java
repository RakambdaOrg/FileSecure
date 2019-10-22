package fr.raksrinana.filesecure.config.options;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.raksrinana.filesecure.config.Option;
import fr.raksrinana.filesecure.files.DesiredTarget;
import fr.raksrinana.nameascreated.NewFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

/**
 * Move a file into a folder yyyy/mm/dd.
 * <p>
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-26.
 *
 * @author Thomas Couchoud
 * @since 2019-03-26
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("FolderPerDayOption")
public class FolderPerDayOption implements Option{
	private static final Logger LOGGER = LoggerFactory.getLogger(FolderPerDayOption.class);
	
	public FolderPerDayOption(){
	}
	
	@Override
	public void apply(final Path originFile, final DesiredTarget desiredTarget, final NewFile fileName, final Path folder){
		try{
			final var date = fileName.getDate();
			desiredTarget.setTargetFolder(folder.resolve(String.format("%4d", date.getYear())).resolve(String.format("%02d", date.getMonthValue())).resolve(String.format("%02d", date.getDayOfMonth())));
		}
		catch(final Exception e){
			LOGGER.error("Failed to build day folder for {} in {}", fileName, folder, e);
		}
	}
	
	@Override
	public int getPriority(){
		return 11;
	}
}