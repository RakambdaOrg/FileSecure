package fr.mrcraftcod.filesecure.files;

import fr.mrcraftcod.filesecure.Flags;
import fr.mrcraftcod.filesecure.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss");
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
	public FolderDifference(final Path target, final Path base, final Function<Path, String> renameStrategy, final List<Flags> flags){
		differences = processInputs(base, target, renameStrategy, flags);
	}
	
	/**
	 * Build the difference between these two folders recursively.
	 *
	 * @param base           The base path (where to get the files from).
	 * @param target         The target path (where files will be copies/moves/...).
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 * @param flags
	 *
	 * @return The list of differences.
	 */
	private List<Difference> processInputs(final Path base, final Path target, final Function<Path, String> renameStrategy, final List<Flags> flags){
		getDifference(base, target, renameStrategy, flags);
		return Arrays.stream(Objects.requireNonNull(base.toFile().listFiles())).parallel().flatMap(f -> getDifference(Paths.get(f.toURI()), target.resolve(f.getName()), renameStrategy, flags)).collect(Collectors.toList());
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
	private Stream<Difference> getDifference(final Path input, final Path output, final Function<Path, String> renameStrategy, final List<Flags> flags){
		if(input.toFile().isFile()){
			final var newFileName = renameStrategy.apply(input);
			return Stream.of(new Difference(input, applyFlags(flags, newFileName, output.getParent()), newFileName));
		}
		return Arrays.stream(Objects.requireNonNull(input.toFile().listFiles())).parallel().flatMap(f -> getDifference(Paths.get(f.toURI()), output.resolve(f.getName()), renameStrategy, flags));
	}
	
	/**
	 * Apply the flags on the strategy.
	 *
	 * @param flags       The flags to apply.
	 * @param newFileName The name of the file after moving it.
	 * @param parent      The path where the file will end up.
	 *
	 * @return The new path where the file will end up.
	 */
	private Path applyFlags(final List<Flags> flags, final String newFileName, final Path parent){
		try{
			if(flags.contains(Flags.UNIQUE_FOLDER_PER_DAY)){
				return getUniqueDayFolder(newFileName, parent);
			}
			else if(flags.contains(Flags.FOLDER_PER_DAY)){
				return getDayFolder(newFileName, parent);
			}
			else if(flags.contains(Flags.FOLDER_PER_MONTH)){
				return getMonthFolder(newFileName, parent);
			}
			else if(flags.contains(Flags.FOLDER_PER_YEAR)){
				return getYearFolder(newFileName, parent);
			}
		}
		catch(Exception e){
			LOGGER.error("Error applying strategy to file {} in {}", newFileName, parent, e);
		}
		return parent;
	}
	
	/**
	 * Move a file into a folder yyyy-mm-dd.
	 *
	 * @param fileName The name of the file.
	 * @param folder   The original destination.
	 *
	 * @return The new destination.
	 */
	private Path getUniqueDayFolder(final String fileName, final Path folder){
		try{
			final var date = LocalDateTime.parse(fileName.substring(0, fileName.lastIndexOf(".")), DATE_TIME_FORMATTER);
			return folder.resolve(String.format("%4d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
		}
		catch(final Exception e){
			LOGGER.error("Failed to build unique day folder for {} in {}", fileName, folder, e);
		}
		return folder;
	}
	
	/**
	 * Move a file into a folder yyyy/mm/dd.
	 *
	 * @param fileName The name of the file.
	 * @param folder   The original destination.
	 *
	 * @return The new destination.
	 */
	private Path getDayFolder(final String fileName, final Path folder){
		try{
			final var date = LocalDateTime.parse(fileName.substring(0, fileName.lastIndexOf(".")), DATE_TIME_FORMATTER);
			return folder.resolve(String.format("%4d", date.getYear())).resolve(String.format("%02d", date.getMonthValue())).resolve(String.format("%02d", date.getDayOfMonth()));
		}
		catch(final Exception e){
			LOGGER.error("Failed to build day folder for {} in {}", fileName, folder, e);
		}
		return folder;
	}
	
	/**
	 * Move a file into a folder yyyy/mm.
	 *
	 * @param fileName The name of the file.
	 * @param folder   The original destination.
	 *
	 * @return The new destination.
	 */
	private Path getMonthFolder(final String fileName, final Path folder){
		try{
			final var date = LocalDateTime.parse(fileName.substring(0, fileName.lastIndexOf(".")), DATE_TIME_FORMATTER);
			return folder.resolve(String.format("%4d", date.getYear())).resolve(String.format("%02d", date.getMonthValue()));
		}
		catch(final Exception e){
			LOGGER.error("Failed to build month folder for {} in {}", fileName, folder, e);
		}
		return folder;
	}
	
	/**
	 * Move a file into a folder yyyy.
	 *
	 * @param fileName The name of the file.
	 * @param folder   The original destination.
	 *
	 * @return The new destination.
	 */
	private Path getYearFolder(final String fileName, final Path folder){
		try{
			final var date = LocalDateTime.parse(fileName.substring(0, fileName.lastIndexOf(".")), DATE_TIME_FORMATTER);
			return folder.resolve(String.format("%4d", date.getYear()));
		}
		catch(final Exception e){
			LOGGER.error("Failed to build year folder for {} in {}", fileName, folder, e);
		}
		return folder;
	}
	
	/**
	 * Apply the given strategy (copy/move/...) to all differences.
	 *
	 * @param backupStrategy The strategy to apply.
	 * @param filters        The filters of the files to keep. If empty, all files will be kept.
	 * @param excludes       The filters of the files not to keep. If empty, all files will be kept.
	 */
	public void applyStrategy(final Processor.BackupStrategy backupStrategy, final List<Pattern> filters, final List<Pattern> excludes){
		differences.stream().filter(difference -> !excludes.stream().map(f -> f.matcher(difference.getBasePath().getFileName().toString()).matches()).findAny().orElse(false)).filter(difference -> filters.stream().map(f -> f.matcher(difference.getBasePath().getFileName().toString()).matches()).findAny().orElse(true)).forEach(difference -> difference.applyStrategy(backupStrategy));
	}
}
