package fr.raksrinana.filesecure.files;

import fr.raksrinana.filesecure.config.BackupStrategy;
import fr.raksrinana.filesecure.config.Option;
import fr.raksrinana.filesecure.exceptions.AbandonBackupException;
import fr.raksrinana.filesecure.exceptions.FlagsProcessingException;
import fr.raksrinana.nameascreated.NewFile;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * List all the differences between two folders.
 */
@Slf4j
public class FolderDifference{
	private final Set<Difference> differences;
	
	/**
	 * Constructor.
	 * Build the difference between these two folders recursively.
	 *
	 * @param target         The target path (where files will be copies/moves/...).
	 * @param base           The base path (where to get the files from).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 * @param flags          The flags to apply to the strategy.
	 * @param depth          The number of subfolder to visit. A negative value is infinite.
	 */
	public FolderDifference(@NonNull final Path target, @NonNull final Path base, @NonNull final Function<Path, NewFile> renameStrategy, @NonNull final Set<Option> flags, final int depth){
		differences = getDifference(base, target, renameStrategy, flags, depth).collect(Collectors.toSet());
	}
	
	/**
	 * Gets raw differences.
	 *
	 * @param input          The input path.
	 * @param output         The output path.
	 * @param renameStrategy The rename strategy to use when we'll apply our backup strategy later.
	 * @param flags          The flags to apply to the strategy.
	 * @param depth          The number of subfolder to visit. A negative value is infinite.
	 *
	 * @return A stream of differences.
	 */
	@NonNull
	private Stream<Difference> getDifference(@NonNull final Path input, @NonNull final Path output, @NonNull final Function<Path, NewFile> renameStrategy, @NonNull final Set<Option> flags, final int depth){
		if(Files.isRegularFile(input)){
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
		if(depth == 0){
			return Stream.empty();
		}
		try{
			return Files.list(input).parallel().flatMap(f -> getDifference(f, output.resolve(f.getFileName()), renameStrategy, flags, depth - 1));
		}
		catch(IOException e){
			log.error("Failed to list directory {}", input, e);
		}
		return Stream.empty();
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
	@NonNull
	private DesiredTarget applyFlags(@NonNull final Set<Option> flags, @NonNull final Path originFile, @NonNull final NewFile newFile, @NonNull final Path outputFolder) throws FlagsProcessingException, AbandonBackupException{
		final var desiredTarget = new DesiredTarget(outputFolder, newFile, newFile.getName(originFile));
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
	public void applyStrategy(@NonNull final BackupStrategy backupStrategy, @NonNull final Collection<Pattern> filters, @NonNull final Collection<Pattern> excludes){
		differences.stream().filter(difference -> !excludes.stream().map(f -> f.matcher(difference.getBasePath().getFileName().toString()).matches()).findAny().orElse(false)).filter(difference -> filters.stream().map(f -> f.matcher(difference.getBasePath().getFileName().toString()).matches()).findAny().orElse(true)).forEach(difference -> difference.applyStrategy(backupStrategy));
	}
}
