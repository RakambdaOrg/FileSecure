package fr.raksrinana.filesecure.files;

import fr.raksrinana.filesecure.config.BackupStrategy;
import fr.raksrinana.filesecure.config.Option;
import fr.raksrinana.filesecure.exceptions.AbandonBackupException;
import fr.raksrinana.filesecure.exceptions.FlagsProcessingException;
import fr.raksrinana.nameascreated.NewFile;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * List all the differences between two folders.
 */
@Slf4j
public class FolderDifference{
	private final Stream<Difference> differences;
	
	/**
	 * Constructor.
	 * Build the difference between these two folders recursively.
	 *
	 * @param target         The target path (where files will be copies/moves/...).
	 * @param base           The base path (where to get the files from).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 * @param flags          The flags to apply to the strategy.
	 * @param recursive      Indicate if we should search recursively (inside folders that we encounter).
	 */
	public FolderDifference(final Path target, final Path base, final Function<Path, NewFile> renameStrategy, final Set<Option> flags, final boolean recursive){
		if(base.toFile().isFile()){
			differences = getDifference(base, target, renameStrategy, flags, false);
		}
		else{
			differences = Arrays.stream(Objects.requireNonNull(base.toFile().listFiles())).parallel().flatMap(f -> getDifference(Paths.get(f.toURI()), target.resolve(f.getName()), renameStrategy, flags, recursive));
		}
	}
	
	/**
	 * Gets raw differences.
	 *
	 * @param input          The input path.
	 * @param output         The output path.
	 * @param renameStrategy The rename strategy to use when we'll apply our backup strategy later.
	 * @param flags          The flags to apply to the strategy.
	 * @param recursive      Indicate if we should search recursively (inside folders that we encounter).
	 *
	 * @return A stream of differences.
	 */
	private Stream<Difference> getDifference(final Path input, final Path output, final Function<Path, NewFile> renameStrategy, final Set<Option> flags, final boolean recursive){
		if(input.toFile().isFile()){
			final var newFile = renameStrategy.apply(input);
			if(Objects.nonNull(newFile)){
				try{
					return Stream.of(new Difference(input, applyFlags(flags, input, newFile, output.getParent())));
				}
				catch(final FlagsProcessingException e){
					log.error("Failed to apply flags", e);
				}
				catch(final AbandonBackupException e){
					log.warn("Did not backup file {} => {}", input, e.getMessage());
				}
			}
			return Stream.empty();
		}
		if(!recursive){
			return Stream.empty();
		}
		return Arrays.stream(Objects.requireNonNull(input.toFile().listFiles())).parallel().flatMap(f -> getDifference(Paths.get(f.toURI()), output.resolve(f.getName()), renameStrategy, flags, recursive));
	}
	
	/**
	 * Apply the flags on the strategy.
	 *
	 * @param flags        The flags to apply.
	 * @param originFile   The path to the file before moving it.
	 * @param newFile      The name of the file after moving it.
	 * @param outputFolder The path where the file will end up.
	 *
	 * @return The new path where the file will end up.
	 *
	 * @throws FlagsProcessingException If an error occurred while applying a flag.
	 * @throws AbandonBackupException   If the file shouldn't be backed up.
	 */
	private DesiredTarget applyFlags(final Set<Option> flags, final Path originFile, final NewFile newFile, final Path outputFolder) throws FlagsProcessingException, AbandonBackupException{
		final var desiredTarget = new DesiredTarget(outputFolder, newFile, newFile.getName(originFile.toFile()));
		try{
			for(final var flag : flags){
				flag.apply(originFile, desiredTarget, newFile, outputFolder);
			}
		}
		catch(final AbandonBackupException e){
			throw e;
		}
		catch(final Exception e){
			log.error("Error applying strategy to file {} in {}", newFile, outputFolder, e);
			throw new FlagsProcessingException("Error applying strategy to file " + newFile + " in " + outputFolder.toFile().getAbsolutePath());
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
