package fr.mrcraftcod.filesecure.files;

import fr.mrcraftcod.filesecure.Processor;
import javafx.util.Pair;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * List all the differences between two folders.
 * <p>
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 14/07/2017.
 *
 * @author Thomas Couchoud
 * @since 2017-07-14
 */
public class FolderDifference
{
	private final List<Difference> differences;
	
	/**
	 * Constructor.
	 * Build the difference between these two folders recursively.
	 *
	 * @param target         The target folder (while files will be copies/moves/...).
	 * @param base           The base folder (where to get the files from).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 */
	@SuppressWarnings("WeakerAccess")
	public FolderDifference(Folder target, Folder base, Function<File, String> renameStrategy)
	{
		differences = processInputs(target, base, renameStrategy);
	}
	
	/**
	 * Build the difference between these two folders recursively.
	 *
	 * @param target         The target folder (while files will be copies/moves/...).
	 * @param base           The base folder (where to get the files from).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 *
	 * @return The list of differences.
	 */
	private List<Difference> processInputs(Folder target, Folder base, Function<File, String> renameStrategy)
	{
		Path basePath = base.getPath();
		return Stream.concat(base.getFiles().stream().map(f -> new Pair<>(f, renameStrategy.apply(basePath.resolve(f).toFile()))).filter(pair -> !target.containsFile(pair.getValue())).map(pair -> new Difference(target, base, pair)), base.getFolders().stream().flatMap(folder -> processInputs(folder, target.getFolder(folder.getName()), renameStrategy).stream())).collect(Collectors.toList());
	}
	
	/**
	 * Apply the given strategy (copy/move/...) to all differences.
	 *
	 * @param backupStrategy The strategy to apply.
	 */
	public void applyStrategy(Processor.BackupStrategy backupStrategy)
	{
		differences.forEach(difference -> difference.applyStrategy(backupStrategy));
	}
}
