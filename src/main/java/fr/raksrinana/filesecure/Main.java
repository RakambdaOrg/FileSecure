package fr.raksrinana.filesecure;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import fr.raksrinana.filesecure.config.Configuration;
import fr.raksrinana.filesecure.exceptions.MissingFolderException;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Path;

/**
 * Main class.
 */
@Slf4j
public class Main{
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
			log.error("Failed to parse arguments", e);
			e.usage();
			return;
		}
		Configuration.loadConfiguration(Path.of(parameters.getConfigurationFile())).ifPresentOrElse(configuration -> {
			for(final var mapping : configuration.getMappings()){
				try{
					final var processor = new Processor(mapping);
					processor.process();
				}
				catch(final MissingFolderException e){
					log.warn("Didn't run, {}", e.getMessage());
				}
				catch(final Exception e){
					log.error("Failed to run processor", e);
				}
			}
		}, () -> log.error("Failed to load configuration from {}", parameters.getConfigurationFile()));
	}
}
