package fr.raksrinana.filesecure.files.extractor.media;

import com.drew.metadata.Directory;
import com.drew.metadata.xmp.XmpDirectory;
import org.jetbrains.annotations.NotNull;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

public class XmpMediaDateExtractor implements MediaDateExtractor<XmpDirectory>{
	private final List<String> keys = List.of("xmp:CreateDate", "photoshop:DateCreated");
	private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
	
	@NotNull
	@Override
	public Optional<ZonedDateTime> parse(@NotNull Directory directory, @NotNull TimeZone tz){
		var xmpDirectory = (XmpDirectory) directory;
		var values = xmpDirectory.getXmpProperties();
		for(var key : keys){
			if(values.containsKey(key)){
				try{
					return Optional.ofNullable(values.get(key))
							.map(date -> ZonedDateTime.parse(date, dateTimeFormatter.withZone(tz.toZoneId())));
				}
				catch(Exception ignored){
				}
			}
		}
		return Optional.empty();
	}
	
	@NotNull
	@Override
	public Class<XmpDirectory> getKlass(){
		return XmpDirectory.class;
	}
}
