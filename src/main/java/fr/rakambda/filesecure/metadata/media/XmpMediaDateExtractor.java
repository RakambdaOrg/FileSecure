package fr.rakambda.filesecure.metadata.media;

import com.drew.metadata.Directory;
import com.drew.metadata.xmp.XmpDirectory;
import org.jspecify.annotations.NonNull;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

public class XmpMediaDateExtractor implements MediaDateExtractor<XmpDirectory>{
	private final List<String> keys = List.of("xmp:CreateDate", "photoshop:DateCreated");
	private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
	
	@NonNull
	@Override
	public Optional<ZonedDateTime> parse(@NonNull Directory directory, @NonNull TimeZone tz){
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
	
	@NonNull
	@Override
	public Class<XmpDirectory> getKlass(){
		return XmpDirectory.class;
	}
}
