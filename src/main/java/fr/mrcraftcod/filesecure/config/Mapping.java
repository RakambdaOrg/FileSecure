package fr.mrcraftcod.filesecure.config;

import fr.mrcraftcod.nameascreated.strategy.ByDateRenaming;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-26.
 *
 * @author Thomas Couchoud
 * @since 2019-03-26
 */
public class Mapping{
	private static final Logger LOGGER = LoggerFactory.getLogger(Mapping.class);
	
	private final static ByDateRenaming defaultRenaming = new ByDateRenaming();
	
	/**
	 * Default renaming strategy.
	 * Rename the file with a date & time.
	 * <p>
	 * See https://github.com/MrCraftCod/NameAsCreated
	 */
	private static final Function<Path, String> defaultRenameStrategy = f -> {
		try{
			return defaultRenaming.renameFile(f).getName(f.toFile());
		}
		catch(Exception e){
			LOGGER.warn("Error renaming file {}", f);
		}
		return f.toFile().getName();
	};
	
	/**
	 * The folder to backup.
	 */
	private final Path input;
	
	/**
	 * The folder where to backup.
	 */
	private final Path output;
	
	/**
	 * The filters of the files to keep. If empty, all files will be kept.
	 */
	private final ArrayList<Pattern> filters;
	
	/**
	 * The filters of the files not to keep. If empty, all files will be kept.
	 */
	private final ArrayList<Pattern> exclusions;
	
	/**
	 * The options to pass to the processor.
	 */
	private final ArrayList<Option> options;
	
	/**
	 * The strategy used to rename files when executing the backup. If null the original name is kept.
	 */
	private Function<Path, String> renameStrategy;
	/**
	 * The backup strategy to use (copy/move/...). If null BackupStrategy.getDefault() will be used.
	 */
	private BackupStrategy backupStrategy;
	
	private Mapping(final Path input, final Path output){
		this.input = input;
		this.output = output;
		this.renameStrategy = defaultRenameStrategy;
		this.backupStrategy = BackupStrategy.getDefault();
		this.filters = new ArrayList<>();
		this.exclusions = new ArrayList<>();
		this.options = new ArrayList<>();
	}
	
	static Mapping parse(final JSONObject json) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException{
		final var mapping = new Mapping(Paths.get(json.getString("input")), Paths.get(json.getString("output")));
		if(json.has("strategy")){
			mapping.setBackupStrategy(BackupStrategy.getByName(json.getString("strategy")));
		}
		if(json.has("filters")){
			final var filters = json.getJSONArray("filters");
			for(var i = 0; i < filters.length(); i++){
				mapping.addFilter(Pattern.compile(filters.getString(i)));
			}
		}
		if(json.has("excludes")){
			final var excludes = json.getJSONArray("excludes");
			for(var i = 0; i < excludes.length(); i++){
				mapping.addExclusion(Pattern.compile(excludes.getString(i)));
			}
		}
		if(json.has("options")){
			final var options = json.getJSONArray("options");
			for(var i = 0; i < options.length(); i++){
				mapping.addOption(Option.parse(options.getJSONObject(i)));
			}
		}
		return mapping;
	}
	
	private void addFilter(final Pattern pattern){
		this.filters.add(pattern);
	}
	
	private void addExclusion(final Pattern pattern){
		this.exclusions.add(pattern);
	}
	
	private void addOption(final Option option){
		this.options.add(option);
	}
	
	public BackupStrategy getBackupStrategy(){
		return backupStrategy;
	}
	
	public void setBackupStrategy(final BackupStrategy backupStrategy){
		this.backupStrategy = backupStrategy;
	}
	
	public Collection<Pattern> getExclusions(){
		return this.exclusions;
	}
	
	public Collection<Pattern> getFilters(){
		return this.filters;
	}
	
	public Path getInput(){
		return input;
	}
	
	public Collection<Option> getOptions(){
		return this.options;
	}
	
	public Path getOutput(){
		return output;
	}
	
	public Function<Path, String> getRenameStrategy(){
		return renameStrategy;
	}
	
	public void setRenameStrategy(final Function<Path, String> renameStrategy){
		this.renameStrategy = renameStrategy;
	}
}
