package fr.mrcraftcod.filesecure.files;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Thomas Couchoud (MrCraftCod - zerderr@gmail.com) on 14/07/2017.
 *
 * @author Thomas Couchoud
 * @since 2017-07-14
 */
public class FolderDifference extends HashMap<String, List<String>>
{
	private final FolderOld base;
	private final FolderOld compare;
	
	public FolderDifference(FolderOld base, FolderOld compare)
	{
		this.base = base;
		this.compare = compare;
		
		for(String candidateFolder : compare.keySet())
		{
			List<String> diffFolder = new ArrayList<>();
			List<String> candidateFiles = compare.get(candidateFolder);
			if(base.containsKey(candidateFolder))
			{
				List<String> currentFiles = base.get(candidateFolder);
				diffFolder.addAll(candidateFiles.stream().filter((file) -> !currentFiles.contains(file)).collect(Collectors.toList()));
			}
			else
				diffFolder.addAll(candidateFiles);
			
			if(!diffFolder.isEmpty())
				put(candidateFolder, diffFolder);
		}
	}
}
