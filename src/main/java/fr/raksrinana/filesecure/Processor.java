package fr.raksrinana.filesecure;

import fr.raksrinana.filesecure.config.Rule;
import fr.raksrinana.filesecure.files.FolderDifference;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Files;

/**
 * Process a pair of folder, one being the source of the backup, and the other the destination.
 * Singleton.
 */
@Slf4j
public class Processor{
	private final Rule rule;
	
	/**
	 * Constructor.
	 *
	 * @param rule The configuration.
	 */
	public Processor(@NotNull Rule rule){
		this.rule = rule;
	}
	
	/**
	 * Processes a pair of folders to backup.
	 * <p>
	 * If the input folder is "/A/B" and have this structure:
	 * A
	 * |-B
	 * | |-C
	 * | | |-1.txt
	 * | | |-2.txt
	 * | |-D
	 * | | |-3.txt
	 * | |-4.txt
	 * <p>
	 * The output folder "/Z" will then contain:
	 * Z
	 * |-C
	 * | |-1.txt
	 * | |-2.txt
	 * |-D
	 * | |-3.txt
	 * |-4.txt
	 */
	void process(){
		for(var mapping : rule.getMappings()){
			if(!Files.exists(mapping.getInput())){
				log.warn("Input folder {} doesn't exist, skipping mapping", mapping.getInput());
				break;
			}
			if(!Files.exists(mapping.getOutput())){
				log.warn("Output folder {} doesn't exist, skipping mapping", mapping.getOutput());
				break;
			}
			
			log.info("Processing ({}) {} ==> {}", rule.getStrategy().name(), mapping.getInput(), mapping.getOutput());
			log.debug("Building differences...");
			var fd = new FolderDifference(mapping.getInput(), mapping.getOutput(), rule);
			
			log.debug("Applying strategy");
			fd.applyStrategy(rule.getStrategy());
		}
	}
}
