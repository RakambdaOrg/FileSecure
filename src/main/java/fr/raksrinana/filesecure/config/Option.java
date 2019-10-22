package fr.raksrinana.filesecure.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.raksrinana.filesecure.config.options.*;
import fr.raksrinana.filesecure.exceptions.AbandonBackupException;
import fr.raksrinana.filesecure.files.DesiredTarget;
import fr.raksrinana.nameascreated.NewFile;
import javax.annotation.Nonnull;
import java.nio.file.Path;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-26.
 *
 * @author Thomas Couchoud
 * @since 2019-03-26
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "name")
@JsonSubTypes(value = {
		@JsonSubTypes.Type(value = FolderPerDayOption.class, name = "FolderPerDayOption"),
		@JsonSubTypes.Type(value = FolderPerMonthOption.class, name = "FolderPerMonthOption"),
		@JsonSubTypes.Type(value = FolderPerYearOption.class, name = "FolderPerYearOption"),
		@JsonSubTypes.Type(value = IfOlderThanOption.class, name = "IfOlderThanOption"),
		@JsonSubTypes.Type(value = NoRenameOption.class, name = "NoRenameOption"),
		@JsonSubTypes.Type(value = UniqueFolderPerDayOption.class, name = "UniqueFolderPerDayOption"),
})
public interface Option extends Comparable<Option>{
	/**
	 * @param originFile    The file to process.
	 * @param desiredTarget The desired target so far.
	 * @param fileName      The name of the file.
	 * @param folder        The original destination.
	 */
	void apply(Path originFile, DesiredTarget desiredTarget, final NewFile fileName, final Path folder) throws AbandonBackupException;
	
	@Override
	default int compareTo(@Nonnull final Option o){
		return Integer.compare(getPriority(), o.getPriority());
	}
	
	/**
	 * Get the priority of this option.
	 *
	 * @return The priority, lower will be executed first.
	 */
	int getPriority();
}
