package fr.mrcraftcod.filesecure;

import fr.mrcraftcod.filesecure.files.Folder;
import fr.mrcraftcod.filesecure.files.MissingFolderException;
import fr.mrcraftcod.filesecure.files.RootFolder;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Process a pair of folder, one being the source of the backup, and the other the destination.
 * Singleton.
 * <p>
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 02/02/2018.
 *
 * @author Thomas Couchoud
 * @since 2018-02-02
 */
public class Processor
{
	private final RootFolder rootFolder;
	private static Processor INSTANCE;
	
	/**
	 * Constructor.
	 */
	private Processor()
	{
		this.rootFolder = new RootFolder();
	}
	
	/**
	 * Processes a pair of folders to backup.
	 * <p>
	 * If the input folder is "/A/B" and have this structure:
	 * A
	 * |-B
	 * | |-C
	 * | | |-1.txt
	 * | | |-2.txt
	 * | |-D
	 * | | |-3.txt
	 * | |-4.txt
	 * <p>
	 * The output folder "/Z" will then contain:
	 * Z
	 * |-C
	 * | |-1.txt
	 * | |-2.txt
	 * |-D
	 * | |-3.txt
	 * |-4.txt
	 *
	 * @param input          The folder to backup.
	 * @param output         The folder where to backup.
	 * @param renameStrategy The strategy used to rename files when executing the backup. If null the original name is kept.
	 *
	 * @throws MissingFolderException If one of the folders doesn't exists.
	 */
	public void process(@NotNull Path input, @NotNull Path output, Function<File, String> renameStrategy) throws MissingFolderException
	{
		System.out.printf("Processing %s ==> %s\n", input, output);
		if(renameStrategy == null)
			renameStrategy = File::getName;
		if(!input.toFile().exists())
			throw new MissingFolderException(input);
		if(!output.toFile().exists())
			throw new MissingFolderException(output);
		Folder inputFolder = rootFolder.getFolderAt(input);
		inputFolder.explore();
		Folder outputFolder = rootFolder.getFolderAt(output);
		outputFolder.explore();
		
		//TODO: Get differences & move files
		outputFolder.getMissingWith(inputFolder);
		
		System.out.println();
	}
	
	/**
	 * Get the instance of this class.
	 *
	 * @return The instance.
	 */
	public static Processor getInstance()
	{
		if(INSTANCE == null)
			INSTANCE = new Processor();
		return INSTANCE;
	}
}
