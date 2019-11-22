package fr.raksrinana.filesecure.exceptions;

import java.nio.file.Path;

public class AbandonBackupException extends Exception{
	public AbandonBackupException(final Path originFile){
		super("Abandoned the backup of " + originFile);
	}
}
