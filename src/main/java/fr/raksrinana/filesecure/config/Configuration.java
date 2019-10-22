package fr.raksrinana.filesecure.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-26.
 *
 * @author Thomas Couchoud
 * @since 2019-03-26
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Configuration{
	private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);
	private static final ObjectReader objectReader;
	@JsonProperty("mappings")
	private List<FolderMapping> mappings;
	
	public Configuration(){
	}
	
	@Nonnull
	public static Optional<Configuration> loadConfiguration(final Path path){
		if(path.toFile().exists()){
			try(final var fis = Files.newBufferedReader(path)){
				return Optional.ofNullable(objectReader.readValue(fis));
			}
			catch(final IOException e){
				LOGGER.error("Failed to read settings in {}", path, e);
			}
		}
		return Optional.empty();
	}
	
	public List<FolderMapping> getMappings(){
		return mappings;
	}
	
	static{
		final var mapper = new ObjectMapper();
		mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker().withFieldVisibility(JsonAutoDetect.Visibility.ANY).withGetterVisibility(JsonAutoDetect.Visibility.NONE).withSetterVisibility(JsonAutoDetect.Visibility.NONE).withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		objectReader = mapper.readerFor(Configuration.class);
	}
}