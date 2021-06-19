package fr.raksrinana.filesecure.files;

import fr.raksrinana.filesecure.config.BackupStrategy;
import fr.raksrinana.filesecure.config.Rule;
import fr.raksrinana.filesecure.config.options.FileOption;
import fr.raksrinana.filesecure.config.options.FolderOption;
import fr.raksrinana.filesecure.exceptions.AbandonBackupException;
import fr.raksrinana.filesecure.exceptions.FlagsProcessingException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static fr.raksrinana.filesecure.config.options.FolderOptionPhase.POST;
import static fr.raksrinana.filesecure.config.options.FolderOptionPhase.PRE;

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
	public FolderDifference(@NotNull Path source, @NotNull Path target, @NotNull Rule rule){
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
	public FolderDifference(@NotNull Path source, @NotNull Path target, @NotNull Rule rule, int depth){
		sourcePath = source;
		this.rule = rule;
		this.depth = depth;
		if(rule.getDepth() < 0 || depth <= rule.getDepth()){
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
	@NotNull
	private Stream<DifferenceElement> getChildrenElements(@NotNull Path input, @NotNull Path output){
		try{
			return Files.list(input).parallel()
					.filter(child -> Files.isDirectory(child)
							|| rule.getExcludes().stream().noneMatch(f -> f.matcher(child.getFileName().toString()).matches()))
					.filter(child -> Files.isDirectory(child)
							|| rule.getFilters().isEmpty()
							|| rule.getFilters().stream().anyMatch(f -> f.matcher(child.getFileName().toString()).matches()))
					.map(child -> {
						try{
							if(Files.isRegularFile(child)){
								var newFile = rule.getRenameStrategy().apply(child);
								if(Objects.nonNull(newFile)){
									var desiredTarget = FileOption.applyFlags(rule.getFileOptions(), child, newFile, output);
									return new FileDifference(child, desiredTarget);
								}
							}
							else{
								var path = FolderOption.applyFlags(rule.getInputFolderOptions(), output.resolve(child.getFileName()), depth, PRE);
								return new FolderDifference(child, path, rule, depth + 1);
							}
						}
						catch(FlagsProcessingException e){
							log.error("Failed to apply folder flags", e);
						}
						catch(AbandonBackupException e){
							log.debug("Did not backup {} => {}", input, e.getMessage());
						}
						return null;
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
	public void applyStrategy(@NotNull BackupStrategy backupStrategy){
		childrenDifferences.forEach(difference -> difference.applyStrategy(backupStrategy));
		if(depth > 0){
			try{
				FolderOption.applyFlags(rule.getInputFolderOptions(), getSourcePath(), depth, POST);
			}
			catch(FlagsProcessingException | AbandonBackupException e){
				log.error("Failed to apply folder flags", e);
			}
		}
	}
}
