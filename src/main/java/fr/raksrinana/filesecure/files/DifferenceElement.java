package fr.raksrinana.filesecure.files;

import fr.raksrinana.filesecure.config.BackupStrategy;
import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;

public interface DifferenceElement extends Comparable<DifferenceElement>{
	void applyStrategy(@NotNull BackupStrategy backupStrategy);
	
	@Override
	default int compareTo(DifferenceElement o){
		return getSourcePath().compareTo(o.getSourcePath());
	}
	
	@NotNull Path getSourcePath();
}
