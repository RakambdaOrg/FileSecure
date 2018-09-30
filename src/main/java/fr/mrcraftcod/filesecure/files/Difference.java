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
	private final String desiredNamed;
	private String finalName;
	
	/**
	 * Constructor.
	 *
	 * @param target       The target folder (where to copy/move/...).
	 * @param base         The source folder.
	 * @param desiredNamed A pair describing the file name in the base (key) and in the target (value).
	 */
	Difference(final Path base, final Path target, final String desiredNamed){
		this.target = target;
		this.base = base;
		this.desiredNamed = desiredNamed;
		this.finalName = null;
	}
	
	/**
	 * Apply a strategy (copy, move, ...) on this difference causing the base to be copied/moved/... to the target.
	 *
	 * @param backupStrategy The strategy to apply.
	 */
	void applyStrategy(final Processor.BackupStrategy backupStrategy){
		generateUniqueName();
		
		final var targetPath = target.resolve(finalName);
		LOGGER.info("{} file {} to {}", backupStrategy.name(), base, targetPath);
		try{
			switch(backupStrategy){
				case MOVE:
					targetPath.getParent().toFile().mkdirs();
					if(targetPath.toFile().exists() || !Files.move(base, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists()){
						LOGGER.info("File {} not {}", base, backupStrategy.name());
					}
					else
						LOGGER.info("File " + basePath + " not " + backupStrategy.name());
					break;
				case COPY:
					targetPath.getParent().toFile().mkdirs();
					if(targetPath.toFile().exists() || !Files.copy(base, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists()){
						LOGGER.info("File {} not {}", base, backupStrategy.name());
					}
					else
						LOGGER.info("File " + basePath + " not " + backupStrategy.name());
					break;
			}
		}
		catch(final IOException e){
			LOGGER.warn("Error applying strategy on file", e);
		}
	}
	
	private void generateUniqueName(){
		var i = 0;
		final var desiredPath = base.getParent().resolve(desiredNamed);
		final var extIndex = desiredNamed.lastIndexOf(".");
		final var prefix = desiredNamed.substring(0, extIndex);
		final var ext = desiredNamed.substring(extIndex);
		finalName = desiredNamed;
		while(getTargetFolder().resolve(finalName).toFile().exists()){
			final var newName = String.format("%s (%d)%s", prefix, ++i, ext);
			LOGGER.debug("File '{}' already exists in target, trying with suffix {}", desiredPath, i);
			finalName = newName;
		}
		
		if(i > 0){
			LOGGER.info("File {} was renamed to {}", desiredPath, finalName);
		}
	}
	
	public Path getBasePath(){
		return base;
	}
	
	public Path getTargetFolder(){
		return target;
	}
}
