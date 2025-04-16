package com.indusind.utility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Utility {
	private static final Logger logger = LoggerFactory.getLogger(Utility.class);

	public static String getDateString(String dateString, String targetFormat) {
		String[] formats = { "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss",
				"dd-MM-yyyy HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss.S",
				"dd-MMM-yy", "dd-MM-yy", "dd-MM-yyyy", "dd/MM/yyyy", "MM-yyyy-dd", "yyyy-MM-dd" };

		if (dateString == null || dateString.isBlank()) {
			return dateString;
		}

		LocalDateTime parsedDateTime = null;

		for (String format : formats) {
			try {
				DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(format);
				if (format.contains("HH") || format.contains("mm") || format.contains("ss")) {
					parsedDateTime = LocalDateTime.parse(dateString, inputFormatter);
				} else {
					LocalDate parsedDate = LocalDate.parse(dateString, inputFormatter);
					parsedDateTime = parsedDate.atStartOfDay();
				}
				break;
			} catch (DateTimeParseException e1) {
//				logger.error("dateString:{},targetFormat:{}", dateString, targetFormat);
			}
		}

		if (parsedDateTime != null) {
			DateTimeFormatter targetFormatter = DateTimeFormatter.ofPattern(targetFormat);
			return parsedDateTime.format(targetFormatter);
		}
		return dateString;
	}

	public static String getTrimmedValue(Map<String, Object> map, String key) {
		return Optional.ofNullable(map.getOrDefault(key, "")).map(Object::toString).orElse("").replace("null", "")
				.trim();
	}
}
