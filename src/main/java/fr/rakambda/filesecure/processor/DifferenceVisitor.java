package fr.rakambda.filesecure.processor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class DifferenceVisitor implements FileVisitor<Path>{
	private final Path rootFolder;
	private final Collection<Pattern> includePatterns;
	private final Collection<Pattern> excludePatterns;
	private final Collection<Pattern> excludeFolderPatterns;
	
	@Getter
	private final Collection<Path> paths = new ConcurrentLinkedDeque<>();
	@Getter
	private final Collection<Path> folders = new ConcurrentLinkedDeque<>();
	
	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs){
		var relative = rootFolder.relativize(dir).toString().replace("\\", "/");
		if(excludeFolderPatterns.stream().anyMatch(f -> f.matcher(relative).matches())){
			return FileVisitResult.SKIP_SUBTREE;
		}
		
		folders.add(dir);
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs){
		var fileName = file.getFileName().toString();
		
		if(excludePatterns.stream().anyMatch(f -> f.matcher(fileName).matches())){
			return FileVisitResult.CONTINUE;
		}
		if(!includePatterns.isEmpty() && includePatterns.stream().noneMatch(f -> f.matcher(fileName).matches())){
			return FileVisitResult.CONTINUE;
		}
		
		paths.add(file);
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException{
		throw exc;
	}
	
	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc){
		return FileVisitResult.CONTINUE;
	}
}
