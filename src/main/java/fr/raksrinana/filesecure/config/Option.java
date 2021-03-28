package fr.raksrinana.filesecure.config;

import org.jetbrains.annotations.NotNull;

public interface Option extends Comparable<Option>{
	@Override
	default int compareTo(@NotNull Option o){
		return Integer.compare(getPriority(), o.getPriority());
	}
	
	/**
	 * Get the priority of this option.
	 *
	 * @return The priority, lower will be executed first.
	 */
	int getPriority();
}
