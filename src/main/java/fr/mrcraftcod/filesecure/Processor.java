package fr.mrcraftcod.filesecure;

import fr.mrcraftcod.filesecure.files.FolderDifference;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Process a pair of folder, one being the source of the backup, and the other the destination.
 * Singleton.
 * <p>
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 02/02/2018.
 *
 * @author Thomas Couchoud
 * @since 2018-02-02
 */
public class Processor{
	private static final Logger LOGGER = LoggerFactory.getLogger(Processor.class);
	private static Processor INSTANCE;
	
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
	
	/**
	 * Constructor.
	 */
	private Processor(){
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
	 * @param filters        The filters of the files to keep. If empty, all files will be kept.
	 * @param excludes       The filters of the files not to keep. If empty, all files will be kept.
	 *
	 * @throws MissingFolderException If one of the folders doesn't exists.
	 */
	void process(@NotNull final Path input, @NotNull final Path output, Function<Path, String> renameStrategy, BackupStrategy backupStrategy, final List<Pattern> filters, final List<Pattern> excludes, final List<Flags> flags) throws MissingFolderException{
		backupStrategy = backupStrategy == null ? BackupStrategy.getDefault() : backupStrategy;
		LOGGER.info("Processing ({}) {} ==> {}", backupStrategy.name(), input, output);
		if(renameStrategy == null){
			renameStrategy = f -> f.toFile().getName();
		}
		if(!input.toFile().exists()){
			throw new MissingFolderException(input);
		}
		if(!output.toFile().exists()){
			throw new MissingFolderException(output);
		}
		
		LOGGER.info("Building differences...");
		final var fd = new FolderDifference(output, input, renameStrategy, flags);
		fd.applyStrategy(backupStrategy, filters, excludes);
	}
	
	/**
	 * Get the instance of this class.
	 *
	 * @return The instance.
	 */
	static Processor getInstance(){
		if(INSTANCE == null){
			INSTANCE = new Processor();
		}
		return INSTANCE;
	}
}
