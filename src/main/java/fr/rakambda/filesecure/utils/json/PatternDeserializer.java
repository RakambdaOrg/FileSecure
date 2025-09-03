package fr.rakambda.filesecure.utils.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.jspecify.annotations.NonNull;
import java.io.IOException;
import java.util.regex.Pattern;

public class PatternDeserializer extends JsonDeserializer<Pattern>{
	@Override
	public Pattern deserialize(@NonNull JsonParser jsonParser, @NonNull DeserializationContext deserializationContext) throws IOException{
		return Pattern.compile(jsonParser.getValueAsString());
	}
}


