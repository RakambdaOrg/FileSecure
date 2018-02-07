package fr.mrcraftcod.filesecure;

import fr.mrcraftcod.filesecure.files.Folder;
import fr.mrcraftcod.filesecure.files.RootFolder;
import fr.mrcraftcod.utils.base.Log;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 02/02/2018.
 *
 * @author Thomas Couchoud
 * @since 2018-02-02
 */
public class Processor
{
	private final RootFolder rootFolder;
	private static Processor INSTANCE;
	
	private Processor()
	{
		this.rootFolder = new RootFolder(null, "<ROOT>");
	}
	
	public void process(@NotNull Path input, @NotNull Path output, Function<File, String> renameStrategy) throws IllegalStateException
	{
		System.out.printf("Processing %s ==> %s\n", input, output);
		if(renameStrategy == null)
			renameStrategy = File::getName;
		if(!input.toFile().exists())
		{
			Log.warning("The input folder " + input.toString() + " doesn't exists");
			throw new IllegalStateException("Input folder doesn't exists");
		}
		if(!output.toFile().exists())
		{
			Log.warning("The output folder " + input.toString() + " doesn't exists");
			throw new IllegalStateException("Output folder doesn't exists");
		}
		Folder inputFolder = rootFolder.getFolderAt(input);
		inputFolder.explore();
		Folder outputFolder = rootFolder.getFolderAt(output);
		outputFolder.explore();
		System.out.println();
	}
	
	public static Processor getInstance()
	{
		if(INSTANCE == null)
			INSTANCE = new Processor();
		return INSTANCE;
	}
}
