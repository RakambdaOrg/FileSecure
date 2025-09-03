package fr.rakambda.filesecure.config.options;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.rakambda.filesecure.config.options.folder.DeleteChildrenTransformer;
import fr.rakambda.filesecure.config.options.folder.DeleteIfEmptyTransformer;
import fr.rakambda.filesecure.config.options.folder.DeleteIfOlderThanTransformer;
import fr.rakambda.filesecure.utils.FileOperations;
import org.jspecify.annotations.NonNull;
import java.nio.file.Path;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "name")
@JsonSubTypes(value = {
		@JsonSubTypes.Type(value = DeleteIfOlderThanTransformer.class, name = "DeleteIfOlderThanTransformer"),
		@JsonSubTypes.Type(value = DeleteIfEmptyTransformer.class, name = "DeleteIfEmptyTransformer"),
		@JsonSubTypes.Type(value = DeleteChildrenTransformer.class, name = "DeleteChildrenTransformer"),
})
public interface FolderTransformer extends Transformer{
	void apply(@NonNull FileOperations fileOperations, @NonNull Path folder, @NonNull Path baseFolder);
}
