package fr.mrcraftcod.filesecure.files;

import fr.mrcraftcod.filesecure.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
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
	private static final Logger LOGGER = LoggerFactory.getLogger(FolderDifference.class);
	private final List<Difference> differences;
	
	/**
	 * Constructor.
	 * Build the difference between these two folders recursively.
	 *
	 * @param target         The target folder (while files will be copies/moves/...).
	 * @param base           The base folder (where to get the files from).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 */
	FolderDifference(final Folder target, final Folder base, final Function<File, String> renameStrategy)
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
	private List<Difference> processInputs(final Folder base, final Folder target, final Function<File, String> renameStrategy){
		final var basePath = base.getPath();
		return Stream.concat(base.getFiles().stream().map(f -> new Pair<>(f, renameStrategy.apply(basePath.resolve(f).toFile()))).filter(pair -> {
			var i = 0;
			var name = pair.getValue();
			var ext = name.lastIndexOf(".");
			while(target.containsFile(pair.getValue()))
			{
				var newName = name.substring(0, ext) + " (" + ++i + ")" + name.substring(ext);
				LOGGER.info("File '{}' in '{}' already exists in '{}' as '{}', trying with suffix {}", pair.getKey(), base.getPath(), target.getPath(), name, i);
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
	public void applyStrategy(final Processor.BackupStrategy backupStrategy, final List<Pattern> filters, final List<Pattern> excludes)
	{
		differences.stream().filter(difference -> !excludes.stream().map(f -> f.matcher(difference.getBaseFileName()).matches()).findAny().orElse(false)).filter(difference -> filters.stream().map(f -> f.matcher(difference.getBaseFileName()).matches()).findAny().orElse(true)).forEach(difference -> difference.applyStrategy(backupStrategy));
	}
}
