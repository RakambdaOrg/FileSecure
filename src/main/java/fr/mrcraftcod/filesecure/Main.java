package fr.mrcraftcod.filesecure;

import fr.mrcraftcod.filesecure.files.MissingFolderException;
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
	private static final Function<File, String> defaultRenameStrategy = f -> {
		try
		{
			NewFile newFile = NameAsCreated.buildName(f, false);
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
	public static void main(String[] args)
	{
		Log.setAppName("FileSecure");
		
		if(args.length > 0)
		{
			Path path = Paths.get(args[0]);
			if(path.toFile().exists())
			{
				try
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
								Processor.getInstance().process(Paths.get(map.getString("input")), Paths.get(map.getString("output")), defaultRenameStrategy, map.has("strategy") ? Processor.BackupStrategy.getByName(map.getString("strategy")) : null);
							}
							catch(MissingFolderException e)
							{
								Log.warning("One of the folders doesn't exists", e);
							}
						}
					}
					else
					{
						Log.error("The config file doesn't contains the mappings key");
					}
				}
				catch(IOException e)
				{
					Log.warning("Couldn't read the configuration file", e);
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
	}
}
