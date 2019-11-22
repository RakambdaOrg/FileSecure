package fr.raksrinana.filesecure.config.options;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.raksrinana.filesecure.config.Option;
import fr.raksrinana.filesecure.files.DesiredTarget;
import fr.raksrinana.nameascreated.NewFile;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Path;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("NoRenameOption")
@Slf4j
@NoArgsConstructor
public class NoRenameOption implements Option{
	@Override
	public void apply(final Path originFile, final DesiredTarget desiredTarget, final NewFile fileName, final Path folder){
		desiredTarget.setDesiredName(originFile.getFileName().toString());
	}
	
	@Override
	public int getPriority(){
		return 100;
	}
}
