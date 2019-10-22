package fr.raksrinana.filesecure.config.options;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.raksrinana.filesecure.config.Option;
import fr.raksrinana.filesecure.exceptions.AbandonBackupException;
import fr.raksrinana.filesecure.files.DesiredTarget;
import fr.raksrinana.nameascreated.NewFile;
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
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("IfOlderThanOption")
public class IfOlderThanOption implements Option{
	private static final Logger LOGGER = LoggerFactory.getLogger(IfOlderThanOption.class);
	@JsonProperty(value = "dayOffset", required = true)
	private int dayOffset = Integer.MAX_VALUE;
	
	public IfOlderThanOption(){
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
