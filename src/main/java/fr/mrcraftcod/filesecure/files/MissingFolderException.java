package fr.mrcraftcod.filesecure.files;

import java.nio.file.Path;

/**
 * Exception raised when a folder doesn't exists.
 * <p>
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 07/02/18.
 *
 * @author Thomas Couchoud
 * @since 2018-02-07
 */
public class MissingFolderException extends IllegalStateException
{
	private static final long serialVersionUID = -2933870594840515527L;
	
	/**
	 * Constructor.
	 *
	 * @param path The path of the folder that doesn't exists.
	 */
	public MissingFolderException(final Path path)
	{
		super("The input folder " + path + " doesn't exists");
	}
}
