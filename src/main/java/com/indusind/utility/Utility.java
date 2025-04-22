package com.indusind.utility;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Utility {

	private String outputPattern;
	private String dateFormatsRegex;
//	private String dateFormats;
	private String[] requestDateFormat;
	private Pattern DATE_PATTERN;

	@Value("${output-date-pattern}")
	private String outputPatternProp;

	@Value("${date-formats-regex}")
	private String dateFormatsRegexProp;

	@Value("${date-formats}")
	private String dateFormatsProp;

	public Utility(@Value("${output-date-pattern}") String ouputPattern,
			@Value("${date-formats-regex}") String dateFormatsRegex,
			@Value("${date-formats}") String dateFormats) {
		this.requestDateFormat = dateFormats.split(",");
		this.outputPattern = ouputPattern;
		this.dateFormatsRegex = dateFormatsRegex;
		DATE_PATTERN = Pattern.compile(dateFormatsRegex);
	}

//	@PostConstruct
//	public void init() {
//		try {
//			this.outputPattern = outputPatternProp;
//			this.dateFormatsRegex = "\\b(0?[1-9]|[12][0-9]|3[01])/(0?[1-9]|1[0-2])/\\d{4}\\b|\\b\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3,9})?(?:Z)?)?\\b|\\b\\d{4}/\\d{2}/\\d{2}\\b|\\b\\d{2}/\\d{2}/\\d{4}\\b|\\b\\d{2}-\\d{2}-\\d{4}\\b|\\b\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}\\b|\\b\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}\\b|\\b\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\b";
//			this.dateFormats = dateFormatsProp;
//			this.requestDateFormat = dateFormats.split(",");
//			System.out.println(dateFormatsRegex);
//			this.DATE_PATTERN = Pattern.compile(dateFormatsRegex);
//			System.out.println(DATE_PATTERN);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//
//	}

	private static final Logger logger = LoggerFactory.getLogger(Utility.class);

	public String getParsedDate(String dateStr) {
		dateStr = StringUtils.isNotBlank(dateStr) ? dateStr.replace("\"", "").replace("\"", "") : dateStr;
		if (StringUtils.isNotBlank(dateStr)) {
			Matcher matcher = DATE_PATTERN.matcher(dateStr);
			if (matcher.matches()) {
				try {
					Date date = DateUtils.parseDateStrictly(dateStr, requestDateFormat);
					return new SimpleDateFormat(outputPattern).format(date);
				} catch (Exception ignored) {
					logger.info("dateStr : {}, getParsedDate Exception: {}", dateStr, ignored);
				}
			}
			return dateStr;
		}
		return dateStr;
	}

	public static String getTrimmedValue(Map<String, Object> map, String key) {
		return Optional.ofNullable(map.getOrDefault(key, "")).map(Object::toString).orElse("").replace("null", "")
				.trim();
	}

//	public static String getDateString(String dateString, String targetFormat) {
//	String[] formats = { "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss",
//			"dd-MM-yyyy HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss.S",
//			"dd-MMM-yy", "dd-MM-yy", "dd-MM-yyyy", "dd/MM/yyyy", "dd/M/yyyy", "d/MM/yyyy", "d/M/yyyy", "MM-yyyy-dd",
//			"yyyy-MM-dd" };
//
//	if (dateString == null || dateString.isBlank()) {
//		return dateString;
//	}
//
//	LocalDateTime parsedDateTime = null;
//
//	for (String format : formats) {
//		try {
//			DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(format);
//			if (format.contains("HH") || format.contains("mm") || format.contains("ss")) {
//				parsedDateTime = LocalDateTime.parse(dateString, inputFormatter);
//			} else {
//				LocalDate parsedDate = LocalDate.parse(dateString, inputFormatter);
//				parsedDateTime = parsedDate.atStartOfDay();
//			}
//			break;
//		} catch (DateTimeParseException e1) {
////			logger.error("dateString:{},targetFormat:{}", dateString, targetFormat);
//		}
//	}
//
//	if (parsedDateTime != null) {
//		DateTimeFormatter targetFormatter = DateTimeFormatter.ofPattern(targetFormat);
//		return parsedDateTime.format(targetFormatter);
//	}
//	return dateString;
//}
}
