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
class NoRenameTransformerTest{
	private final NoRenameTransformer tested = new NoRenameTransformer();

	@Mock
	private Path originalOut;
	@Mock
	private Path baseOutput;
	@Mock
	private FileMetadata metadata;
	
	@Test
	void itShouldWork() throws AbandonBackupException{
		var source = Paths.get("/path/to/source.other");
		var out = Paths.get("/path/to/my/file.ext");
		
		assertThat(tested.apply(source, originalOut, baseOutput, out, metadata))
				.contains(Paths.get("/path/to/my/source.other"));
	}
}