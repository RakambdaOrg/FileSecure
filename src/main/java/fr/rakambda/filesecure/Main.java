package fr.rakambda.filesecure;

import fr.rakambda.filesecure.config.Configuration;
import fr.rakambda.filesecure.metadata.MetadataExtractor;
import fr.rakambda.filesecure.processor.Processor;
import fr.rakambda.filesecure.utils.FileOperations;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class Main{
	public static void main(String[] args){
		var cli = parseArgs(args);
		
		var configurationOptional = Configuration.loadConfiguration(Path.of(cli.getConfigurationFile()));
		if(configurationOptional.isEmpty()){
			log.error("Failed to load configuration from {}", cli.getConfigurationFile());
			System.exit(1);
			return;
		}
		
		var configuration = configurationOptional.get();
		var metadataExtractor = new MetadataExtractor();
		var fileOperations = new FileOperations(cli.isDryRun());
		
		for(var rule : configuration.getRules()){
			for(var mapping : rule.getMappings()){
				try{
					new Processor(rule, mapping, metadataExtractor, fileOperations).process();
				}
				catch(Exception e){
					log.error("Failed to run processor", e);
				}
			}
		}
	}
	
	private static CLIParameters parseArgs(String[] args){
		var parameters = new CLIParameters();
		var cli = new CommandLine(parameters);
		cli.registerConverter(Path.class, Paths::get);
		cli.setUnmatchedArgumentsAllowed(true);
		try{
			cli.parseArgs(args);
		}
		catch(CommandLine.ParameterException e){
			log.error("Failed to parse arguments", e);
			cli.usage(System.out);
			System.exit(1);
		}
		return parameters;
	}
}
