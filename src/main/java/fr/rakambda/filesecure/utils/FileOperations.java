package fr.rakambda.filesecure.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@RequiredArgsConstructor
public class FileOperations{
	private final boolean dryRun;
	
	public void copy(@NonNull Path in, @NonNull Path out) throws IOException{
		if(dryRun){
			return;
		}
		createDirectories(out.getParent());
		Files.copy(in, out, StandardCopyOption.COPY_ATTRIBUTES);
	}
	
	public void move(@NonNull Path in, @NonNull Path out) throws IOException{
		if(dryRun){
			return;
		}
		createDirectories(out.getParent());
		Files.move(in, out);
	}
	
	public void moveWithCopy(@NonNull Path in, @NonNull Path out) throws IOException{
		if(dryRun){
			return;
		}
		
		copy(in, out);
		Files.delete(in);
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
