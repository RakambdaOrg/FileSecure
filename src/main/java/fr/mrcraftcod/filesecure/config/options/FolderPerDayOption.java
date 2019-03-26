package fr.mrcraftcod.filesecure.config.options;

import fr.mrcraftcod.filesecure.config.Option;
import fr.mrcraftcod.filesecure.files.DesiredTarget;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Move a file into a folder yyyy/mm/dd.
 * <p>
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-26.
 *
 * @author Thomas Couchoud
 * @since 2019-03-26
 */
public class FolderPerDayOption implements Option{
	private static final Logger LOGGER = LoggerFactory.getLogger(FolderPerDayOption.class);
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss");
	
	public FolderPerDayOption(final JSONObject json){
	
	}
	
	@Override
	public void apply(final Path originFile, final DesiredTarget desiredTarget, final String fileName, final Path folder){
		try{
			final var date = LocalDateTime.parse(fileName.substring(0, fileName.lastIndexOf(".")), DATE_TIME_FORMATTER);
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
