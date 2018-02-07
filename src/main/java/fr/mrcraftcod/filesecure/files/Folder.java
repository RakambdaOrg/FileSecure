package fr.mrcraftcod.filesecure.files;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 07/02/2018.
 *
 * @author Thomas Couchoud
 * @since 2018-02-07
 */
public class Folder
{
	private final HashMap<String, Folder> folders;
	private final ArrayList<String> files;
	private final String name;
	private final Folder parent;
	private boolean explored;
	
	public Folder(Folder parent, String name)
	{
		this.name = name;
		this.parent = parent;
		this.explored = false;
		this.folders = new HashMap<>();
		this.files = new ArrayList<>();
	}
	
	public Folder getFolderAt(Path path)
	{
		if(path.getParent() == null)
		{
			return getFolder(path.getRoot().toString());
		}
		Folder parent = getFolderAt(path.getParent());
		return parent.getFolder(path.getFileName().toString());
	}
	
	private Folder getFolder(String name)
	{
		if(!folders.containsKey(name))
			folders.put(name, new Folder(this, name));
		return folders.get(name);
	}
	
	public void explore()
	{
		if(!isExplored())
		{
			explored = true;
		}
		List<File> files = Arrays.asList(getPath().toFile().listFiles());
		this.files.addAll(files.stream().filter(File::isFile).map(File::getName).distinct().collect(Collectors.toList()));
		files.stream().filter(File::isDirectory).forEach(f -> folders.put(f.getName(), new Folder(this, f.getName())));
		folders.values().forEach(Folder::explore);
	}
	
	private Path getPath()
	{
		return (parent == null || parent instanceof RootFolder) ? Paths.get(getName()) : parent.getPath().resolve(getName());
	}
	
	public String getName()
	{
		return name;
	}
	
	@Override
	public String toString()
	{
		return "Folder{" + "name='" + name + '\'' + ", parent=" + (parent == null ? "" : parent.getName()) + ", children=" + folders.size() + '}';
	}
	
	public boolean isExplored()
	{
		return explored;
	}
}
