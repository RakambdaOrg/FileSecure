package fr.raksrinana.filesecure.config.options.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.raksrinana.filesecure.config.options.FileOption;
import fr.raksrinana.filesecure.exceptions.AbandonBackupException;
import fr.raksrinana.filesecure.files.DesiredTarget;
import fr.raksrinana.nameascreated.NewFile;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.time.ZonedDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("IfOlderThanOption")
@Log4j2
@NoArgsConstructor
public class IfOlderThanOption implements FileOption{
	@JsonProperty(value = "dayOffset", required = true)
	@Getter
	private int dayOffset = Integer.MAX_VALUE;
	
	@Override
	public void apply(@NotNull Path originFile, @NotNull DesiredTarget desiredTarget, @NotNull NewFile fileName, @NotNull Path folder) throws AbandonBackupException{
		try{
			var date = fileName.getDate();
			if(date.isAfter(ZonedDateTime.now().minusDays(getDayOffset()))){
				throw new AbandonBackupException(originFile);
			}
		}
		catch(Exception e){
			if(e instanceof AbandonBackupException){
				throw e;
			}
			log.error("Failed to determine if {} should be backed up, it will be by default", originFile, e);
		}
	}
	
	@Override
	public int getPriority(){
		return 0;
	}
}
