package com.indusind;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
		String csvFile = "/gamJsonfile.csv";
		List<JsonObject> listOfCSVFileData = new ArrayList<>();

		try (CSVReader reader = new CSVReader(new FileReader(System.getProperty("user.dir") + csvFile))) {
			String[] headers = reader.readNext();
			String[] row;

			while ((row = reader.readNext()) != null) {
				JsonObject json = JsonObject.create();

				for (int i = 0; i < headers.length; i++) {
					json.put(headers[i], row[i]);
				}

				logger.info("JsonOutput : {}", json.toString());
				listOfCSVFileData.add(json);
			}
			for (int i = 0; i < listOfCSVFileData.size(); i += batchSize) {
				List<JsonObject> batch = listOfCSVFileData.subList(i,
						Math.min(i + batchSize, listOfCSVFileData.size()));

				List<String> acidList = batch.stream().map(obj -> obj.getString("ACID")).collect(Collectors.toList());

				updateGamData(acidList, batch);
			}
//			List<String> acidList = listOfCSVFileData.stream().map(obj -> obj.getString("ACID"))
//					.collect(Collectors.toList());
//			updateGamData(acidList, listOfCSVFileData);
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
					couchBaseConfig.getQueryResultCustomerMasterV6Scope("UPDATE "
							+ couchBaseConfig.getGamCollectionName()
							+ " USE KEYS $acid SET ACCT_CLS_FLG = $ACCT_CLS_FLG, ENTITY_CRE_FLG = $ENTITY_CRE_FLG, ACCT_CLS_FLG = $ACCT_CLS_FLG, ACCT_CLS_DATE = $ACCT_CLS_DATE, FREZ_CODE = $FREZ_CODE, TS_CNT = $TS_CNT",
							JsonObject.create().put("acid", acid).put("ACCT_CLS_FLG", csvObj.getString("ACCT_CLS_FLG"))
									.put("ENTITY_CRE_FLG", csvObj.getString("ENTITY_CRE_FLG"))
									.put("ACCT_CLS_FLG", csvObj.getString("ACCT_CLS_FLG"))
									.put("ACCT_CLS_DATE", csvObj.getString("ACCT_CLS_DATE"))
									.put("FREZ_CODE", csvObj.getString("FREZ_CODE"))
									.put("TS_CNT", csvObj.getString("TS_CNT")));
					logger.info("Successfully updated Data : {}", csvObj);
					fileStoringLogicService.successfullUpdateFile(csvObj);
				}
			} catch (Exception e) {
				fileStoringLogicService.failedToUpdateFile(csvObj, e.getMessage());
				logger.error("Failed to update : {} : {}", csvObj, e.getMessage(), e);
			}
		}

	}

}
