package fr.rakambda.filesecure.files.strategy;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.xmp.XmpDirectory;
import fr.rakambda.filesecure.files.NewFile;
import fr.rakambda.filesecure.files.extractor.media.MediaDateExtractor;
import fr.rakambda.filesecure.files.extractor.media.SimpleMediaDateExtractor;
import fr.rakambda.filesecure.files.extractor.media.XmpMediaDateExtractor;
import fr.rakambda.filesecure.files.extractor.name.NameDateExtractor;
import fr.rakambda.filesecure.files.extractor.name.Pattern1NameDateExtractorImpl;
import fr.rakambda.filesecure.files.extractor.name.Pattern2NameDateExtractorImpl;
import fr.rakambda.filesecure.files.extractor.name.Pattern3NameDateExtractorImpl;
import fr.rakambda.filesecure.utils.json.GeonamesTimeZone;
import kong.unirest.GenericType;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.regex.Pattern;

@Slf4j
public class ByDateRenaming implements RenamingStrategy{
	private final DateTimeFormatter outputDateFormat;
	private final List<NameDateExtractor> dateFormats;
	private final List<MediaDateExtractor<?>> mediaDateExtractors;
	
	/**
	 * Constructor.
	 * <p>
	 * The output format will be `yyyy-MM-dd HH.mm.ss`.
	 */
	public ByDateRenaming(){
		dateFormats = new ArrayList<>();
		dateFormats.add(new Pattern1NameDateExtractorImpl());
		dateFormats.add(new Pattern2NameDateExtractorImpl());
		dateFormats.add(new Pattern3NameDateExtractorImpl());
		
		mediaDateExtractors = new ArrayList<>();
		mediaDateExtractors.add(new SimpleMediaDateExtractor<>(QuickTimeMetadataDirectory.class, QuickTimeMetadataDirectory.TAG_CREATION_DATE));
		mediaDateExtractors.add(new SimpleMediaDateExtractor<>(QuickTimeDirectory.class, QuickTimeDirectory.TAG_CREATION_TIME));
		mediaDateExtractors.add(new SimpleMediaDateExtractor<>(Mp4Directory.class, Mp4Directory.TAG_CREATION_TIME));
		mediaDateExtractors.add(new SimpleMediaDateExtractor<>(ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL));
		mediaDateExtractors.add(new XmpMediaDateExtractor());
		outputDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss", Locale.ENGLISH).withZone(ZoneId.systemDefault());
	}
	
	/**
	 * Constructor.
	 *
	 * @param dateFormats         The extractors to extract data from the file name.
	 * @param mediaDateExtractors The extractors to extract data from the dictionaries of the file.
	 * @param outputDateFormat    The output format.
	 */
	public ByDateRenaming(@NotNull List<NameDateExtractor> dateFormats, @NotNull List<MediaDateExtractor<?>> mediaDateExtractors, @NotNull DateTimeFormatter outputDateFormat){
		this.dateFormats = dateFormats;
		this.mediaDateExtractors = mediaDateExtractors;
		this.outputDateFormat = outputDateFormat;
	}
	
	@Override
	@NotNull
	public NewFile renameFile(@NotNull Path path) throws IOException{
		var filename = path.getFileName().toString();
		final var prefix = "";
		var dotIndex = filename.lastIndexOf('.');
		var extension = dotIndex < 0 ? "" : filename.substring(dotIndex);
		var name = dotIndex < 0 ? filename : filename.substring(0, dotIndex);
		var attr = Files.readAttributes(path, BasicFileAttributes.class);
		var createdDate = Instant.ofEpochMilli(attr.lastModifiedTime().toMillis()).atZone(ZoneId.systemDefault());
		return processMetadata(path, name, extension)
				.orElseGet(() -> processFileName(path, prefix, extension, name, createdDate));
	}
	
	private NewFile processFileName(Path path, String prefix, String extension, String name, ZonedDateTime createdDate){
		for(var nameDateExtractor : dateFormats){
			try{
				log.debug("Trying extractor `{}`", nameDateExtractor);
				var dateOptional = nameDateExtractor.parse(name);
				if(dateOptional.isPresent()){
					var date = dateOptional.get();
					if(date.getYear() < 1970){
						throw new ParseException("Invalid year", 0);
					}
					log.debug("Matched date format for {}{}", name, extension);
					return new NewFile(outputDateFormat.format(date), extension, path.getParent(), date, path);
				}
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
		log.debug("Unrecognized date format : {}{}, using file last modified time", name, extension);
		var theDate = createdDate;
		if(createdDate.getYear() <= 1970){
			theDate = createdDate.withYear(LocalDateTime.now().getYear());
		}
		return new NewFile(prefix + outputDateFormat.format(theDate), extension, path.getParent(), theDate, path);
	}
	
	/**
	 * Process the metadata of a file to attempt to extract it's creation date.
	 *
	 * @param path      The path of the file.
	 * @param name      The name of the file.
	 * @param extension The extension of the file.
	 *
	 * @return A potential newFile object.
	 */
	@NotNull
	private Optional<NewFile> processMetadata(@NotNull Path path, @NotNull String name, @NotNull String extension){
		try{
			var metadata = ImageMetadataReader.readMetadata(path.toFile());
			if(Objects.nonNull(metadata)){
				var zoneID = getZoneIdFromMetadata(metadata).orElse(ZoneId.systemDefault());
				var timeZone = TimeZone.getTimeZone(zoneID);
				for(var dataExtractor : mediaDateExtractors){
					for(var directory : metadata.getDirectoriesOfType(dataExtractor.getKlass())){
						try{
							log.debug("Trying {}", dataExtractor.getKlass().getName());
							if(Objects.nonNull(directory)){
								var takenDateOptional = dataExtractor.parse(directory, timeZone);
								if(takenDateOptional.isPresent()){
									var takenDate = takenDateOptional.get();
									log.debug("Matched directory {} for {}{}", directory, name, extension);
									if(takenDate.getYear() < 1970){
										throw new ParseException("Invalid year", 0);
									}
									return Optional.of(new NewFile(outputDateFormat.format(takenDate), extension, path.getParent(), takenDate, path));
								}
							}
						}
						catch(ParseException e){
							log.warn("Invalid year with directory {} for file {}", dataExtractor.getKlass().getName(), path);
						}
						catch(Exception e){
							log.error("Error processing directory {} for {}{} => {}", dataExtractor.getKlass().getName(), name, extension, e.getMessage());
						}
					}
				}
			}
		}
		catch(Exception e){
			log.error("Error processing metadata for {}{} => {}", name, extension, e.getMessage());
		}
		return Optional.empty();
	}
	
	/**
	 * Get a potential zoneID from the geolocation present in the metadata of the file
	 *
	 * @param metadata The metadata.
	 *
	 * @return The potential zoneID found.
	 */
	@NotNull
	private Optional<ZoneId> getZoneIdFromMetadata(@NotNull Metadata metadata){
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
	@NotNull
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
	@NotNull
	private static Optional<Angle> getAngle(@NotNull String s){
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
	private static double getMultiplicand(@NotNull String group){
		return switch(group){
			case "W", "S" -> -1;
			default -> 1;
		};
	}
}
