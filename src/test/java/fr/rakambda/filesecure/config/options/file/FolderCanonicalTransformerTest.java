package fr.rakambda.filesecure.config.options.file;

import fr.rakambda.filesecure.exceptions.AbandonBackupException;
import fr.rakambda.filesecure.processor.FileMetadata;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FolderCanonicalTransformerTest{
	private final FolderCanonicalTransformer tested = new FolderCanonicalTransformer(3);
	
	@Mock
	private Path source;
	@Mock
	private Path originalOut;
	@Mock
	private FileMetadata metadata;
	
	@Test
	void itShouldWork() throws AbandonBackupException{
		var base = Paths.get("/path");
		var out = Paths.get("/path/going/to/my/file.ext");
		
		assertThat(tested.apply(source, originalOut, base, out, metadata))
				.contains(Paths.get("/path/g/o/i/going/to/my/file.ext"));
	}
	
	@Test
	void itShouldWorkWhenPartIsSmaller() throws AbandonBackupException{
		var base = Paths.get("/path");
		var out = Paths.get("/path/go/to/my/file.ext");
		
		assertThat(tested.apply(source, originalOut, base, out, metadata))
				.contains(Paths.get("/path/g/o/_/go/to/my/file.ext"));
	}
}