package fr.mrcraftcod.filesecure;

import fr.mrcraftcod.filesecure.files.Folder;
import fr.mrcraftcod.filesecure.files.FolderDifference;
import fr.mrcraftcod.filesecure.files.MissingFolderException;
import fr.mrcraftcod.filesecure.files.RootFolder;
import fr.mrcraftcod.utils.base.Log;
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
	 * The strategies available to do the backup.
	 */
	public enum BackupStrategy
	{
		COPY, MOVE;
		
		/**
		 * Get the strategy by its name.
		 *
		 * @param name The name to search for.
		 *
		 * @return The strategy, or the default strategy if no strategies were found.
		 */
		public static BackupStrategy getByName(String name)
		{
			switch(name.toLowerCase())
			{
				case "copy":
					return COPY;
				case "move":
					return MOVE;
				default:
					return getDefault();
			}
		}
		
		/**
		 * @return The default strategy to use.
		 */
		@SuppressWarnings({
				"WeakerAccess",
				"SameReturnValue"
		})
		public static BackupStrategy getDefault()
		{
			return MOVE;
		}
	}
	
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
	 * @param backupStrategy The backup strategy to use (copy/move/...). If null BackupStrategy.getDefault() will be used.
	 *
	 * @throws MissingFolderException If one of the folders doesn't exists.
	 */
	@SuppressWarnings("WeakerAccess")
	public void process(@NotNull Path input, @NotNull Path output, Function<File, String> renameStrategy, BackupStrategy backupStrategy) throws MissingFolderException
	{
		Log.info(String.format("Processing %s ==> %s", input, output));
		if(renameStrategy == null)
			renameStrategy = File::getName;
		if(!input.toFile().exists())
			throw new MissingFolderException(input);
		if(!output.toFile().exists())
			throw new MissingFolderException(output);
		
		Log.info("Building input folder...");
		Folder inputFolder = rootFolder.getFolderAt(input);
		inputFolder.explore();
		Log.info("Building output folder...");
		Folder outputFolder = rootFolder.getFolderAt(output);
		outputFolder.explore();
		
		Log.info("Building differences...");
		FolderDifference fd = outputFolder.getMissingWith(inputFolder, renameStrategy);
		fd.applyStrategy(backupStrategy == null ? BackupStrategy.getDefault() : backupStrategy);
	}
	
	/**
	 * Get the instance of this class.
	 *
	 * @return The instance.
	 */
	@SuppressWarnings("WeakerAccess")
	public static Processor getInstance()
	{
		if(INSTANCE == null)
			INSTANCE = new Processor();
		return INSTANCE;
	}
}
