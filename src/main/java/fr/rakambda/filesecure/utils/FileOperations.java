package fr.rakambda.filesecure.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@RequiredArgsConstructor
public class FileOperations{
	private final boolean dryRun;
	
	public void copy(@NotNull Path in, @NotNull Path out) throws IOException{
		if(dryRun){
			return;
		}
		createDirectories(out.getParent());
		Files.copy(in, out);
	}
	
	public void move(@NotNull Path in, @NotNull Path out) throws IOException{
		if(dryRun){
			return;
		}
		createDirectories(out.getParent());
		Files.move(in, out);
	}
	
	public void delete(Path path) throws IOException{
		if(dryRun){
			return;
		}
		Files.delete(path);
	}
	
	public boolean deleteIfExists(Path path) throws IOException{
		if(dryRun){
			return false;
		}
		return Files.deleteIfExists(path);
	}
	
	private void createDirectories(Path path) throws IOException{
		Files.createDirectories(path);
		if(!Files.isDirectory(path)){
			throw new IOException("Destination folder %s already exists as a file".formatted(path));
		}
	}
}
