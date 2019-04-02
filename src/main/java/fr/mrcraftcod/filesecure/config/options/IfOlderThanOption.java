package fr.mrcraftcod.filesecure.config.options;

import fr.mrcraftcod.filesecure.config.Option;
import fr.mrcraftcod.filesecure.exceptions.AbandonBackupException;
import fr.mrcraftcod.filesecure.files.DesiredTarget;
import fr.mrcraftcod.nameascreated.NewFile;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-29.
 *
 * @author Thomas Couchoud
 * @since 2019-03-29
 */
public class IfOlderThanOption implements Option{
	private static final Logger LOGGER = LoggerFactory.getLogger(IfOlderThanOption.class);
	private final int dayOffset;
	
	public IfOlderThanOption(final JSONObject json){
		this.dayOffset = json.getInt("dayOffset");
	}
	
	@Override
	public void apply(final Path originFile, final DesiredTarget desiredTarget, final NewFile fileName, final Path folder) throws AbandonBackupException{
		try{
			final var date = fileName.getDate();
			if(date.isAfter(ZonedDateTime.now().minus(this.getDayOffset(), ChronoUnit.DAYS))){
				throw new AbandonBackupException(originFile);
			}
		}
		catch(final Exception e){
			if(e instanceof AbandonBackupException){
				throw e;
			}
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
