package com.indusind.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.couchbase.client.java.json.JsonObject;

@Service
public class FileStoringLogicService {

	@Value("${successFileName}")
	private String successDataFileName;

	@Value("${failedFileName}")
	private String failedDataFileName;

	private final Logger logger = LoggerFactory.getLogger(FileStoringLogicService.class);

	public void successfullUpdateFile(JsonObject data) {
		StringBuilder formattedContent = new StringBuilder();
		formattedContent.append(String.format("%s", data));
//		.append("\n");
		createOrUpdateTextFile(formattedContent.toString(), System.getProperty("user.dir") + successDataFileName);
	}

	public void failedToUpdateFile(JsonObject data, String reasonForFailure) {
		StringBuilder formattedContent = new StringBuilder();
		formattedContent.append(String.format("%s, REASON_FOR_FAILURE:%s", data, reasonForFailure));
//		.append("\n");

		createOrUpdateTextFile(formattedContent.toString(), System.getProperty("user.dir") + failedDataFileName);
	}

	public File createOrUpdateTextFile(String content, String fileName) {
		try {
			File file = new File(fileName);

			if (file.getParentFile() != null && !file.getParentFile().exists() && file.getParentFile().mkdirs()) {
				logger.info("Directories created for file: {}", fileName);
			}

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
				writer.write(content);
				writer.newLine();
			}

			return file;
		} catch (IOException e) {
			logger.error("Error creating/updating file: {}, Exception: {}", fileName, e.getMessage());
			return null;
		}
	}

}