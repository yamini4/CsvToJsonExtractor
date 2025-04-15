package com.indusind;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.couchbase.client.java.json.JsonObject;
import com.indusind.config.CouchbaseConfig;
import com.indusind.service.FileStoringLogicService;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

@SpringBootApplication
public class CsvToJsonExtractorApplication implements ApplicationContextAware {

	private static ApplicationContext context;

	private static Integer batchSize = 500;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		context = applicationContext;
	}

	private static final Logger logger = LoggerFactory.getLogger(CsvToJsonExtractorApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(CsvToJsonExtractorApplication.class, args);
//		CouchbaseConfig couchBaseConfig = context.getBean(CouchbaseConfig.class);
		String csvFile = context.getBean(CouchbaseConfig.class).getCsvFileName();
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
						updateGamData(acidList, batch);
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
//			updateGamData(acidList, listOfCSVFileData);

//			CouchbaseConfig couchBaseConfig = context.getBean(CouchbaseConfig.class);
//			writeToCsv(couchBaseConfig.getCustomerMasterV6Scope().query("SELECT fin_gam.* FROM fin_gam").rowsAsObject(),
//					System.getProperty("user.dir") + "/gamJsonfile.csv");
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static void updateGamData(List<String> acidList, List<JsonObject> listOfCSVFileData) {
		CouchbaseConfig couchBaseConfig = context.getBean(CouchbaseConfig.class);
		FileStoringLogicService fileStoringLogicService = context.getBean(FileStoringLogicService.class);

		List<JsonObject> listAccClosedataFromGam = couchBaseConfig.getQueryResultCustomerMasterV6Scope(
				"SELECT ACCT_CLS_FLG, ACID FROM " + couchBaseConfig.getGamCollectionName() + " USE KEYS $acidList",
				JsonObject.create().put("acidList", acidList));
		Map<String, String> gamMap = listAccClosedataFromGam.stream()
				.collect(Collectors.toMap(obj -> obj.getString("ACID"), obj -> obj.getString("ACCT_CLS_FLG")));

		// Update CSV data only if ACCT_CLS_FLG = "N"
		for (JsonObject csvObj : listOfCSVFileData) {
			String acid = csvObj.getString("ACID");
			String acctClsFlg = gamMap.get(acid);
			if (null == acctClsFlg || acctClsFlg.isEmpty()) {
				fileStoringLogicService.failedToUpdateFile(csvObj, "Data Not Found");
				logger.info("Data Not Found With This ACID : {}", acid);
			}
			try {
				if ("N".equalsIgnoreCase(acctClsFlg)) {
					Map<String, Object> csvObjMap = csvObj.toMap();
					couchBaseConfig.getQueryResultCustomerMasterV6Scope("UPDATE "
							+ couchBaseConfig.getGamCollectionName()
							+ " USE KEYS $acid SET ACCT_CLS_FLG = $ACCT_CLS_FLG, ENTITY_CRE_FLG = $ENTITY_CRE_FLG, ACCT_CLS_DATE = $ACCT_CLS_DATE, FREZ_CODE = $FREZ_CODE, TS_CNT = $TS_CNT",
							JsonObject.create().put("acid", acid)
									.put("ACCT_CLS_FLG", csvObjMap.getOrDefault("ACCT_CLS_FLG", ""))
									.put("ENTITY_CRE_FLG", csvObjMap.getOrDefault("ENTITY_CRE_FLG", ""))
									.put("ACCT_CLS_DATE", csvObjMap.getOrDefault("ACCT_CLS_DATE", ""))
									.put("FREZ_CODE", csvObjMap.getOrDefault("FREZ_CODE", ""))
									.put("TS_CNT", csvObjMap.getOrDefault("TS_CNT", "0")));
//					logger.info("Successfully updated Data : {}", csvObj);
					fileStoringLogicService.successfullUpdateFile(csvObj);
				}
			} catch (Exception e) {
				fileStoringLogicService.failedToUpdateFile(csvObj, e.getMessage());
				logger.error("Failed to update : {} : {}", csvObj, e.getMessage(), e);
			}
		}

	}

	public static void writeToCsv(List<JsonObject> dataList, String filePath) {
		try (ICSVWriter writer = new CSVWriterBuilder(new FileWriter(filePath))
				.withQuoteChar(CSVWriter.NO_QUOTE_CHARACTER).build()) {

			// Write header
			String[] header = { "ACID", "ENTITY_CRE_FLG", "ACCT_CLS_FLG", "ACCT_CLS_DATE", "FREZ_CODE", "TS_CNT" };
			writer.writeNext(header);

			// Write rows
			for (JsonObject obj : dataList) {
				String[] row = { String.valueOf(obj.get("acid")),
						obj.getString("ENTITY_CRE_FLG") != null ? obj.getString("ENTITY_CRE_FLG") : "",
						obj.getString("ACCT_CLS_FLG") != null ? obj.getString("ACCT_CLS_FLG") : "",
						obj.getString("ACCT_CLS_DATE") != null ? obj.getString("ACCT_CLS_DATE") : "",
						obj.getString("FREZ_CODE") != null ? obj.getString("FREZ_CODE") : "",
						String.valueOf(obj.get("TS_CNT")) };
				writer.writeNext(row);
			}

			logger.info("CSV file written to: {}" + filePath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
