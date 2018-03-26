package fr.mrcraftcod.filesecure.files;

import fr.mrcraftcod.filesecure.Processor;
import fr.mrcraftcod.utils.base.Log;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
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
		differences = processInputs(base, target, renameStrategy);
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
	private List<Difference> processInputs(Folder base, Folder target, Function<File, String> renameStrategy)
	{
		Path basePath = base.getPath();
		return Stream.concat(base.getFiles().stream().map(f -> new Pair<>(f, renameStrategy.apply(basePath.resolve(f).toFile()))).filter(pair -> {
			int i = 1;
			while(target.containsFile(pair.getValue()))
			{
				String name = pair.getValue();
				int ext = name.lastIndexOf(".");
				String newName = name.substring(0, ext) + " (" + i++ + ")" + name.substring(ext);
				Log.info("File '" + pair.getKey() + "' in '" + base.getPath() + "' already exists in '" + target.getPath() + "' as '" + pair.getValue() + "', trying with suffix " + i);
				pair.setValue(newName);
			}
			return true;
		}).map(pair -> new Difference(base, target, pair)), base.getFolders().stream().flatMap(folder -> processInputs(folder, target.getFolder(folder.getName()), renameStrategy).stream())).collect(Collectors.toList());
	}
	
	/**
	 * Apply the given strategy (copy/move/...) to all differences.
	 *
	 * @param backupStrategy The strategy to apply.
	 * @param filters        The filters of the files to keep. If empty, all files will be kept.
	 * @param excludes       The filters of the files not to keep. If empty, all files will be kept.
	 */
	public void applyStrategy(Processor.BackupStrategy backupStrategy, List<Pattern> filters, List<Pattern> excludes)
	{
		differences.stream().filter(difference -> !excludes.stream().map(f -> f.matcher(difference.getBaseFileName()).matches()).findAny().orElse(false)).filter(difference -> filters.stream().map(f -> f.matcher(difference.getBaseFileName()).matches()).findAny().orElse(true)).forEach(difference -> difference.applyStrategy(backupStrategy));
	}
}
