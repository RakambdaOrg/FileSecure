package fr.raksrinana.filesecure.utils.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.ZoneId;

public class ZoneIdDeserializer extends JsonDeserializer<ZoneId>{
	@Override
	public ZoneId deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException{
		return ZoneId.of(jsonParser.getValueAsString());
	}
}
