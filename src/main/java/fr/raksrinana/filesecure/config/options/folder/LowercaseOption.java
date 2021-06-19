package fr.raksrinana.filesecure.config.options.folder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.raksrinana.filesecure.config.options.FolderOption;
import fr.raksrinana.filesecure.config.options.FolderOptionPhase;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.util.Locale;
import static fr.raksrinana.filesecure.config.options.FolderOptionPhase.PRE;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("LowercaseOption")
@Slf4j
@NoArgsConstructor
public class LowercaseOption implements FolderOption{
	@Override
	public Path apply(@NotNull Path folder, int depth, @NonNull FolderOptionPhase phase){
		if(phase == PRE){
			return folder.resolveSibling(folder.getFileName().toString().toLowerCase(Locale.ROOT));
		}
		return folder;
	}
	
	@Override
	public int getPriority(){
		return Integer.MAX_VALUE;
	}
}
