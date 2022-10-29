package fr.rakambda.filesecure.utils.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeDeserializer extends JsonDeserializer<LocalDateTime>{
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	
	@Override
	public LocalDateTime deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException{
		return LocalDateTime.parse(jsonParser.getValueAsString(), FORMATTER);
	}
}
