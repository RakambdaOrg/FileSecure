package fr.rakambda.filesecure.files.renaming;

import fr.rakambda.filesecure.files.strategy.RenamingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class RenameDate{
	/**
	 * Rename files with their creation date.
	 *
	 * @param renamingStrategy The strategy to rename the files.
	 * @param paths            The list of files to modify.
	 */
	public static void processFiles(@NotNull RenamingStrategy renamingStrategy, @NotNull List<Path> paths){
		for(var path : paths){
			try{
				if(Files.isRegularFile(path) && path.getFileName().toString().contains(".") && !path.getFileName().toString().startsWith(".")){
					try{
						var newFile = renamingStrategy.renameFile(path);
						var fileTo = path.getParent().resolve(newFile.getName(path));
						if(fileTo.getFileName().toString().equals(path.toFile().getName())){
							continue;
						}
						if(Files.exists(fileTo)){
							log.warn("Couldn't rename file {} to {}, file already exists", path, fileTo);
							continue;
						}
						newFile.moveTo(fileTo);
					}
					catch(Exception e){
						log.error("Error strategy file {}: {}", path, e.getMessage());
					}
				}
			}
			catch(Exception e){
				log.error("Error processing file {}", path, e);
			}
		}
	}
}
