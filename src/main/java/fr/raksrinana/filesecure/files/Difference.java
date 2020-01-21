package fr.raksrinana.filesecure.files;

import fr.raksrinana.filesecure.config.BackupStrategy;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Represent a file difference.
 */
@Slf4j
class Difference{
	@Getter
	private final Path basePath;
	@Getter
	private final DesiredTarget desiredTarget;
	private String finalName;
	
	/**
	 * Constructor.
	 *
	 * @param basePath      The source folder.
	 * @param desiredTarget The target folder (where to copy/move/...).
	 */
	Difference(@NonNull final Path basePath, @NonNull final DesiredTarget desiredTarget){
		this.desiredTarget = desiredTarget;
		this.basePath = basePath;
		this.finalName = null;
	}
	
	/**
	 * Apply a strategy (copy, move, ...) on this difference causing the base to be copied/moved/... to the target.
	 *
	 * @param backupStrategy The strategy to apply.
	 */
	void applyStrategy(@NonNull final BackupStrategy backupStrategy){
		generateUniqueName();
		final var targetPath = desiredTarget.getTargetFolder().resolve(finalName);
		log.info("{} file {} to {}", backupStrategy.name(), basePath, targetPath);
		try{
			switch(backupStrategy){
				case MOVE:
					Files.createDirectories(desiredTarget.getTargetFolder());
					if(Files.isDirectory(desiredTarget.getTargetFolder()) || !Files.move(basePath, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists()){
						log.info("File {} not {}", basePath, backupStrategy.name());
					}
					break;
				case COPY:
					Files.createDirectories(desiredTarget.getTargetFolder());
					if(Files.isDirectory(desiredTarget.getTargetFolder()) || !Files.copy(basePath, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists()){
						log.info("File {} not {}", basePath, backupStrategy.name());
					}
					break;
			}
		}
		catch(final IOException e){
			log.warn("Error applying strategy on file", e);
		}
	}
	
	private void generateUniqueName(){
		var i = 0;
		final var desiredPath = basePath.getParent().resolve(getDesiredTarget().getDesiredName());
		final var extIndex = getDesiredTarget().getDesiredName().lastIndexOf(".");
		final var prefix = getDesiredTarget().getDesiredName().substring(0, extIndex);
		final var ext = getDesiredTarget().getDesiredName().substring(extIndex);
		finalName = getDesiredTarget().getDesiredName();
		while(Files.exists(getDesiredTarget().getTargetFolder().resolve(finalName))){
			final var newName = String.format("%s (%d)%s", prefix, ++i, ext);
			log.debug("File '{}' already exists in target, trying with suffix {}", desiredPath, i);
			finalName = newName;
		}
		if(i > 0){
			log.info("File {} was renamed to {}", desiredPath, finalName);
		}
	}
}
