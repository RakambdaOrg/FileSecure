package fr.mrcraftcod.filesecure.files;

import fr.mrcraftcod.filesecure.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	private final Path base;
	private final Path target;
	private final Pair<String, String> file;
	
	/**
	 * Constructor.
	 *
	 * @param target The target folder (where to copy/move/...).
	 * @param base   The source folder.
	 * @param file   A pair describing the file name in the base (key) and in the target (value).
	 */
	Difference(final Path base, final Path target, final Pair<String, String> file){
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
		generateUniqueName();
		
		final var basePath = base.resolve(file.getKey());
		final var targetPath = target.resolve(file.getValue());
		LOGGER.info("{} file {} to {}", backupStrategy.name(), basePath, targetPath);
		try{
			switch(backupStrategy){
				case MOVE:
					targetPath.getParent().toFile().mkdirs();
					if(targetPath.toFile().exists() || !Files.move(basePath, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists()){
						LOGGER.info("File {} not {}", basePath, backupStrategy.name());
					}
					break;
				case COPY:
					targetPath.getParent().toFile().mkdirs();
					if(targetPath.toFile().exists() || !Files.copy(basePath, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists()){
						LOGGER.info("File {} not {}", basePath, backupStrategy.name());
					}
					break;
			}
		}
		catch(final IOException e){
			LOGGER.warn("Error applying strategy on file", e);
		}
	}
	
	private void generateUniqueName(){
		var i = 0;
		final var name = file.getValue();
		final var ext = name.lastIndexOf(".");
		while(getTargetFolder().resolve(file.getValue()).toFile().exists()){
			final var newName = name.substring(0, ext) + " (" + ++i + ")" + name.substring(ext);
			LOGGER.debug("File '{}' in '{}' already exists in '{}' as '{}', trying with suffix {}", file.getKey(), getBaseFileName(), getTargetFolder(), name, i);
			file.setValue(newName);
		}
		
		if(i > 0){
			LOGGER.info("File {} was renamed to {}", name, file.getValue());
		}
	}
	
	public Path getTargetFolder(){
		return target;
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
