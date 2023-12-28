package fr.rakambda.filesecure.config.options.folder;

import fr.rakambda.filesecure.utils.FileOperations;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeleteIfEmptyTransformerTest{
	private final DeleteIfEmptyTransformer tested = new DeleteIfEmptyTransformer(2);
	
	@Mock
	private FileOperations fileOperations;
	
	private static Stream<Arguments> validPaths(){
		return Stream.of(
				Arguments.of(Paths.get("/path/to/the")),
				Arguments.of(Paths.get("/path/to/the/folder")),
				Arguments.of(Paths.get("/path/to/the/folder/yes"))
		);
	}
	
	private static Stream<Arguments> depthInvalidPaths(){
		return Stream.of(
				Arguments.of(Paths.get("/path")),
				Arguments.of(Paths.get("/path/to"))
		);
	}
	
	@ParameterizedTest
	@MethodSource("validPaths")
	void itShouldWorkOnEmptyFolder(Path folder) throws IOException{
		var base = Paths.get("/path");
		
		try(var filesMock = mockStatic(Files.class)){
			filesMock.when(() -> Files.list(folder)).thenReturn(Stream.of());
			
			tested.apply(fileOperations, folder, base);
			
			verify(fileOperations).delete(folder);
		}
	}
	
	@Test
	void itShouldSkipNotEmpty() throws IOException{
		var folder = Paths.get("/path/to/the/folder");
		var base = Paths.get("/path");
		
		try(var filesMock = mockStatic(Files.class)){
			filesMock.when(() -> Files.list(folder)).thenReturn(Stream.of(folder.resolve("fake")));
			
			tested.apply(fileOperations, folder, base);
			
			verify(fileOperations, never()).delete(any());
		}
	}
	
	@ParameterizedTest
	@MethodSource("depthInvalidPaths")
	void itShouldSkipIfDepthIsNotEnough(Path folder) throws IOException{
		var base = Paths.get("/path");
		
		try(var filesMock = mockStatic(Files.class)){
			filesMock.when(() -> Files.list(folder)).thenReturn(Stream.empty());
			
			tested.apply(fileOperations, folder, base);
			
			verify(fileOperations, never()).delete(any());
		}
	}
}