package fr.raksrinana.filesecure;

import com.beust.jcommander.Parameter;
import java.nio.file.Path;
import java.nio.file.Paths;

@SuppressWarnings("FieldMayBeFinal")
public class CLIParameters{
	@Parameter(description = "The path to the configuration file", /*converter = PathConverter.class,*/ required = true)
	private String configurationFile;
	
	CLIParameters(){
	}
	
	public Path getConfigurationFile(){
		return Paths.get(configurationFile);
	}
}

