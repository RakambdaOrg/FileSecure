package fr.raksrinana.filesecure.utils.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.ZoneId;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@NoArgsConstructor
public class GeonamesTimeZone{
	@JsonProperty("lng")
	private double longitude;
	@JsonProperty("lat")
	private double latitude;
	@JsonProperty("sunrise")
	@JsonDeserialize(using = DateTimeDeserializer.class)
	private LocalDateTime sunrise;
	@JsonProperty("sunset")
	@JsonDeserialize(using = DateTimeDeserializer.class)
	private LocalDateTime sunset;
	@JsonProperty("time")
	@JsonDeserialize(using = DateTimeDeserializer.class)
	private LocalDateTime time;
	@JsonProperty("gmtOffset")
	private int gmtOffset;
	@JsonProperty("rawOffset")
	private int rawOffset;
	@JsonProperty("dstOffset")
	private int dstOffset;
	@JsonProperty("timezoneId")
	@JsonDeserialize(using = ZoneIdDeserializer.class)
	private ZoneId timezoneId;
	@JsonProperty("countryCode")
	private String countryCode;
	@JsonProperty("countryName")
	private String countryName;
}
