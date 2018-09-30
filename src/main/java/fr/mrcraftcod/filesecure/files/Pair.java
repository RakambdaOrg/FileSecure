package fr.mrcraftcod.filesecure.files;

/**
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 26/03/2018.
 *
 * @author Thomas Couchoud
 * @since 2018-03-26
 */
class Pair<K, V>{
	private final K key;
	private V value;
	
	/**
	 * Constructor.
	 *
	 * @param key   The key.
	 * @param value The value.
	 */
	Pair(final K key, final V value){
		this.key = key;
		this.value = value;
	}
	
	/**
	 * Get the key.
	 *
	 * @return The key.
	 */
	K getKey(){
		return key;
	}
	
	/**
	 * Get the value.
	 *
	 * @return The value.
	 */
	V getValue(){
		return value;
	}
	
	/**
	 * Set the value.
	 *
	 * @param value The value to set.
	 */
	void setValue(final V value){
		this.value = value;
	}
}
