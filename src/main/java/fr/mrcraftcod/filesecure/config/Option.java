package fr.mrcraftcod.filesecure.config;

import fr.mrcraftcod.filesecure.Utils;
import fr.mrcraftcod.filesecure.exceptions.AbandonBackupException;
import fr.mrcraftcod.filesecure.files.DesiredTarget;
import fr.mrcraftcod.nameascreated.NewFile;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-26.
 *
 * @author Thomas Couchoud
 * @since 2019-03-26
 */
public interface Option extends Comparable<Option>{
	static Option parse(final JSONObject json) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException{
		final var klazz = Class.forName(Optional.of(json.optString("name")).filter(s -> !s.isBlank()).orElseThrow(() -> new IllegalStateException("No class name provided")));
		if(!Utils.getAllExtendedOrImplementedTypesRecursively(klazz).contains(Option.class)){
			throw new IllegalArgumentException("Option object isn't parsable from JSON");
		}
		@SuppressWarnings("unchecked") final var parsableClazz = (Class<Option>) klazz;
		final var parameters = Optional.ofNullable(json.optJSONObject("parameters")).orElse(new JSONObject());
		return parsableClazz.getConstructor(JSONObject.class).newInstance(parameters);
	}
	
	/**
	 * @param originFile
	 * @param desiredTarget
	 * @param fileName      The name of the file.
	 * @param folder        The original destination.
	 */
	void apply(Path originFile, DesiredTarget desiredTarget, final NewFile fileName, final Path folder) throws AbandonBackupException;
	
	int getPriority();
	
	@Override
	default int compareTo(@NotNull final Option o){
		return Integer.compare(getPriority(), o.getPriority());
	}
}
