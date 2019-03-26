package fr.mrcraftcod.filesecure.files;

import fr.mrcraftcod.filesecure.config.BackupStrategy;
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
@SuppressWarnings("WeakerAccess")
class Difference{
	private final static Logger LOGGER = LoggerFactory.getLogger(Difference.class);
	private final Path base;
	private final DesiredTarget target;
	private String finalName;
	
	/**
	 * Constructor.
	 *
	 * @param base         The source folder.
	 * @param target       The target folder (where to copy/move/...).
	 */
	Difference(final Path base, final DesiredTarget target){
		this.target = target;
		this.base = base;
		this.finalName = null;
	}
	
	/**
	 * Apply a strategy (copy, move, ...) on this difference causing the base to be copied/moved/... to the target.
	 *
	 * @param backupStrategy The strategy to apply.
	 */
	void applyStrategy(final BackupStrategy backupStrategy){
		generateUniqueName();
		
		final var targetPath = target.getTargetFolder().resolve(finalName);
		LOGGER.info("{} file {} to {}", backupStrategy.name(), base, targetPath);
		try{
			switch(backupStrategy){
				case MOVE:
					targetPath.getParent().toFile().mkdirs();
					if(targetPath.toFile().exists() || !Files.move(base, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists()){
						LOGGER.info("File {} not {}", base, backupStrategy.name());
					}
					break;
				case COPY:
					targetPath.getParent().toFile().mkdirs();
					if(targetPath.toFile().exists() || !Files.copy(base, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists()){
						LOGGER.info("File {} not {}", base, backupStrategy.name());
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
		final var desiredPath = base.getParent().resolve(getDesiredTarget().getDesiredName());
		final var extIndex = getDesiredTarget().getDesiredName().lastIndexOf(".");
		final var prefix = getDesiredTarget().getDesiredName().substring(0, extIndex);
		final var ext = getDesiredTarget().getDesiredName().substring(extIndex);
		finalName = getDesiredTarget().getDesiredName();
		while(getDesiredTarget().getTargetFolder().resolve(finalName).toFile().exists()){
			final var newName = String.format("%s (%d)%s", prefix, ++i, ext);
			LOGGER.debug("File '{}' already exists in target, trying with suffix {}", desiredPath, i);
			finalName = newName;
		}
		
		if(i > 0){
			LOGGER.info("File {} was renamed to {}", desiredPath, finalName);
		}
	}
	
	public DesiredTarget getDesiredTarget(){
		return target;
	}
	
	public Path getBasePath(){
		return base;
	}
}
