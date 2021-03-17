package fr.raksrinana.filesecure.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ext.NioPathDeserializer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.Path;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@Slf4j
public class Mapping{
	@JsonProperty("input")
	@Getter
	@JsonDeserialize(using = NioPathDeserializer.class)
	private Path input;
	@JsonProperty("output")
	@Getter
	@JsonDeserialize(using = NioPathDeserializer.class)
	private Path output;
}
