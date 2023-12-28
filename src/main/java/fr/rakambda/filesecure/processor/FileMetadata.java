package fr.rakambda.filesecure.processor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.time.ZonedDateTime;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public class FileMetadata{
	@Nullable
	private final ZonedDateTime takenDate;
	@Nullable
	private final ZonedDateTime createdDate;
	@Nullable
	private final ZonedDateTime nameDate;
	
	@NotNull
	public ZonedDateTime getDate(){
		return Optional.ofNullable(nameDate)
				.or(() -> Optional.ofNullable(takenDate))
				.or(() -> Optional.ofNullable(createdDate))
				.orElseGet(ZonedDateTime::now);
	}
}
