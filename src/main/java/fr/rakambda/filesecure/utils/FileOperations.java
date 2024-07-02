package fr.rakambda.filesecure.utils;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;

@Slf4j
@RequiredArgsConstructor
public class FileOperations{
	private final boolean dryRun;
	
	public void copy(@NotNull Path in, @NotNull Path out) throws IOException{
		if(dryRun){
			return;
		}
		createDirectories(out.getParent());
		var inputAttributes = Files.getFileAttributeView(in, BasicFileAttributeView.class).readAttributes();
		
		Files.copy(in, out);
		copyFileAttributes(inputAttributes, out);
	}
	
	public void move(@NotNull Path in, @NotNull Path out) throws IOException{
		if(dryRun){
			return;
		}
		createDirectories(out.getParent());
		var inputAttributes = Files.getFileAttributeView(in, BasicFileAttributeView.class).readAttributes();
		
		Files.move(in, out);
		copyFileAttributes(inputAttributes, out);
	}
	
	public void moveWithCopy(@NotNull Path in, @NotNull Path out) throws IOException{
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
	
	private void copyFileAttributes(@NonNull BasicFileAttributes baseAttributes, @NotNull Path to) throws IOException{
		var attributes = Files.getFileAttributeView(to, BasicFileAttributeView.class);
		attributes.setTimes(baseAttributes.lastModifiedTime(), baseAttributes.lastAccessTime(), baseAttributes.creationTime());
	}
}
