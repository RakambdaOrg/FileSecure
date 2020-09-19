package fr.raksrinana.filesecure.files;

import fr.raksrinana.filesecure.config.BackupStrategy;
import fr.raksrinana.filesecure.config.FileOption;
import fr.raksrinana.filesecure.config.FolderOption;
import fr.raksrinana.filesecure.exceptions.AbandonBackupException;
import fr.raksrinana.filesecure.exceptions.FlagsProcessingException;
import fr.raksrinana.nameascreated.NewFile;
import lombok.Getter;
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
public class FolderDifference implements DifferenceElement{
	private final Collection<DifferenceElement> childrenDifferences;
	@Getter
	private final Path sourcePath;
	private final Collection<FolderOption> folderOptions;
	private final int depth;
	
	/**
	 * Constructor.
	 * Build the difference between these two folders recursively.
	 *
	 * @param source         The base path (where to get the files from).
	 * @param target         The target path (where files will be copies/moves/...).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 * @param fileOptions    The flags to apply to the strategy.
	 * @param depth          The number of subfolder to visit. A negative value is infinite.
	 * @param filters        The filters of the files to keep. If empty, all files will be kept.
	 * @param excludes       The filters of the files not to keep. If empty, all files will be kept.
	 */
	public FolderDifference(@NonNull final Path source, @NonNull final Path target, @NonNull final Function<Path, NewFile> renameStrategy, @NonNull final Set<FolderOption> folderOptions, @NonNull final Set<FileOption> fileOptions, final int depth, @NonNull final Collection<Pattern> filters, @NonNull final Collection<Pattern> excludes){
		this(source, target, renameStrategy, folderOptions, fileOptions, depth, filters, excludes, 0);
	}
	
	/**
	 * Constructor.
	 * Build the difference between these two folders recursively.
	 *
	 * @param source         The base path (where to get the files from).
	 * @param target         The target path (where files will be copies/moves/...).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 * @param fileOptions    The flags to apply to the strategy.
	 * @param maxDepth       The number of subfolder to visit. A negative value is infinite.
	 * @param filters        The filters of the files to keep. If empty, all files will be kept.
	 * @param excludes       The filters of the files not to keep. If empty, all files will be kept.
	 */
	public FolderDifference(@NonNull final Path source, @NonNull final Path target, @NonNull final Function<Path, NewFile> renameStrategy, @NonNull final Set<FolderOption> folderOptions, @NonNull final Set<FileOption> fileOptions, final int maxDepth, @NonNull final Collection<Pattern> filters, @NonNull final Collection<Pattern> excludes, int depth){
		this.sourcePath = source;
		this.folderOptions = folderOptions;
		this.depth = depth;
		if(maxDepth != 0){
			childrenDifferences = getChildrenElements(source, target, renameStrategy, folderOptions, fileOptions, maxDepth, filters, excludes).distinct().sorted().collect(Collectors.toList());
		}
		else{
			childrenDifferences = Set.of();
		}
	}
	
	/**
	 * Gets raw differences.
	 *
	 * @param input          The input path.
	 * @param output         The output path.
	 * @param renameStrategy The rename strategy to use when we'll apply our backup strategy later.
	 * @param fileOptions    The flags to apply to the strategy.
	 * @param maxDepth       The number of subfolder to visit. A negative value is infinite.
	 * @param filters        The filters of the files to keep. If empty, all files will be kept.
	 * @param excludes       The filters of the files not to keep. If empty, all files will be kept.
	 *
	 * @return A stream of differences.
	 */
	@NonNull
	private Stream<DifferenceElement> getChildrenElements(@NonNull final Path input, @NonNull final Path output, @NonNull final Function<Path, NewFile> renameStrategy, @NonNull final Set<FolderOption> folderOptions, @NonNull final Set<FileOption> fileOptions, final int maxDepth, @NonNull final Collection<Pattern> filters, @NonNull final Collection<Pattern> excludes){
		try{
			return Files.list(input).parallel()
					.filter(child -> Files.isDirectory(child)
							|| !excludes.stream().map(f -> f.matcher(child.getFileName().toString()).matches()).findAny().orElse(false))
					.filter(child -> Files.isDirectory(child)
							|| filters.stream().map(f -> f.matcher(child.getFileName().toString()).matches()).findAny().orElse(true))
					.map(child -> {
						if(Files.isRegularFile(child)){
							final var newFile = renameStrategy.apply(child);
							if(Objects.nonNull(newFile)){
								try{
									return new FileDifference(child, FileOption.applyFlags(fileOptions, child, newFile, output));
								}
								catch(final FlagsProcessingException e){
									log.error("Failed to apply flags", e);
								}
								catch(final AbandonBackupException e){
									log.debug("Did not backup file {} => {}", input, e.getMessage());
								}
							}
							return null;
						}
						return new FolderDifference(child, output.resolve(child.getFileName()), renameStrategy, folderOptions, fileOptions, maxDepth - 1, filters, excludes, this.depth + 1);
					}).filter(Objects::nonNull);
		}
		catch(IOException e){
			log.error("Failed to list directory {}", input, e);
		}
		return Stream.empty();
	}
	
	/**
	 * Apply the given strategy (copy/move/...) to all differences.
	 *
	 * @param backupStrategy The strategy to apply.
	 */
	public void applyStrategy(@NonNull final BackupStrategy backupStrategy){
		childrenDifferences.forEach(difference -> difference.applyStrategy(backupStrategy));
		if(this.depth > 0){
			this.folderOptions.forEach(option -> option.apply(this.getSourcePath()));
		}
	}
}
