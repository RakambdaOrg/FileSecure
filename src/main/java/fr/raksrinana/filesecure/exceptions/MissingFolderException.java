package fr.raksrinana.filesecure.exceptions;

import lombok.Getter;
import java.nio.file.Path;

/**
 * Exception raised when a folder doesn't exists.
 */
public class MissingFolderException extends IllegalStateException{
	private static final long serialVersionUID = -2933870594840515527L;
	@Getter
	private final Path path;
	
	/**
	 * Constructor.
	 *
	 * @param path The path of the folder that doesn't exists.
	 */
	public MissingFolderException(final Path path){
		this(path, "input");
	}
	
	/**
	 * Constructor.
	 *
	 * @param path The path of the folder that doesn't exists.
	 * @param kind The kind of the folder (input, output, ...).
	 */
	public MissingFolderException(final Path path, final String kind){
		super("The " + kind + " folder " + path + " doesn't exists");
		this.path = path;
	}
}
