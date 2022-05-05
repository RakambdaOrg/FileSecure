package fr.raksrinana.filesecure.config.options;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.raksrinana.filesecure.config.options.file.FolderPerDayOption;
import fr.raksrinana.filesecure.config.options.file.FolderPerMonthOption;
import fr.raksrinana.filesecure.config.options.file.FolderPerWeekOption;
import fr.raksrinana.filesecure.config.options.file.FolderPerYearOption;
import fr.raksrinana.filesecure.config.options.file.IfOlderThanOption;
import fr.raksrinana.filesecure.config.options.file.NoRenameOption;
import fr.raksrinana.filesecure.config.options.file.UniqueFolderPerDayOption;
import fr.raksrinana.filesecure.exceptions.AbandonBackupException;
import fr.raksrinana.filesecure.exceptions.FlagsProcessingException;
import fr.raksrinana.filesecure.files.DesiredTarget;
import fr.raksrinana.filesecure.files.NewFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "name")
@JsonSubTypes(value = {
		@JsonSubTypes.Type(value = FolderPerDayOption.class, name = "FolderPerDayOption"),
		@JsonSubTypes.Type(value = FolderPerWeekOption.class, name = "FolderPerWeekOption"),
		@JsonSubTypes.Type(value = FolderPerMonthOption.class, name = "FolderPerMonthOption"),
		@JsonSubTypes.Type(value = FolderPerYearOption.class, name = "FolderPerYearOption"),
		@JsonSubTypes.Type(value = IfOlderThanOption.class, name = "IfOlderThanOption"),
		@JsonSubTypes.Type(value = NoRenameOption.class, name = "NoRenameOption"),
		@JsonSubTypes.Type(value = UniqueFolderPerDayOption.class, name = "UniqueFolderPerDayOption"),
})
public interface FileOption extends Option{
	Logger log = LoggerFactory.getLogger(FileOption.class);
	
	/**
	 * Apply the flags on the strategy.
	 *
	 * @param flags        The flags to apply.
	 * @param originFile   The path to the file before moving it.
	 * @param newFile      The name of the file after moving it.
	 * @param outputFolder The path where the file will end up.
	 *
	 * @return The new path where the file will end up.
	 *
	 * @throws FlagsProcessingException If an error occurred while applying a flag.
	 * @throws AbandonBackupException   If the file shouldn't be backed up.
	 */
	@NotNull
	static DesiredTarget applyFlags(@NotNull Set<FileOption> flags, @NotNull Path originFile, @NotNull NewFile newFile, @NotNull Path outputFolder) throws FlagsProcessingException, AbandonBackupException{
		var desiredTarget = new DesiredTarget(outputFolder, newFile, newFile.getName(originFile));
		try{
			for(var flag : flags){
				flag.apply(originFile, desiredTarget, newFile, outputFolder);
			}
		}
		catch(AbandonBackupException e){
			throw e;
		}
		catch(Exception e){
			log.error("Error applying strategy to file {} in {}", newFile, outputFolder, e);
			throw new FlagsProcessingException("Error applying strategy to file " + newFile + " in " + outputFolder.toFile().getAbsolutePath());
		}
		return desiredTarget;
	}
	
	/**
	 * @param originFile    The file to process.
	 * @param desiredTarget The desired target so far.
	 * @param fileName      The name of the file.
	 * @param folder        The original destination.
	 */
	void apply(@NotNull Path originFile, @NotNull DesiredTarget desiredTarget, @NotNull NewFile fileName, @NotNull Path folder) throws AbandonBackupException;
}
