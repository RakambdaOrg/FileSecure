package fr.rakambda.filesecure.metadata;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.xmp.XmpDirectory;
import fr.rakambda.filesecure.metadata.media.MediaDateExtractor;
import fr.rakambda.filesecure.metadata.media.SimpleMediaDateExtractor;
import fr.rakambda.filesecure.metadata.media.XmpMediaDateExtractor;
import fr.rakambda.filesecure.metadata.name.NameDateExtractor;
import fr.rakambda.filesecure.metadata.name.Pattern1NameDateExtractorImpl;
import fr.rakambda.filesecure.metadata.name.Pattern2NameDateExtractorImpl;
import fr.rakambda.filesecure.metadata.name.Pattern3NameDateExtractorImpl;
import fr.rakambda.filesecure.metadata.name.Pattern4NameDateExtractorImpl;
import fr.rakambda.filesecure.metadata.name.Pattern5NameDateExtractorImpl;
import fr.rakambda.filesecure.metadata.name.Pattern6NameDateExtractorImpl;
import fr.rakambda.filesecure.processor.FileMetadata;
import fr.rakambda.filesecure.utils.json.GeonamesTimeZone;
import kong.unirest.GenericType;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import us.fatehi.pointlocation6709.Angle;
import us.fatehi.pointlocation6709.Latitude;
import us.fatehi.pointlocation6709.Longitude;
import us.fatehi.pointlocation6709.PointLocation;
import us.fatehi.pointlocation6709.parse.PointLocationParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.regex.Pattern;

@Slf4j
public class MetadataExtractor{
	private final List<NameDateExtractor> dateFormats;
	private final List<MediaDateExtractor<?>> mediaDateExtractors;
	
	public MetadataExtractor(){
		dateFormats = new ArrayList<>();
		dateFormats.add(new Pattern1NameDateExtractorImpl());
		dateFormats.add(new Pattern2NameDateExtractorImpl());
		dateFormats.add(new Pattern3NameDateExtractorImpl());
		dateFormats.add(new Pattern4NameDateExtractorImpl());
		dateFormats.add(new Pattern5NameDateExtractorImpl());
		dateFormats.add(new Pattern6NameDateExtractorImpl());
		
		mediaDateExtractors = new ArrayList<>();
		mediaDateExtractors.add(new SimpleMediaDateExtractor<>(QuickTimeMetadataDirectory.class, QuickTimeMetadataDirectory.TAG_CREATION_DATE));
		mediaDateExtractors.add(new SimpleMediaDateExtractor<>(QuickTimeDirectory.class, QuickTimeDirectory.TAG_CREATION_TIME));
		mediaDateExtractors.add(new SimpleMediaDateExtractor<>(Mp4Directory.class, Mp4Directory.TAG_CREATION_TIME));
		mediaDateExtractors.add(new SimpleMediaDateExtractor<>(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL));
		mediaDateExtractors.add(new XmpMediaDateExtractor());
	}
	
	@NonNull
	public FileMetadata getMetadata(@NonNull Path path) throws IOException{
		return new FileMetadata(getDateFromMetadata(path), getCreationDate(path), getDateFromFileName(path));
	}
	
	@NonNull
	private ZonedDateTime getCreationDate(@NonNull Path path) throws IOException{
		var attr = Files.readAttributes(path, BasicFileAttributes.class);
		var date = Instant.ofEpochMilli(attr.lastModifiedTime().toMillis()).atZone(ZoneId.systemDefault());
		
		if(date.getYear() <= 1970){
			date = date.withYear(LocalDateTime.now().getYear());
		}
		
		return date;
	}
	
	@Nullable
	private ZonedDateTime getDateFromMetadata(@NonNull Path path){
		try{
			var metadata = ImageMetadataReader.readMetadata(path.toFile());
			if(Objects.isNull(metadata)){
				return null;
			}
			
			var zoneID = getZoneIdFromMetadata(metadata).orElse(ZoneId.systemDefault());
			var timeZone = TimeZone.getTimeZone(zoneID);
			
			for(var dataExtractor : mediaDateExtractors){
				for(var directory : metadata.getDirectoriesOfType(dataExtractor.getKlass())){
					if(Objects.nonNull(directory)){
						try{
							var result = runDirectoryExtractor(dataExtractor, directory, timeZone);
							if(Objects.nonNull(result)){
								return result;
							}
						}
						catch(ParseException e){
							log.warn("Invalid year with metadata directory {} for file {}", dataExtractor.getKlass().getName(), path);
						}
						catch(Exception e){
							log.error("Error processing metadata directory {} for {} => {}", dataExtractor.getKlass().getName(), path, e.getMessage());
						}
					}
				}
			}
		}
		catch(Exception e){
			log.error("Error getting date from metadata for {} => {}", path, e.getMessage());
		}
		return null;
	}
	
	@Nullable
	private ZonedDateTime runDirectoryExtractor(@NonNull MediaDateExtractor<?> dataExtractor, @NonNull Directory directory, @NonNull TimeZone timeZone) throws ParseException{
		log.debug("Trying {}", dataExtractor.getKlass().getName());
		
		var takenDateOptional = dataExtractor.parse(directory, timeZone);
		if(takenDateOptional.isEmpty()){
			return null;
		}
		
		var takenDate = takenDateOptional.get();
		log.debug("Matched metadata directory {}", directory);
		if(takenDate.getYear() < 1970){
			throw new ParseException("Invalid year", 0);
		}
		return takenDate;
	}
	
	@Nullable
	private ZonedDateTime getDateFromFileName(@NonNull Path path){
		var fileName = path.getFileName().toString();
		
		for(var nameDateExtractor : dateFormats){
			try{
				log.debug("Trying name extractor `{}`", nameDateExtractor);
				var dateOptional = nameDateExtractor.parse(fileName);
				if(dateOptional.isEmpty()){
					continue;
				}
				
				var date = dateOptional.get();
				if(date.getYear() < 1970){
					throw new ParseException("Invalid year", 0);
				}
				
				log.debug("Matched date format for {}", path);
				return date;
			}
			catch(ParseException e){
				log.warn("Invalid year with used format for file {}", path);
			}
			catch(DateTimeParseException ignored){
			}
			catch(Exception e){
				log.error("Error using format {} => {}", nameDateExtractor, e.getMessage());
			}
		}
		
		return null;
	}
	
	@NonNull
	private Optional<ZoneId> getZoneIdFromMetadata(@NonNull Metadata metadata){
		try{
			for(var gpsDirectory : metadata.getDirectoriesOfType(GpsDirectory.class)){
				var location = gpsDirectory.getGeoLocation();
				var zoneId = getZoneID(location.getLatitude(), location.getLongitude());
				if(zoneId.isPresent()){
					return zoneId;
				}
			}
			for(var quickTimeMetadataDirectory : metadata.getDirectoriesOfType(QuickTimeMetadataDirectory.class)){
				var repr = quickTimeMetadataDirectory.getString(0x050D);
				if(Objects.nonNull(repr) && !repr.isBlank()){
					var location = PointLocationParser.parsePointLocation(repr);
					var zoneId = getZoneID(location.getLatitude().getDegrees(), location.getLongitude().getDegrees());
					if(zoneId.isPresent()){
						return zoneId;
					}
				}
			}
			for(var xmpDirectory : metadata.getDirectoriesOfType(XmpDirectory.class)){
				var xmpValues = xmpDirectory.getXmpProperties();
				if(xmpValues.containsKey("exif:GPSLatitude") && xmpValues.containsKey("exif:GPSLongitude")){
					var zoneId = getAngle(xmpValues.get("exif:GPSLatitude"))
							.flatMap(lat -> getAngle(xmpValues.get("exif:GPSLongitude"))
									.flatMap(lon -> {
										var location = new PointLocation(new Latitude(lat), new Longitude(lon));
										return getZoneID(location.getLatitude().getDegrees(), location.getLongitude().getDegrees());
									}));
					if(zoneId.isPresent()){
						return zoneId;
					}
				}
			}
		}
		catch(Exception e){
			log.warn("Error getting GPS infos", e);
		}
		return Optional.empty();
	}
	
	/**
	 * Get the zoneID of a geolocation.
	 *
	 * @param latitude  The latitude.
	 * @param longitude The longitude.
	 *
	 * @return The zoneID corresponding to this location.
	 */
	@NonNull
	private static Optional<ZoneId> getZoneID(double latitude, double longitude){
		try{
			var request = Unirest.get("http://api.geonames.org/timezoneJSON")
					.queryString("lat", latitude)
					.queryString("lng", longitude)
					.queryString("username", "mrcraftcod")
					.asObject(new GenericType<GeonamesTimeZone>(){});
			if(request.isSuccess()){
				var geonamesTimeZone = request.getBody();
				return Optional.ofNullable(geonamesTimeZone.getTimezoneId());
			}
		}
		catch(Exception e){
			log.error("Error getting zoneID for coordinates {};{}", latitude, longitude, e);
		}
		return Optional.empty();
	}
	
	/**
	 * Convert a N/E/S/W coordinate to an angle one.
	 *
	 * @param s The coordinate to convert.
	 *
	 * @return The angle.
	 */
	@NonNull
	private static Optional<Angle> getAngle(@NonNull String s){
		var pattern = Pattern.compile("(\\d{1,3}),(\\d{1,2})\\.(\\d+)([NESW])");
		var matcher = pattern.matcher(s);
		if(matcher.matches()){
			var angle = Integer.parseInt(matcher.group(1))
					+ (Integer.parseInt(matcher.group(2)) / 60.0)
					+ (Double.parseDouble("0." + matcher.group(3)) / 60.0);
			angle *= getMultiplicand(matcher.group(4));
			return Optional.of(Angle.fromDegrees(angle));
		}
		return Optional.empty();
	}
	
	/**
	 * Get the sign of the angle depending of it's position (N/E/S/W).
	 *
	 * @param group The group (N/E/S/W).
	 *
	 * @return The sign of the angle.
	 */
	private static double getMultiplicand(@NonNull String group){
		return switch(group){
			case "W", "S" -> -1;
			default -> 1;
		};
	}
}
