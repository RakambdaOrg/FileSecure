package fr.raksrinana.filesecure.files;

import fr.raksrinana.nameascreated.NewFile;
import java.nio.file.Path;

/**
 * Created by mrcraftcod (MrCraftCod - zerderr@gmail.com) on 2019-02-17.
 *
 * @author Thomas Couchoud
 * @since 2019-02-17
 */
@SuppressWarnings("WeakerAccess")
public class DesiredTarget{
	private final NewFile newFileInfos;
	private Path targetFolder;
	private String desiredName;
	
	/**
	 * Constructor.
	 *
	 * @param targetFolder The target folder (where to copy/move/...).
	 * @param desiredName  A pair describing the file name in the base (key) and in the target (value).
	 */
	public DesiredTarget(final Path targetFolder, final NewFile newFileInfos, final String desiredName){
		this.targetFolder = targetFolder;
		this.desiredName = desiredName;
		this.newFileInfos = newFileInfos;
	}
	
	public String getDesiredName(){
		return desiredName;
	}
	
	public NewFile getNewFileInfos(){
		return newFileInfos;
	}
	
	public void setDesiredName(final String desiredName){
		this.desiredName = desiredName;
	}
	
	public Path getTargetFolder(){
		return targetFolder;
	}
	
	public void setTargetFolder(final Path targetFolder){
		this.targetFolder = targetFolder;
	}
}
