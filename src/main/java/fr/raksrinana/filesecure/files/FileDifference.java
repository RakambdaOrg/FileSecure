package fr.raksrinana.filesecure.files;

import fr.raksrinana.filesecure.config.BackupStrategy;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Represent a file difference.
 */
@Slf4j
class FileDifference implements DifferenceElement{
	@Getter
	private final Path sourcePath;
	@Getter
	private final DesiredTarget desiredTarget;
	
	/**
	 * Constructor.
	 *
	 * @param sourcePath    The source folder.
	 * @param desiredTarget The target folder (where to copy/move/...).
	 */
	FileDifference(@NonNull final Path sourcePath, @NonNull final DesiredTarget desiredTarget){
		this.sourcePath = sourcePath;
		this.desiredTarget = desiredTarget;
	}
	
	/**
	 * Apply a strategy (copy, move, ...) on this difference causing the base to be copied/moved/... to the target.
	 *
	 * @param backupStrategy The strategy to apply.
	 */
	public void applyStrategy(@NonNull final BackupStrategy backupStrategy){
		generateUniqueName().ifPresent(finalName -> {
			final var targetPath = desiredTarget.getTargetFolder().resolve(finalName);
			log.info("{} file {} to {}", backupStrategy.name(), sourcePath, targetPath);
			try{
				switch(backupStrategy){
					case MOVE:
						Files.createDirectories(desiredTarget.getTargetFolder());
						if(Files.isDirectory(desiredTarget.getTargetFolder())){
							Files.move(sourcePath, targetPath);
						}
						break;
					case COPY:
						Files.createDirectories(desiredTarget.getTargetFolder());
						if(Files.isDirectory(desiredTarget.getTargetFolder())){
							Files.copy(sourcePath, targetPath);
						}
						break;
				}
			}
			catch(final Exception e){
				log.warn("Error applying strategy {} on file {}", backupStrategy, sourcePath, e);
			}
		});
	}
	
	private Optional<String> generateUniqueName(){
		try{
			var i = 0;
			final var desiredPath = sourcePath.getParent().resolve(getDesiredTarget().getDesiredName());
			final var extIndex = getDesiredTarget().getDesiredName().lastIndexOf(".");
			final var prefix = getDesiredTarget().getDesiredName().substring(0, extIndex);
			final var ext = getDesiredTarget().getDesiredName().substring(extIndex);
			var finalName = getDesiredTarget().getDesiredName();
			while(Files.exists(getDesiredTarget().getTargetFolder().resolve(finalName))){
				final var newName = String.format("%s (%d)%s", prefix, ++i, ext);
				log.debug("File '{}' already exists in target, trying with suffix {}", desiredPath, i);
				finalName = newName;
			}
			if(i > 0){
				log.info("File {} was renamed to {}", desiredPath, finalName);
			}
			return Optional.of(finalName);
		}
		catch(Exception e){
			log.error("Failed to generate unique name", e);
		}
		return Optional.empty();
	}
}
