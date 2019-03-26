package fr.mrcraftcod.filesecure;

import fr.mrcraftcod.filesecure.config.Configuration;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Main class.
 * <p>
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 19/12/2016.
 *
 * @author Thomas Couchoud
 * @since 2016-12-19
 */
public class Main{
	private final static Logger LOGGER = LoggerFactory.getLogger(Main.class);
	
	/**
	 * Main method.
	 *
	 * @param args The arguments of the program:
	 *             0: A path to the config file, to the json format.
	 */
	public static void main(final String[] args){
		Configuration configuration = null;
		if(args.length > 0){
			final var path = Paths.get(args[0]);
			if(path.toFile().exists()){
				try{
					configuration = Configuration.parse(new JSONObject(String.join("\n", Files.readAllLines(path))));
				}
				catch(final Exception e){
					LOGGER.warn("Couldn't read the configuration file", e);
				}
			}
			else{
				LOGGER.error("The specified config file doesn't exists");
			}
		}
		else{
			LOGGER.error("No config file given");
		}
		if(Objects.nonNull(configuration)){
			for(final var mapping : configuration.getMappings()){
				try{
					final var processor = new Processor(mapping);
					processor.process();
				}
				catch(final Exception e){
					LOGGER.error("Failed to run processor", e);
				}
			}
		}
	}
}
