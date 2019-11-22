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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@Slf4j
public class FolderMapping{
	private final static ByDateRenaming defaultRenaming = new ByDateRenaming();
	/**
	 * The strategy used to rename files when executing the backup. If null the original name is kept.
	 */
	@JsonIgnore
	@Getter
	private Function<Path, NewFile> renameStrategy = f -> {
		try{
			return defaultRenaming.renameFile(f);
		}
		catch(Exception e){
			log.warn("Error renaming file {} => {}", f, e.getMessage());
		}
		return null;
	};
	@JsonProperty("input")
	@Getter
	@JsonDeserialize(using = NioPathDeserializer.class)
	private Path input;
	@JsonProperty("output")
	@Getter
	@JsonDeserialize(using = NioPathDeserializer.class)
	private Path output;
	@JsonProperty("strategy")
	@Getter
	private BackupStrategy strategy = BackupStrategy.getDefault();
	@JsonProperty("filters")
	@Getter
	@JsonDeserialize(contentUsing = PatternDeserializer.class)
	private List<Pattern> filters = new ArrayList<>();
	@JsonProperty("excludes")
	@Getter
	@JsonDeserialize(contentUsing = PatternDeserializer.class)
	private List<Pattern> excludes = new ArrayList<>();
	@JsonProperty("options")
	@Getter
	private Set<Option> options = new HashSet<>();
	@JsonProperty("depth")
	@Getter
	private int depth = -1;
	
	@Override
	public String toString(){
		return getInput() + " ==> " + getOutput() + '[' + getStrategy() + " / " + getFilters().size() + " filters / " + getExcludes().size() + " excludes / " + getOptions().size() + " options" + ']';
	}
}
