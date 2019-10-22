package fr.raksrinana.filesecure.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The strategies available to do the backup.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum BackupStrategy{
	COPY, MOVE, NONE;
	
	/**
	 * Get the strategy by its name.
	 *
	 * @param name The name to search for.
	 *
	 * @return The strategy, or the default strategy if no strategies were found.
	 */
	@JsonCreator
	public static BackupStrategy getByName(final String name){
		return BackupStrategy.valueOf(name);
	}
	
	/**
	 * @return The default strategy to use.
	 */
	@SuppressWarnings("SameReturnValue")
	public static BackupStrategy getDefault(){
		return NONE;
	}
}
