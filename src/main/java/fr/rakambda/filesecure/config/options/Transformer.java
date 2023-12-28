package fr.rakambda.filesecure.config.options;

import org.jetbrains.annotations.NotNull;

public interface Transformer extends Comparable<Transformer>{
	@Override
	default int compareTo(@NotNull Transformer o){
		return Integer.compare(getPriority(), o.getPriority());
	}
	
	/**
	 * Get the priority of this option.
	 *
	 * @return The priority, lower will be executed first.
	 */
	int getPriority();
}
