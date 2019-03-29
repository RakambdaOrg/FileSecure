package fr.mrcraftcod.filesecure.files;

import fr.mrcraftcod.filesecure.config.BackupStrategy;
import fr.mrcraftcod.filesecure.config.Option;
import fr.mrcraftcod.filesecure.exceptions.AbandonBackupException;
import fr.mrcraftcod.filesecure.exceptions.FlagsProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
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
	private final Stream<Difference> differences;
	
	/**
	 * Constructor.
	 * Build the difference between these two folders recursively.
	 *
	 * @param target         The target path (where files will be copies/moves/...).
	 * @param base           The base path (where to get the files from).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 * @param flags          The flags to apply to the strategy.
	 */
	public FolderDifference(final Path target, final Path base, final Function<Path, String> renameStrategy, final List<Option> flags){
		getDifference(base, target, renameStrategy, flags);
		differences = Arrays.stream(Objects.requireNonNull(base.toFile().listFiles())).parallel().flatMap(f -> getDifference(Paths.get(f.toURI()), target.resolve(f.getName()), renameStrategy, flags));
	}
	
	/**
	 * Gets raw differences.
	 *
	 * @param input          The input path.
	 * @param output         The output path.
	 * @param renameStrategy The rename strategy to use when we'll apply our backup strategy later.
	 * @param flags          The flags to apply to the strategy.
	 *
	 * @return A stream of differences.
	 */
	private Stream<Difference> getDifference(final Path input, final Path output, final Function<Path, String> renameStrategy, final List<Option> flags){
		if(input.toFile().isFile()){
			final var newFileName = renameStrategy.apply(input);
			try{
				return Stream.of(new Difference(input, applyFlags(flags, input, newFileName, output.getParent())));
			}
			catch(final FlagsProcessingException e){
				LOGGER.error("Failed to apply flags", e);
			}
			catch(final AbandonBackupException e){
				LOGGER.warn("Did not backup file {}", input, e);
			}
			return Stream.empty();
		}
		return Arrays.stream(Objects.requireNonNull(input.toFile().listFiles())).parallel().flatMap(f -> getDifference(Paths.get(f.toURI()), output.resolve(f.getName()), renameStrategy, flags));
	}
	
	/**
	 * Apply the flags on the strategy.
	 *
	 * @param flags        The flags to apply.
	 * @param originFile   The path to the file before moving it.
	 * @param newFileName  The name of the file after moving it.
	 * @param outputFolder The path where the file will end up.
	 *
	 * @return The new path where the file will end up.
	 *
	 * @throws FlagsProcessingException If an error occurred while applying a flag.
	 * @throws AbandonBackupException If the file shouldn't be backed up.
	 */
	private DesiredTarget applyFlags(final List<Option> flags, final Path originFile, final String newFileName, final Path outputFolder) throws FlagsProcessingException, AbandonBackupException{
		final var desiredTarget = new DesiredTarget(outputFolder, newFileName);
		try{
			flags.sort(Comparator.comparing(Option::getPriority));
			for(final var flag : flags){
				flag.apply(originFile, desiredTarget, newFileName, outputFolder);
			}
		}
		catch(final AbandonBackupException e){
			throw e;
		}
		catch(final Exception e){
			LOGGER.error("Error applying strategy to file {} in {}", newFileName, outputFolder, e);
			throw new FlagsProcessingException("Error applying strategy to file " + newFileName + " in " + outputFolder.toFile().getAbsolutePath());
		}
		return desiredTarget;
	}
	
	/**
	 * Apply the given strategy (copy/move/...) to all differences.
	 *
	 * @param backupStrategy The strategy to apply.
	 * @param filters        The filters of the files to keep. If empty, all files will be kept.
	 * @param excludes       The filters of the files not to keep. If empty, all files will be kept.
	 */
	public void applyStrategy(final BackupStrategy backupStrategy, final Collection<Pattern> filters, final Collection<Pattern> excludes){
		differences.filter(difference -> !excludes.stream().map(f -> f.matcher(difference.getBasePath().getFileName().toString()).matches()).findAny().orElse(false)).filter(difference -> filters.stream().map(f -> f.matcher(difference.getBasePath().getFileName().toString()).matches()).findAny().orElse(true)).forEach(difference -> difference.applyStrategy(backupStrategy));
	}
}
