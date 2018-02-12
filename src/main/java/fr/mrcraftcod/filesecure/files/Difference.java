package fr.mrcraftcod.filesecure.files;

import fr.mrcraftcod.filesecure.Processor;
import fr.mrcraftcod.utils.base.Log;
import javafx.util.Pair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Represent a file difference.
 * <p>
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 08/02/18.
 *
 * @author Thomas Couchoud
 * @since 2018-02-08
 */
@SuppressWarnings("WeakerAccess")
public class Difference
{
	private final Folder base;
	private final Folder target;
	private final Pair<String, String> file;
	
	/**
	 * Constructor.
	 *
	 * @param target The target folder (where to copy/move/...).
	 * @param base   The source folder.
	 * @param file   A pair describing the file name in the base (key) and un the target (value).
	 */
	public Difference(Folder base, Folder target, Pair<String, String> file)
	{
		this.target = target;
		this.base = base;
		this.file = file;
	}
	
	/**
	 * Apply a strategy (copy, move, ...) on this difference causing the base to be copied/moved/... to the target.
	 *
	 * @param backupStrategy The strategy to apply.
	 */
	@SuppressWarnings("WeakerAccess")
	public void applyStrategy(Processor.BackupStrategy backupStrategy)
	{
		Path basePath = base.getPath().resolve(file.getKey());
		Path targetPath = target.getPath().resolve(file.getValue());
		//noinspection ResultOfMethodCallIgnored
		targetPath.getParent().toFile().mkdirs();
		Log.info(backupStrategy.name() + " file " + basePath + " to " + targetPath);
		try
		{
			switch(backupStrategy)
			{
				case MOVE:
					if(Files.move(basePath, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists())
					{
						base.getFiles().remove(basePath.getFileName().toString());
						target.getFiles().add(targetPath.getFileName().toString());
					}
					break;
				case COPY:
					if(Files.copy(basePath, targetPath, StandardCopyOption.REPLACE_EXISTING).toFile().exists())
					{
						target.getFiles().add(targetPath.getFileName().toString());
					}
					break;
			}
		}
		catch(IOException e)
		{
			Log.warning("", e);
		}
	}
}
