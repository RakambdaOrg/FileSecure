package fr.raksrinana.filesecure.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ext.NioPathDeserializer;
import fr.raksrinana.filesecure.utils.json.PatternDeserializer;
import fr.raksrinana.nameascreated.NewFile;
import fr.raksrinana.nameascreated.strategy.ByDateRenaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FolderMapping{
	private static final Logger LOGGER = LoggerFactory.getLogger(FolderMapping.class);
	private final static ByDateRenaming defaultRenaming = new ByDateRenaming();
	/**
	 * The strategy used to rename files when executing the backup. If null the original name is kept.
	 */
	@JsonIgnore
	private Function<Path, NewFile> renameStrategy = f -> {
		try{
			return defaultRenaming.renameFile(f);
		}
		catch(Exception e){
			LOGGER.warn("Error renaming file {} => {}", f, e.getMessage());
		}
		return null;
	};
	@JsonProperty("input")
	@JsonDeserialize(using = NioPathDeserializer.class)
	private Path input;
	@JsonProperty("output")
	@JsonDeserialize(using = NioPathDeserializer.class)
	private Path output;
	@JsonProperty("strategy")
	private BackupStrategy strategy = BackupStrategy.getDefault();
	@JsonProperty("filters")
	@JsonDeserialize(contentUsing = PatternDeserializer.class)
	private List<Pattern> filters = new ArrayList<>();
	@JsonProperty("excludes")
	@JsonDeserialize(contentUsing = PatternDeserializer.class)
	private List<Pattern> excludes = new ArrayList<>();
	@JsonProperty("options")
	private Set<Option> options = new HashSet<>();
	
	@Override
	public String toString(){
		return getInput() + " ==> " + getOutput() + '[' + getStrategy() + " / " + getFilters().size() + " filters / " + getExcludes().size() + " excludes / " + getOptions().size() + " options" + ']';
	}
	
	public Path getInput(){
		return input;
	}
	
	public Path getOutput(){
		return output;
	}
	
	public BackupStrategy getStrategy(){
		return strategy;
	}
	
	public List<Pattern> getFilters(){
		return filters;
	}
	
	public List<Pattern> getExcludes(){
		return excludes;
	}
	
	public Set<Option> getOptions(){
		return options;
	}
	
	public Function<Path, NewFile> getRenameStrategy(){
		return renameStrategy;
	}
}
