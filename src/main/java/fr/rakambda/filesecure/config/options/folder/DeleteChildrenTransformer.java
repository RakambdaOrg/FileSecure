package fr.rakambda.filesecure.config.options.folder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import fr.rakambda.filesecure.config.options.FolderTransformer;
import fr.rakambda.filesecure.utils.FileOperations;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("DeleteChildrenTransformer")
@NoArgsConstructor
public class DeleteChildrenTransformer implements FolderTransformer{
	@JsonProperty
	private List<String> names = new ArrayList<>();
	
	@Override
	public int getPriority(){
		return 10;
	}
	
	@Override
	public void apply(@NonNull FileOperations fileOperations, @NonNull Path folder, @NonNull Path baseFolder){
		try{
			for(var child : names){
				var childPath = folder.resolve(child);
				if(fileOperations.deleteIfExists(childPath)){
					log.info("Deleted child file {}", childPath);
				}
			}
		}
		catch(Exception e){
			log.error("Failed to delete children in {}", folder, e);
		}
	}
}
