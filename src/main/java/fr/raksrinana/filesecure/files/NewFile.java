package fr.raksrinana.filesecure.files;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;

@Slf4j
public class NewFile{
	private final String name;
	@Getter
	private final String extension;
	@Getter
	private final Path parent;
	@Getter
	private final ZonedDateTime date;
	@Getter
	private final Path source;
	public static boolean testMode = false;
	
	/**
	 * Constructor.
	 *
	 * @param name      The name of the new file.
	 * @param extension The extension of the new file.
	 * @param parent    The parent folder.
	 * @param fileDate  The creation date of the file.
	 * @param source    The source file.
	 */
	public NewFile(@NotNull String name, @NotNull String extension, @NotNull Path parent, @NotNull ZonedDateTime fileDate, @NotNull Path source){
		this.parent = parent;
		this.name = name;
		this.extension = extension.toLowerCase();
		date = fileDate;
		this.source = source;
	}
	
	/**
	 * Get the new name of the file to put it in a folder.
	 *
	 * @param directory The folder where to put the file in.
	 *
	 * @return The new name.
	 */
	@NotNull
	public String getName(Path directory){
		if(directory == null || (name + extension).equalsIgnoreCase(directory.getFileName().toString())){
			return name + extension;
		}
		if(!Files.exists(directory.resolve(name + extension))){
			return name + extension;
		}
		var i = 1;
		while(Files.exists(directory.resolve(String.format("%s (%d)%s", name, i, extension)))){
			i++;
		}
		return String.format("%s (%d)%s", name, i, extension);
	}
	
	/**
	 * Rename this file to the given file.
	 *
	 * @param target The file to rename it to.
	 */
	public void moveTo(@NotNull Path target){
		if(!testMode){
			try{
				Files.move(getSource(), target);
				log.debug("Renamed {} to {}", this, target);
			}
			catch(IOException e){
				log.error("Failed to move {} to {}", this, target, e);
			}
		}
		else{
			log.debug("Renamed {} to {}", this, target);
		}
	}
}
