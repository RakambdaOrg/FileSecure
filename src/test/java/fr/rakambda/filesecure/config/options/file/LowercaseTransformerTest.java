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
class LowercaseTransformerTest{
	private final LowercaseTransformer tested = new LowercaseTransformer();
	
	@Mock
	private Path source;
	@Mock
	private Path originalOut;
	@Mock
	private FileMetadata metadata;
	
	@Test
	void itShouldWork() throws AbandonBackupException{
		var base = Paths.get("/pAth/tO");
		var out = Paths.get("/pAth/tO/mY/sUper/fIle.ext");
		
		assertThat(tested.apply(source, originalOut, base, out, metadata))
				.contains(Paths.get("/pAth/tO/my/super/fIle.ext"));
	}
}