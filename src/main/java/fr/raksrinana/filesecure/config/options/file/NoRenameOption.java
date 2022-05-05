package fr.raksrinana.filesecure.config.options.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.raksrinana.filesecure.config.options.FileOption;
import fr.raksrinana.filesecure.files.DesiredTarget;
import fr.raksrinana.filesecure.files.NewFile;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("NoRenameOption")
@Log4j2
@NoArgsConstructor
public class NoRenameOption implements FileOption{
	@Override
	public void apply(@NotNull Path originFile, @NotNull DesiredTarget desiredTarget, @NotNull NewFile fileName, @NotNull Path folder){
		desiredTarget.setDesiredName(originFile.getFileName().toString());
	}
	
	@Override
	public int getPriority(){
		return Integer.MAX_VALUE;
	}
}
