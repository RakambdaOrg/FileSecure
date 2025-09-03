package fr.rakambda.filesecure.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import fr.rakambda.filesecure.utils.FileOperations;
import fr.rakambda.filesecure.utils.ThrowingTriConsumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import java.nio.file.Path;

@Getter
@RequiredArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum Operation{
	COPY(FileOperations::copy),
	MOVE(FileOperations::move),
	MOVE_WITH_COPY(FileOperations::moveWithCopy),
	NONE((op, in, out) -> {});
	
	private final ThrowingTriConsumer<FileOperations, Path, Path> processor;
	
	@JsonCreator
	public static Operation getByName(@NonNull String name){
		return valueOf(name);
	}
	
	@SuppressWarnings("SameReturnValue")
	public static Operation getDefault(){
		return NONE;
	}
}
