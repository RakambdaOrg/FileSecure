package fr.mrcraftcod.filesecure.config;

import org.json.JSONObject;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-26.
 *
 * @author Thomas Couchoud
 * @since 2019-03-26
 */
public class Configuration{
	private final ArrayList<Mapping> mappings;
	
	private Configuration(){
		this.mappings = new ArrayList<>();
	}
	
	public static Configuration parse(final JSONObject json) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException{
		final var config = new Configuration();
		if(json.has("mappings")){
			final var mappings = json.getJSONArray("mappings");
			for(var i = 0; i < mappings.length(); i++){
				config.mappings.add(Mapping.parse(mappings.getJSONObject(i)));
			}
		}
		return config;
	}
	
	public Collection<Mapping> getMappings(){
		return this.mappings;
	}
}