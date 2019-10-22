package fr.raksrinana.filesecure;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import fr.raksrinana.filesecure.config.Configuration;
import fr.raksrinana.filesecure.exceptions.MissingFolderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		final var parameters = new CLIParameters();
		try{
			JCommander.newBuilder().addObject(parameters).build().parse(args);
		}
		catch(final ParameterException e){
			LOGGER.error("Failed to parse arguments", e);
			e.usage();
			return;
		}
		Configuration.loadConfiguration(parameters.getConfigurationFile()).ifPresentOrElse(configuration -> {
			for(final var mapping : configuration.getMappings()){
				try{
					final var processor = new Processor(mapping);
					processor.process();
				}
				catch(final MissingFolderException e){
					LOGGER.warn("Didn't run, {}", e.getMessage());
				}
				catch(final Exception e){
					LOGGER.error("Failed to run processor", e);
				}
			}
		}, () -> LOGGER.error("Failed to load configuration from {}", parameters.getConfigurationFile()));
	}
}
