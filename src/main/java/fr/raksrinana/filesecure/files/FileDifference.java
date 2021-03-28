package fr.raksrinana.filesecure.files;

import fr.raksrinana.filesecure.Main;
import fr.raksrinana.filesecure.config.BackupStrategy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
	FileDifference(@NotNull Path sourcePath, @NotNull DesiredTarget desiredTarget){
		this.sourcePath = sourcePath;
		this.desiredTarget = desiredTarget;
	}
	
	/**
	 * Apply a strategy (copy, move, ...) on this difference causing the base to be copied/moved/... to the target.
	 *
	 * @param backupStrategy The strategy to apply.
	 */
	public void applyStrategy(@NotNull BackupStrategy backupStrategy){
		generateUniqueName().ifPresent(finalName -> {
			var targetPath = desiredTarget.getTargetFolder().resolve(finalName);
			log.info("{} file {} to {}", backupStrategy.name(), sourcePath, targetPath);
			if(!Main.parameters.isDryRun()){
				try{
					switch(backupStrategy){
						case MOVE -> {
							Files.createDirectories(desiredTarget.getTargetFolder());
							if(Files.isDirectory(desiredTarget.getTargetFolder())){
								Files.move(sourcePath, targetPath);
							}
						}
						case COPY -> {
							Files.createDirectories(desiredTarget.getTargetFolder());
							if(Files.isDirectory(desiredTarget.getTargetFolder())){
								Files.copy(sourcePath, targetPath);
							}
						}
					}
				}
				catch(Exception e){
					log.warn("Error applying strategy {} on file {}", backupStrategy, sourcePath, e);
				}
			}
		});
	}
	
	private Optional<String> generateUniqueName(){
		try{
			var i = 0;
			var desiredPath = sourcePath.getParent().resolve(getDesiredTarget().getDesiredName());
			var extIndex = getDesiredTarget().getDesiredName().lastIndexOf(".");
			var prefix = getDesiredTarget().getDesiredName().substring(0, extIndex);
			var ext = getDesiredTarget().getDesiredName().substring(extIndex);
			var finalName = getDesiredTarget().getDesiredName();
			while(Files.exists(getDesiredTarget().getTargetFolder().resolve(finalName))){
				var newName = "%s (%d)%s".formatted(prefix, ++i, ext);
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
