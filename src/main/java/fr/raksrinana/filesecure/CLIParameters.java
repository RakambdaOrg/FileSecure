package fr.raksrinana.filesecure;

import com.beust.jcommander.Parameter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class CLIParameters{
	@Parameter(description = "The path to the configuration file", /*converter = PathConverter.class,*/ required = true)
	@Getter
	private String configurationFile;
}

