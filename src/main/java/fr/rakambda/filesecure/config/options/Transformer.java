package fr.rakambda.filesecure.config.options;

import org.jspecify.annotations.NonNull;

public interface Transformer extends Comparable<Transformer>{
	@Override
	default int compareTo(@NonNull Transformer o){
		return Integer.compare(getPriority(), o.getPriority());
	}
	
	/**
	 * Get the priority of this option.
	 *
	 * @return The priority, lower will be executed first.
	 */
	int getPriority();
}
