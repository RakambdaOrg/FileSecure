package fr.mrcraftcod.filesecure;

import fr.mrcraftcod.filesecure.files.MissingFolderException;
import fr.mrcraftcod.nameascreated.NameAsCreated;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
	 * Default renaming strategy.
	 * Rename the file with a date & time.
	 * <p>
	 * See https://github.com/MrCraftCod/NameAsCreated
	 */
	private static final Function<File, String> defaultRenameStrategy = f -> {
		try{
			return NameAsCreated.buildName(f).getName(f);
		}
		catch(IOException e){
			LOGGER.warn("Error renaming file {}", f.getAbsolutePath());
		}
		return f.getName();
	};
	
	/**
	 * Main method.
	 *
	 * @param args The arguments of the program:
	 *             0: A path to the config file, to the json format.
	 */
	public static void main(final String[] args){
		if(args.length > 0){
			final var path = Paths.get(args[0]);
			if(path.toFile().exists()){
				try{
					final var json = new JSONObject(String.join("\n", Files.readAllLines(path)));
					if(json.has("mappings")){
						final var mappings = json.getJSONArray("mappings");
						for(var i = 0; i < mappings.length(); i++){
							final var map = mappings.getJSONObject(i);
							try{
								Processor.getInstance().process(Paths.get(map.getString("input")), Paths.get(map.getString("output")), defaultRenameStrategy, map.has("strategy") ? Processor.BackupStrategy.getByName(map.getString("strategy")) : null, map.has("filters") ? Arrays.stream(map.getString("filters").split(",")).map(Pattern::compile).collect(Collectors.toList()) : Collections.emptyList(), map.has("excludes") ? Arrays.stream(map.getString("excludes").split(",")).map(Pattern::compile).collect(Collectors.toList()) : Collections.emptyList());
							}
							catch(final MissingFolderException e){
								LOGGER.warn("One of the folders doesn't exists", e);
							}
						}
					}
					else{
						LOGGER.error("The config file doesn't contains the mappings key");
					}
				}
				catch(final IOException e){
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
	}
}
