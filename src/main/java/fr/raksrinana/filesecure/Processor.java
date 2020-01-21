package fr.raksrinana.filesecure;

import fr.raksrinana.filesecure.config.FolderMapping;
import fr.raksrinana.filesecure.exceptions.MissingFolderException;
import fr.raksrinana.filesecure.files.FolderDifference;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Files;

/**
 * Process a pair of folder, one being the source of the backup, and the other the destination.
 * Singleton.
 */
@Slf4j
public class Processor{
	private final FolderMapping mapping;
	
	/**
	 * Constructor.
	 *
	 * @param mapping The configuration.
	 *
	 * @throws MissingFolderException If one of the folders doesn't exists.
	 */
	public Processor(@NonNull final FolderMapping mapping) throws MissingFolderException{
		if(!Files.exists(mapping.getInput())){
			throw new MissingFolderException(mapping.getInput());
		}
		if(!Files.exists(mapping.getOutput())){
			throw new MissingFolderException(mapping.getOutput(), "output");
		}
		this.mapping = mapping;
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
	 */
	void process(){
		log.info("Processing ({}) {} ==> {}", mapping.getStrategy().name(), mapping.getInput(), mapping.getOutput());
		log.info("Building differences...");
		final var fd = new FolderDifference(mapping.getOutput(), mapping.getInput(), mapping.getRenameStrategy(), mapping.getOptions(), mapping.getDepth());
		fd.applyStrategy(mapping.getStrategy(), mapping.getFilters(), mapping.getExcludes());
	}
}
