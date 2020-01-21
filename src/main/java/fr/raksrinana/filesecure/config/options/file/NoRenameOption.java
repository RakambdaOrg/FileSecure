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

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("NoRenameOption")
@Slf4j
@NoArgsConstructor
public class NoRenameOption implements FileOption{
	@Override
	public void apply(@NonNull final Path originFile, @NonNull final DesiredTarget desiredTarget, @NonNull final NewFile fileName, @NonNull final Path folder){
		desiredTarget.setDesiredName(originFile.getFileName().toString());
	}
	
	@Override
	public int getPriority(){
		return Integer.MAX_VALUE;
	}
}
