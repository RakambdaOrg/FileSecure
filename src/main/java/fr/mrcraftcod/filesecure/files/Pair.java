package fr.mrcraftcod.filesecure.files;

/**
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 26/03/2018.
 *
 * @author Thomas Couchoud
 * @since 2018-03-26
 */
public class Pair<K, V>
{
	private K key;
	private V value;
	
	public Pair(K key, V value)
	{
		this.key = key;
		this.value = value;
	}
	
	public K getKey()
	{
		return key;
	}
	
	public void setKey(K key)
	{
		this.key = key;
	}
	
	public V getValue()
	{
		return value;
	}
	
	public void setValue(V value)
	{
		this.value = value;
	}
}
