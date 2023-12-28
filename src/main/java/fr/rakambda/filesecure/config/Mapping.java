package fr.rakambda.filesecure.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ext.NioPathDeserializer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Path;

@Slf4j
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class Mapping{
	@JsonProperty
	@JsonDeserialize(using = NioPathDeserializer.class)
	private Path input;
	@JsonProperty
	@JsonDeserialize(using = NioPathDeserializer.class)
	private Path output;
}
