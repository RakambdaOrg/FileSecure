package fr.mrcraftcod.filesecure;

import java.util.*;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-26.
 *
 * @author Thomas Couchoud
 * @since 2019-03-26
 */
public class Utils{
	/**
	 * Get all the classes extended or implemented by a given class.
	 *
	 * @param clazz The class to fetch extends and implements for.
	 *
	 * @return A set of extended and implemented classes.
	 */
	public static Set<Class<?>> getAllExtendedOrImplementedTypesRecursively(Class<?> clazz){
		final List<Class<?>> res = new ArrayList<>();
		
		do{
			res.add(clazz);
			final var interfaces = clazz.getInterfaces();
			if(interfaces.length > 0){
				res.addAll(Arrays.asList(interfaces));
				for(final var interfaze : interfaces){
					res.addAll(getAllExtendedOrImplementedTypesRecursively(interfaze));
				}
			}
			final var superClass = clazz.getSuperclass();
			if(superClass == null){
				break;
			}
			clazz = superClass;
		}
		while(!"java.lang.Object".equals(clazz.getCanonicalName()));
		return new HashSet<>(res);
	}
}
