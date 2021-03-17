package fr.raksrinana.filesecure.files;

import fr.raksrinana.filesecure.config.BackupStrategy;
import fr.raksrinana.filesecure.config.FileOption;
import fr.raksrinana.filesecure.config.Rule;
import fr.raksrinana.filesecure.exceptions.AbandonBackupException;
import fr.raksrinana.filesecure.exceptions.FlagsProcessingException;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
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
	private final int depth;
	private final Rule rule;
	
	/**
	 * Constructor.
	 * Build the difference between these two folders recursively.
	 *
	 * @param source The base path (where to get the files from).
	 * @param target The target path (where files will be copies/moves/...).
	 * @param rule   The rules to apply when scanning.
	 */
	public FolderDifference(@NonNull Path source, @NonNull Path target, @NonNull Rule rule){
		this(source, target, rule, 0);
	}
	
	/**
	 * Constructor.
	 * Build the difference between these two folders recursively.
	 *
	 * @param source The base path (where to get the files from).
	 * @param target The target path (where files will be copies/moves/...).
	 * @param rule   The rules to apply when scanning.
	 */
	public FolderDifference(@NonNull Path source, @NonNull Path target, @NonNull Rule rule, int depth){
		this.sourcePath = source;
		this.rule = rule;
		this.depth = depth;
		if(depth > rule.getDepth()){
			childrenDifferences = getChildrenElements(source, target).distinct().sorted().collect(Collectors.toList());
		}
		else{
			childrenDifferences = Set.of();
		}
	}
	
	/**
	 * Gets raw differences.
	 *
	 * @param input  The input path.
	 * @param output The output path.
	 *
	 * @return A stream of differences.
	 */
	@NonNull
	private Stream<DifferenceElement> getChildrenElements(@NonNull Path input, @NonNull Path output){
		try{
			return Files.list(input).parallel()
					.filter(child -> Files.isDirectory(child)
							|| rule.getExcludes().stream().noneMatch(f -> f.matcher(child.getFileName().toString()).matches()))
					.filter(child -> Files.isDirectory(child)
							|| rule.getFilters().isEmpty()
							|| rule.getFilters().stream().anyMatch(f -> f.matcher(child.getFileName().toString()).matches()))
					.map(child -> {
						if(Files.isRegularFile(child)){
							final var newFile = rule.getRenameStrategy().apply(child);
							if(Objects.nonNull(newFile)){
								try{
									return new FileDifference(child, FileOption.applyFlags(rule.getFileOptions(), child, newFile, output));
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
						return new FolderDifference(child, output.resolve(child.getFileName()), rule, this.depth + 1);
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
			rule.getInputFolderOptions().forEach(option -> option.apply(this.getSourcePath(), depth));
		}
	}
}
