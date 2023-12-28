package fr.rakambda.filesecure.config.options;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fr.rakambda.filesecure.config.options.file.FolderCanonicalTransformer;
import fr.rakambda.filesecure.config.options.file.FolderPerDayTransformer;
import fr.rakambda.filesecure.config.options.file.FolderPerMonthTransformer;
import fr.rakambda.filesecure.config.options.file.FolderPerWeekTransformer;
import fr.rakambda.filesecure.config.options.file.FolderPerYearTransformer;
import fr.rakambda.filesecure.config.options.file.FolderUniquePerDayTransformer;
import fr.rakambda.filesecure.config.options.file.IfOlderThanTransformer;
import fr.rakambda.filesecure.config.options.file.LowercaseTransformer;
import fr.rakambda.filesecure.config.options.file.NoRenameTransformer;
import fr.rakambda.filesecure.config.options.file.RenameWithDateTransformer;
import fr.rakambda.filesecure.exceptions.AbandonBackupException;
import fr.rakambda.filesecure.processor.FileMetadata;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "name")
@JsonSubTypes(value = {
		@JsonSubTypes.Type(value = FolderCanonicalTransformer.class, name = "FolderCanonicalTransformer"),
		@JsonSubTypes.Type(value = FolderPerDayTransformer.class, name = "FolderPerDayTransformer"),
		@JsonSubTypes.Type(value = FolderPerWeekTransformer.class, name = "FolderPerWeekTransformer"),
		@JsonSubTypes.Type(value = FolderPerMonthTransformer.class, name = "FolderPerMonthTransformer"),
		@JsonSubTypes.Type(value = FolderPerYearTransformer.class, name = "FolderPerYearTransformer"),
		@JsonSubTypes.Type(value = FolderUniquePerDayTransformer.class, name = "FolderUniquePerDayTransformer"),
		@JsonSubTypes.Type(value = IfOlderThanTransformer.class, name = "IfOlderThanTransformer"),
		@JsonSubTypes.Type(value = LowercaseTransformer.class, name = "LowercaseTransformer"),
		@JsonSubTypes.Type(value = NoRenameTransformer.class, name = "NoRenameTransformer"),
		@JsonSubTypes.Type(value = RenameWithDateTransformer.class, name = "RenameWithDateTransformer"),
})
public interface FileTransformer extends Transformer{
	@NotNull
	Optional<Path> apply(@NotNull Path sourceFile, @NotNull Path originalOutput, @NotNull Path baseOutput, @NotNull Path currentOutput, @NotNull FileMetadata metadata) throws AbandonBackupException;
}
