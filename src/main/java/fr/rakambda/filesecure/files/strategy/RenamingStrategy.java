package fr.rakambda.filesecure.files.strategy;

import fr.rakambda.filesecure.files.NewFile;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;

@FunctionalInterface
public interface RenamingStrategy{
	/**
	 * Handle the strategy of a file.
	 *
	 * @param path The path of the file to rename.
	 *
	 * @return The infos about how to rename this file.
	 *
	 * @throws Exception If something went wrong.
	 */
	@NotNull NewFile renameFile(@NotNull Path path) throws Exception;
}
