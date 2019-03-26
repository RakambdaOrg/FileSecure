package fr.mrcraftcod.filesecure.config;

/**
 * The strategies available to do the backup.
 */
public enum BackupStrategy{
	COPY, MOVE, NONE;
	
	/**
	 * Get the strategy by its name.
	 *
	 * @param name The name to search for.
	 *
	 * @return The strategy, or the default strategy if no strategies were found.
	 */
	public static BackupStrategy getByName(final String name){
		switch(name.toLowerCase()){
			case "copy":
				return COPY;
			case "move":
				return MOVE;
			case "none":
				return NONE;
			default:
				return getDefault();
		}
	}
	
	/**
	 * @return The default strategy to use.
	 */
	@SuppressWarnings("SameReturnValue")
	public static BackupStrategy getDefault(){
		return MOVE;
	}
}
