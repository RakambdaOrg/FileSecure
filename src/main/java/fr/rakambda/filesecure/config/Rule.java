package fr.rakambda.filesecure.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import fr.rakambda.filesecure.config.options.FileTransformer;
import fr.rakambda.filesecure.config.options.FolderTransformer;
import fr.rakambda.filesecure.utils.json.PatternDeserializer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class Rule{
	@JsonProperty
	private Operation operation = Operation.getDefault();
	@JsonProperty
	private List<Mapping> mappings = new ArrayList<>();
	
	@JsonProperty
	@JsonDeserialize(contentUsing = PatternDeserializer.class)
	private Set<Pattern> filters = new HashSet<>();
	@JsonProperty
	@JsonDeserialize(contentUsing = PatternDeserializer.class)
	private Set<Pattern> excludes = new HashSet<>();
	
	@JsonProperty
	private Set<FileTransformer> fileTransformers = new HashSet<>();
	@JsonProperty
	private Set<FolderTransformer> folderTransformers = new HashSet<>();
	
	@JsonProperty
	private boolean skipIfAlreadyExists = false;
	
	@JsonProperty
	private int maxDepth = Integer.MAX_VALUE;
}
