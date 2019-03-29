package fr.mrcraftcod.filesecure.exceptions;

import java.nio.file.Path;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-29.
 *
 * @author Thomas Couchoud
 * @since 2019-03-29
 */
public class AbandonBackupException extends Exception{
	public AbandonBackupException(final Path originFile){
		super("Abandoned the backup of " + originFile);
	}
}
