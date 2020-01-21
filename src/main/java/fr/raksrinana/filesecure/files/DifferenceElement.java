package fr.raksrinana.filesecure.files;

import fr.raksrinana.filesecure.config.BackupStrategy;
import lombok.NonNull;
import java.nio.file.Path;

public interface DifferenceElement extends Comparable<DifferenceElement>{
	void applyStrategy(@NonNull final BackupStrategy backupStrategy);
	
	@Override
	default int compareTo(DifferenceElement o){
		return getSourcePath().compareTo(o.getSourcePath());
	}
	
	@NonNull Path getSourcePath();
}
