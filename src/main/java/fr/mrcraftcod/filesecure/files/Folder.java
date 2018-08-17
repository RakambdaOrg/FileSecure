package fr.mrcraftcod.filesecure.files;

import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represent a folder and its content.
 * <p>
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
	
	/**
	 * Constructor.
	 *
	 * @param parent The parent folder.
	 * @param name   The name of the folder.
	 */
	Folder(final Folder parent, final String name)
	{
		this.name = name;
		this.parent = parent;
		this.explored = false;
		this.folders = new HashMap<>();
		this.files = new ArrayList<>();
	}
	
	/**
	 * Get or create a Folder object representing the given path.
	 *
	 * @param path The path of the folder to get.
	 *
	 * @return The Folder object representing the given path.
	 */
	public Folder getFolderAt(@NotNull final Path path)
	{
		if(path.getParent() == null)
		{
			return getFolder(path.getRoot().toString());
		}
		final var parent = getFolderAt(path.getParent());
		return parent.getFolder(path.getFileName().toString());
	}
	
	/**
	 * Get or create a folder inside this current folder.
	 *
	 * @param name The name of the folder.
	 *
	 * @return The Folder.
	 */
	Folder getFolder(@NotNull final String name)
	{
		if(!folders.containsKey(name))
			folders.put(name, new Folder(this, name));
		return folders.get(name);
	}
	
	/**
	 * Retrieve the content (files name) of this folder and its children.
	 */
	public void explore()
	{
		if(!explored)
		{
			final var filesArray = getPath().toFile().listFiles();
			if(filesArray != null)
			{
				final var files = Arrays.asList(filesArray);
				this.files.addAll(files.stream().filter(File::isFile).map(File::getName).distinct().filter(n -> !n.equals(".dropbox")).collect(Collectors.toList()));
				files.stream().filter(File::isDirectory).forEach(f -> folders.put(f.getName(), new Folder(this, f.getName())));
			}
			explored = true;
		}
		folders.values().forEach(Folder::explore);
	}
	
	/**
	 * Get the missing files from the given folder.
	 *
	 * @param folder         The folder to get missing files from.
	 * @param renameStrategy The rename strategy to use when we'll apply out backup strategy later.
	 *
	 * @return The difference.
	 */
	public FolderDifference getMissingWith(@NotNull final Folder folder, final Function<File, String> renameStrategy)
	{
		return new FolderDifference(this, folder, renameStrategy);
	}
	
	/**
	 * Tells if this folders contains a file.
	 *
	 * @param name The file to check.
	 *
	 * @return True if contained, false otherwise.
	 */
	boolean containsFile(final String name)
	{
		return getFiles().contains(name);
	}
	
	/**
	 * Get all the files of this folder.
	 *
	 * @return The files.
	 */
	public ArrayList<String> getFiles()
	{
		return files;
	}
	
	@Override
	public String toString()
	{
		return "Folder{" + "name='" + name + '\'' + ", parent=" + (parent == null ? "" : parent.getName()) + ", children=" + folders.size() + '}';
	}
	
	/**
	 * Get all the folders of this folder.
	 *
	 * @return The files.
	 */
	Collection<Folder> getFolders()
	{
		return folders.values();
	}
	
	/**
	 * Get the name of the folder.
	 *
	 * @return The folder's name.
	 */
	String getName(){
		return name;
	}
	
	/**
	 * Get the path of this folder.
	 *
	 * @return The folder path.
	 */
	Path getPath()
	{
		return (parent == null || parent instanceof RootFolder) ? Paths.get(getName()) : parent.getPath().resolve(getName());
	}
}
