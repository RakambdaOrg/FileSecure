package fr.mrcraftcod.filesecure.config.options;

import fr.mrcraftcod.filesecure.config.Option;
import fr.mrcraftcod.filesecure.exceptions.AbandonBackupException;
import fr.mrcraftcod.filesecure.files.DesiredTarget;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-29.
 *
 * @author Thomas Couchoud
 * @since 2019-03-29
 */
public class IfOlderThanOption implements Option{
	private static final Logger LOGGER = LoggerFactory.getLogger(IfOlderThanOption.class);
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss");
	private final int dayOffset;
	
	public IfOlderThanOption(final JSONObject json){
		this.dayOffset = json.getInt("dayOffset");
	}
	
	@Override
	public void apply(final Path originFile, final DesiredTarget desiredTarget, final String fileName, final Path folder) throws AbandonBackupException{
		try{
			final var date = LocalDateTime.parse(fileName.substring(0, fileName.lastIndexOf(".")), DATE_TIME_FORMATTER);
			if(date.isAfter(LocalDateTime.now().minus(this.getDayOffset(), ChronoUnit.DAYS))){
				throw new AbandonBackupException(originFile);
			}
		}
		catch(final Exception e){
			LOGGER.error("Failed to determine if {} should be backed up, it will be by default", originFile, e);
		}
	}
	
	private int getDayOffset(){
		return this.dayOffset;
	}
	
	@Override
	public int getPriority(){
		return 0;
	}
}
