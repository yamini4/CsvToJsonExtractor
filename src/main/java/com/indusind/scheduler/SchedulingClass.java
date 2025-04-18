package com.indusind.scheduler;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.couchbase.client.java.json.JsonObject;
import com.indusind.config.CouchbaseConfig;
import com.indusind.service.FileStoringLogicService;
import com.indusind.utility.Utility;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

@Component
public class SchedulingClass {
	private static final Logger logger = LoggerFactory.getLogger(SchedulingClass.class);

	@Autowired
	private CouchbaseConfig couchbaseConfig;

	@Autowired
	private FileStoringLogicService fileStoringLogicService;

	@Value("${batchSize}")
	private Integer batchSize = 500;

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

	private void updateGamData(List<String> acidList, List<JsonObject> listOfCSVFileData) {

		List<JsonObject> listAccClosedataFromGam = couchbaseConfig.getQueryResultCustomerMasterV6Scope(
				"SELECT IFMISSINGORNULL(ACCT_CLS_FLG, '') AS ACCT_CLS_FLG, ACID FROM "
						+ couchbaseConfig.getGamCollectionName() + " USE KEYS $acidList",
				JsonObject.create().put("acidList", acidList));
		Map<String, String> gamMap = listAccClosedataFromGam.stream()
				.collect(Collectors.toMap(obj -> obj.getString("ACID"), obj -> obj.getString("ACCT_CLS_FLG")));

		// Update CSV data only if ACCT_CLS_FLG = "N"
		for (JsonObject csvObj : listOfCSVFileData) {
			String acid = csvObj.getString("ACID");
			String acctClsFlg = gamMap.get(acid);
			try {
				if (null == acctClsFlg || acctClsFlg.isEmpty()) {
					fileStoringLogicService.failedToUpdateFile(csvObj, "Data Not Found");
					logger.info("Data Not Found With This ACID : {}", acid);
				}

				if ("N".equalsIgnoreCase(acctClsFlg)) {
					Map<String, Object> csvObjMap = csvObj.toMap();
					JsonObject queryParam = JsonObject.create().put("acid", acid)
							.put("ACCT_CLS_FLG", csvObjMap.getOrDefault("ACCT_CLS_FLG", ""))
							.put("ENTITY_CRE_FLG", csvObjMap.getOrDefault("ENTITY_CRE_FLG", ""))
							.put("ACCT_CLS_DATE",
									Utility.getDateString(Utility.getTrimmedValue(csvObjMap, "ACCT_CLS_DATE"),
											"yyyy-MM-dd HH:mm:ss"))

							.put("TS_CNT", csvObjMap.getOrDefault("TS_CNT", 0))//String TS_CNT value
//							.put("TS_CNT", safeParseInt(csvObjMap.getOrDefault("TS_CNT", "0").toString(), 0))//Integer TS_CNT value
							.put("FREZ_CODE", csvObjMap.getOrDefault("FREZ_CODE", ""));

					couchbaseConfig.getQueryResultCustomerMasterV6Scope("UPDATE "
							+ couchbaseConfig.getGamCollectionName()
							+ " USE KEYS $acid SET ACCT_CLS_FLG = $ACCT_CLS_FLG, ENTITY_CRE_FLG = $ENTITY_CRE_FLG, ACCT_CLS_DATE = $ACCT_CLS_DATE, FREZ_CODE = $FREZ_CODE, TS_CNT = $TS_CNT",
							queryParam);
//					logger.info("Successfully updated Data : {}", csvObj);
					fileStoringLogicService.successfullUpdateFile(queryParam);
				} else {
					fileStoringLogicService.failedToUpdateFile(csvObj, "ACCT_CLS_FLG is 'Y'");
				}

			} catch (Exception e) {
				fileStoringLogicService.failedToUpdateFile(csvObj, e.getMessage());
				logger.error("Failed to update : {} : {}", csvObj, e.getMessage(), e);
			}
		}
	}

	public static int safeParseInt(String value, int defaultValue) {
		try {
			value = value == null ? "" : value.trim();
			return value.isEmpty() ? defaultValue : Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return defaultValue;
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
