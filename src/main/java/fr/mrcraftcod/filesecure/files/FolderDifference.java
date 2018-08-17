package fr.mrcraftcod.filesecure.files;

import fr.mrcraftcod.filesecure.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
public class FolderDifference{
	private static final Logger LOGGER = LoggerFactory.getLogger(FolderDifference.class);
	private final List<Difference> differences;
	
	/**
	 * Constructor.
	 * Build the difference between these two folders recursively.
	 *
	 * @param target         The target path (where files will be copies/moves/...).
	 * @param base           The base path (where to get the files from).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 */
	public FolderDifference(final Path target, final Path base, final Function<File, String> renameStrategy){
		differences = processInputs(base, target, renameStrategy);
	}
	
	/**
	 * Build the difference between these two folders recursively.
	 *
	 * @param target         The target path (where files will be copies/moves/...).
	 * @param base           The base path (where to get the files from).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 *
	 * @return The list of differences.
	 */
	private List<Difference> processInputs(final Path base, final Path target, final Function<File, String> renameStrategy){
		getDifference(base, target, renameStrategy);
		return Arrays.stream(Objects.requireNonNull(base.toFile().listFiles())).flatMap(f -> getDifference(Paths.get(f.toURI()), target.resolve(".."), renameStrategy)).collect(Collectors.toList());
	}
	
	/**
	 * Gets raw differences.
	 *
	 * @param input          The input path.
	 * @param output         The output path.
	 * @param renameStrategy The rename strategy to use when we'll apply our backup strategy later.
	 *
	 * @return A stream of differences.
	 */
	private Stream<Difference> getDifference(final Path input, final Path output, final Function<File, String> renameStrategy){
		if(input.toFile().isFile()){
			return Stream.of(new Difference(input.getParent(), output.getParent(), new Pair<>(input.getFileName().toString(), renameStrategy.apply(input.toFile()))));
		}
		return Arrays.stream(Objects.requireNonNull(input.toFile().listFiles())).flatMap(f -> getDifference(Paths.get(f.toURI()), output.resolve(f.getName()), renameStrategy));
	}
	
	/**
	 * Apply the given strategy (copy/move/...) to all differences.
	 *
	 * @param backupStrategy The strategy to apply.
	 * @param filters        The filters of the files to keep. If empty, all files will be kept.
	 * @param excludes       The filters of the files not to keep. If empty, all files will be kept.
	 */
	public void applyStrategy(final Processor.BackupStrategy backupStrategy, final List<Pattern> filters, final List<Pattern> excludes){
		differences.stream().filter(difference -> !excludes.stream().map(f -> f.matcher(difference.getBaseFileName()).matches()).findAny().orElse(false)).filter(difference -> filters.stream().map(f -> f.matcher(difference.getBaseFileName()).matches()).findAny().orElse(true)).forEach(difference -> difference.applyStrategy(backupStrategy));
	}
}
