package fr.raksrinana.filesecure.utils.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import lombok.NonNull;
import java.io.IOException;
import java.util.regex.Pattern;

public class PatternDeserializer extends JsonDeserializer<Pattern>{
	@Override
	public Pattern deserialize(@NotNull final JsonParser jsonParser, @NotNull final DeserializationContext deserializationContext) throws IOException{
		return Pattern.compile(jsonParser.getValueAsString());
	}
}


