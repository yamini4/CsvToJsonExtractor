package com.indusind.scheduler;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.couchbase.client.java.json.JsonObject;
import com.indusind.config.CouchbaseConfig;
import com.indusind.service.DataUpdateService;
import com.opencsv.CSVReader;

@Component
public class SchedulingClass {
	private static final Logger logger = LoggerFactory.getLogger(SchedulingClass.class);

	@Autowired
	private CouchbaseConfig couchbaseConfig;
//
//	@Autowired
//	private FileStoringLogicService fileStoringLogicService;

	@Autowired
	private DataUpdateService dataUpdateService;

	@Value("${batchSize}")
	private Integer batchSize;

	@Scheduled(fixedRate = 54000000) // 15hours
	public void processService() {
		logger.info("Scheduled");
		String csvFile = couchbaseConfig.getCsvFileName();
		List<JsonObject> listOfCSVFileData = new ArrayList<>();

		try (CSVReader reader = new CSVReader(new FileReader(System.getProperty("user.dir") + csvFile))) {
			String[] headers = reader.readNext();
			String[] row;

			while ((row = reader.readNext()) != null) {
				JsonObject json = JsonObject.create();

				for (int i = 0; i < headers.length; i++) {
					json.put(headers[i], row[i]);
				}

//				logger.info("JsonOutput : {}", json.toString());
				listOfCSVFileData.add(json);
			}
			logger.info("listOfCSVFileData extraction is done");
			ExecutorService executor = Executors.newFixedThreadPool(20);

			for (int i = 0; i < listOfCSVFileData.size(); i += batchSize) {
				int fromIndex = i;
				int toIndex = Math.min(i + batchSize, listOfCSVFileData.size());
				List<JsonObject> batch = new ArrayList<>(listOfCSVFileData.subList(fromIndex, toIndex));
				executor.submit(() -> {
					try {
						List<String> acidList = batch.stream().map(obj -> obj.getString("ACID"))
								.collect(Collectors.toList());
						dataUpdateService.updateGamData(acidList, batch);
					} catch (Exception e) {
						logger.error("Error processing batch from index {} to {}: {}", fromIndex, toIndex,
								e.getMessage(), e);
					}
				});
				logger.info("Submitted batch from index {} to {}", fromIndex, toIndex);
			}

			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.HOURS);
			logger.info("Process Completed");

//			List<String> acidList = listOfCSVFileData.stream().map(obj -> obj.getString("ACID"))
//					.collect(Collectors.toList());
//			dataUpdateService.updateGamData(acidList, listOfCSVFileData);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
