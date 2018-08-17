package fr.mrcraftcod.filesecure.files;

import fr.mrcraftcod.filesecure.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Represent a file difference.
 * <p>
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 08/02/18.
 *
 * @author Thomas Couchoud
 * @since 2018-02-08
 */
class Difference{
	private final static Logger LOGGER = LoggerFactory.getLogger(Difference.class);
	private final Folder base;
	private final Folder target;
	private final Pair<String, String> file;
	
	/**
	 * Constructor.
	 *
	 * @param target The target folder (where to copy/move/...).
	 * @param base   The source folder.
	 * @param file   A pair describing the file name in the base (key) and in the target (value).
	 */
	Difference(final Folder base, final Folder target, final Pair<String, String> file){
		this.target = target;
		this.base = base;
		this.file = file;
	}
	
	/**
	 * Apply a strategy (copy, move, ...) on this difference causing the base to be copied/moved/... to the target.
	 *
	 * @param backupStrategy The strategy to apply.
	 */
	void applyStrategy(final Processor.BackupStrategy backupStrategy){
		final var basePath = base.getPath().resolve(file.getKey());
		final var targetPath = target.getPath().resolve(file.getValue());
		LOGGER.info("{} file {} to {}", backupStrategy.name(), basePath, targetPath);
		try{
			switch(backupStrategy){
				case MOVE:
					targetPath.getParent().toFile().mkdirs();
					if(!targetPath.toFile().exists() && Files.move(basePath, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists()){
						base.getFiles().remove(basePath.getFileName().toString());
						target.getFiles().add(targetPath.getFileName().toString());
					}
					else{
						LOGGER.info("File {} not {}", basePath, backupStrategy.name());
					}
					break;
				case COPY:
					targetPath.getParent().toFile().mkdirs();
					if(!targetPath.toFile().exists() && Files.copy(basePath, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists()){
						target.getFiles().add(targetPath.getFileName().toString());
					}
					else{
						LOGGER.info("File {} not {}", basePath, backupStrategy.name());
					}
					break;
			}
		}
		catch(final IOException e){
			LOGGER.warn("Error applying strategy on file", e);
		}
	}
	
	/**
	 * Get the name of the base file.
	 *
	 * @return The base file name.
	 */
	String getBaseFileName(){
		return file.getKey();
	}
}
