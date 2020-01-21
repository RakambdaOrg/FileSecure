package fr.raksrinana.filesecure.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.raksrinana.filesecure.config.options.folder.DeleteIfOlderThanOption;
import lombok.NonNull;
import java.nio.file.Path;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "name")
@JsonSubTypes(value = {
		@JsonSubTypes.Type(value = DeleteIfOlderThanOption.class, name = "DeleteIfOlderThanOption"),
})
public interface FolderOption extends Option{
	/**
	 * @param originFile The folder to process.
	 */
	void apply(@NonNull Path originFolder);
}
