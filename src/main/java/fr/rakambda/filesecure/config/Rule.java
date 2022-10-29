package fr.rakambda.filesecure.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import fr.rakambda.filesecure.config.options.FileOption;
import fr.rakambda.filesecure.config.options.FolderOption;
import fr.rakambda.filesecure.files.NewFile;
import fr.rakambda.filesecure.files.strategy.ByDateRenaming;
import fr.rakambda.filesecure.utils.json.PatternDeserializer;
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
public class Rule{
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
	@JsonProperty("strategy")
	@Getter
	private BackupStrategy strategy = BackupStrategy.getDefault();
	@JsonProperty("filters")
	@Getter
	@JsonDeserialize(contentUsing = PatternDeserializer.class)
	private Set<Pattern> filters = new HashSet<>();
	@JsonProperty("excludes")
	@Getter
	@JsonDeserialize(contentUsing = PatternDeserializer.class)
	private Set<Pattern> excludes = new HashSet<>();
	@JsonProperty("options")
	@Getter
	private Set<FileOption> fileOptions = new HashSet<>();
	@JsonProperty("depth")
	@Getter
	private int depth = -1;
	@JsonProperty("inputFolderOptions")
	@Getter
	private Set<FolderOption> inputFolderOptions = new HashSet<>();
	@JsonProperty("mappings")
	@Getter
	private List<Mapping> mappings = new ArrayList<>();
}
