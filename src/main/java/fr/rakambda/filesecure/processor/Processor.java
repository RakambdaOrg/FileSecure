package fr.rakambda.filesecure.processor;

import fr.rakambda.filesecure.config.Mapping;
import fr.rakambda.filesecure.config.Rule;
import fr.rakambda.filesecure.exceptions.AbandonBackupException;
import fr.rakambda.filesecure.exceptions.FlagsProcessingException;
import fr.rakambda.filesecure.metadata.MetadataExtractor;
import fr.rakambda.filesecure.utils.FileOperations;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Slf4j
public class Processor{
	@NonNull
	private final Rule rule;
	private final Mapping mapping;
	private final MetadataExtractor metadataExtractor;
	private final FileOperations fileOperations;
	
	public void process() throws IOException{
		if(!Files.exists(mapping.getInput())){
			log.warn("Input folder {} doesn't exist, skipping mapping", mapping.getInput());
			return;
		}
		
		log.info("Processing mapping ({}) {} ==> {}", rule.getOperation().name(), mapping.getInput(), mapping.getOutput());
		log.debug("Building differences...");
		var differenceVisitor = new DifferenceVisitor(mapping.getInput(), rule.getFilters(), rule.getExcludes(), rule.getFolderExcludes());
		Files.walkFileTree(mapping.getInput(), Set.of(), rule.getMaxDepth(), differenceVisitor);
		
		log.debug("Applying strategy");
		differenceVisitor.getPaths().forEach(this::applyStrategy);
		
		log.debug("Cleaning up");
		differenceVisitor.getFolders().stream()
				.sorted(Comparator.comparingInt(Path::getNameCount).reversed())
				.forEach(this::cleanup);
	}
	
	private void cleanup(@NonNull Path path){
		for(var option : rule.getFolderTransformers()){
			option.apply(fileOperations, path, mapping.getInput());
		}
	}
	
	@SneakyThrows(IOException.class)
	private void applyStrategy(@NonNull Path path) throws FlagsProcessingException{
		var relativeInput = mapping.getInput().relativize(path);
		var exactDestination = mapping.getOutput().resolve(relativeInput);
		var destination = exactDestination;
		var metadata = metadataExtractor.getMetadata(path);
		
		for(var option : rule.getFileTransformers()){
			try{
				var newDestination = option.apply(path, exactDestination, mapping.getOutput(), destination, metadata);
				if(newDestination.isPresent()){
					log.debug("File destination {} changed to {} by {}", destination, newDestination.get(), option.getClass().getSimpleName());
					destination = newDestination.get();
				}
			}
			catch(AbandonBackupException e){
				log.debug("Transformer {} abandoned backup for file {}", option.getClass().getSimpleName(), path);
				return;
			}
			catch(Exception e){
				log.error("Error applying transformer {} to file {}", option.getClass(), path, e);
				throw new FlagsProcessingException("Error applying transformer to file " + path);
			}
		}
		
		if(rule.isSkipIfAlreadyExists() && Files.exists(destination)){
			return;
		}
		var nonExistingDestination = generateUniqueName(destination);
		
		log.info("{} file {} to {}", rule.getOperation().name(), path, nonExistingDestination);
		try{
			rule.getOperation().getProcessor().accept(fileOperations, path, nonExistingDestination);
			if(rule.isSetFileCreationDate()){
				Optional.ofNullable(metadata.getNameDate())
						.or(() -> Optional.ofNullable(metadata.getTakenDate()))
						.ifPresent(date -> {
							try{
								var instant = date.toInstant();
								var fileTime = FileTime.from(instant);
								log.info("Setting creation date for {} to {}", exactDestination, instant);
								
								var attributes = Files.getFileAttributeView(nonExistingDestination, BasicFileAttributeView.class);
								BasicFileAttributes attributesRead = attributes.readAttributes();
								attributes.setTimes(fileTime, attributesRead.lastAccessTime(), fileTime);
							}
							catch(IOException e){
								log.warn("Failed to set file creation date to obtained metadata", e);
							}
						});
			}
		}
		catch(Exception e){
			log.warn("Error applying operation {} on file {}", rule.getOperation(), path, e);
		}
	}
	
	@NonNull
	private Path generateUniqueName(@NonNull Path sourcePath){
		try{
			if(!Files.exists(sourcePath)){
				return sourcePath;
			}
			
			var fileName = sourcePath.getFileName().toString();
			
			var extIndex = fileName.lastIndexOf(".");
			var prefix = fileName.substring(0, extIndex);
			var ext = fileName.substring(extIndex);
			
			var i = 0;
			var finalName = fileName;
			do{
				var newName = "%s (%d)%s".formatted(prefix, ++i, ext);
				log.debug("File '{}' already exists in target, trying with suffix {}", sourcePath, i);
				finalName = newName;
			}
			while(Files.exists(sourcePath.getParent().resolve(finalName)));
			
			return sourcePath.getParent().resolve(finalName);
		}
		catch(Exception e){
			log.error("Failed to generate unique name", e);
			throw new RuntimeException("Failed to generate unique name", e);
		}
	}
}
