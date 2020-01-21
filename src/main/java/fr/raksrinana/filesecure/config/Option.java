package fr.raksrinana.filesecure.config;

import lombok.NonNull;

public interface Option extends Comparable<Option>{
	@Override
	default int compareTo(@NonNull final Option o){
		return Integer.compare(getPriority(), o.getPriority());
	}
	
	/**
	 * Get the priority of this option.
	 *
	 * @return The priority, lower will be executed first.
	 */
	int getPriority();
}
