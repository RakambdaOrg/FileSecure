package fr.mrcraftcod.filesecure;

import fr.mrcraftcod.filesecure.files.FolderOld;
import fr.mrcraftcod.filesecure.files.FolderDifference;
import fr.mrcraftcod.nameascreated.NameAsCreated;
import fr.mrcraftcod.nameascreated.NewFile;
import fr.mrcraftcod.utils.base.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Main class.
 * <p>
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 19/12/2016.
 *
 * @author Thomas Couchoud
 * @since 2016-12-19
 */
public class Main
{
	/**
	 * Default renaming strategy.
	 * Rename the file with a date & time.
	 * <p>
	 * See https://github.com/MrCraftCod/NameAsCreated
	 */
	private static Function<File, String> defaultRenameStrategy = f -> {
		try
		{
			NewFile newFile = NameAsCreated.buildName(f);
			String newName = newFile.getName(f);
			if(!newName.equals(f.getName()))
				if(f.renameTo(new File(f.getParentFile(), newName)))
					return newName;
		}
		catch(IOException e)
		{
			Log.warning("Error renaming file " + f.getAbsolutePath());
		}
		return f.getName();
	};
	
	/**
	 * Main method.
	 *
	 * @param args The arguments of the program:
	 *             0: A path to the config file, to the json format.
	 */
	public static void main(String[] args) throws IOException
	{
		Log.setAppName("FileSecure");
		
		if(args.length > 0)
		{
			Path path = Paths.get(args[0]);
			if(path.toFile().exists())
			{
				JSONObject json = new JSONObject(Files.readAllLines(path).stream().collect(Collectors.joining("\n")));
				if(json.has("mappings"))
				{
					JSONArray mappings = json.getJSONArray("mappings");
					for(int i = 0; i < mappings.length(); i++)
					{
						JSONObject map = mappings.getJSONObject(i);
						try
						{
							Processor.getInstance().process(Paths.get(map.getString("input")), Paths.get(map.getString("output")), defaultRenameStrategy);
						}
						catch(IllegalStateException ignored)
						{
						}
					}
				}
				else
				{
					Log.error("The config file doesn't contains the mappings key");
				}
			}
			else
			{
				Log.error("The specified config file doesn't exists");
			}
		}
		else
		{
			Log.error("No config file given");
		}
		
		// inputs = new File[]{new File("D:\\Documents\\Dropbox\\Tha\\Save")};
		// outputFolder = new FolderOld(new File("G:\\Tha"));
	}
	
	private static void processFolders(FolderOld sourceFolder, FolderOld outputFolder)
	{
		if(!outputFolder.getRoot().exists())
		{
			Log.error("OUTPUT DIR DO NOT EXISTS");
			return;
		}
		FolderDifference filesDiff = outputFolder.findMissing(sourceFolder);
		Log.info("Differences between " + sourceFolder.getRoot() + " & " + outputFolder.getRoot() + ":\n" + filesDiff);
		
		copyFiles(filesDiff, sourceFolder, outputFolder);
	}
	
	private static void copyFiles(FolderDifference filesDiff, FolderOld sourceFolder, FolderOld outputFolder)
	{
		for(String folder : filesDiff.keySet())
		{
			Log.info("Copying folder " + folder);
			Path folderBase = Paths.get(sourceFolder.getRoot().getAbsolutePath(), folder);
			Path endOut = Paths.get(outputFolder.getRoot().getAbsolutePath(), folder);
			endOut.toFile().mkdirs();
			for(String file : filesDiff.get(folder))
			{
				Log.info("\tCopying file " + file);
				try
				{
					Files.move(folderBase.resolve(file).toAbsolutePath(), endOut.resolve(file).toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
				}
				catch(IOException e)
				{
					Log.warning("", e);
				}
			}
		}
	}
}
