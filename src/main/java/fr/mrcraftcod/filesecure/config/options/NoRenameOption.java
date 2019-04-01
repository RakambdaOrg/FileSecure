package fr.mrcraftcod.filesecure.config.options;

import fr.mrcraftcod.filesecure.config.Option;
import fr.mrcraftcod.filesecure.files.DesiredTarget;
import fr.mrcraftcod.nameascreated.NewFile;
import org.json.JSONObject;
import java.nio.file.Path;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-03-26.
 *
 * @author Thomas Couchoud
 * @since 2019-03-26
 */
public class NoRenameOption implements Option{
	public NoRenameOption(final JSONObject json){
	
	}
	
	@Override
	public void apply(final Path originFile, final DesiredTarget desiredTarget, final NewFile fileName, final Path folder){
		desiredTarget.setDesiredName(originFile.getFileName().toString());
	}
	
	@Override
	public int getPriority(){
		return 100;
	}
}
