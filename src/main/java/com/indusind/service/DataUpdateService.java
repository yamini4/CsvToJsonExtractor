package com.indusind.service;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.couchbase.client.java.json.JsonObject;
import com.indusind.config.CouchbaseConfig;
import com.indusind.utility.Utility;
import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

@Service
public class DataUpdateService {

	@Autowired
	private CouchbaseConfig couchbaseConfig;

	@Autowired
	private FileStoringLogicService fileStoringLogicService;

	@Autowired
	Utility utility;

	@Value("${batchSize}")
	private Integer batchSize;

	private static final Logger logger = LoggerFactory.getLogger(DataUpdateService.class);

	public void updateGamData(List<String> acidList, List<JsonObject> listOfCSVFileData) {

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
							.put("ACCT_CLS_FLG", csvObjMap.getOrDefault("ACCT_CLS_FLG", "")).put("ACCT_CLS_DATE",
									utility.getParsedDate(Utility.getTrimmedValue(csvObjMap, "ACCT_CLS_DATE")));
//							.put("ENTITY_CRE_FLG", csvObjMap.getOrDefault("ENTITY_CRE_FLG", ""))

					// .put("TS_CNT", csvObjMap.getOrDefault("TS_CNT", 0))// String TS_CNT value
//							.put("TS_CNT", safeParseInt(csvObjMap.getOrDefault("TS_CNT", "0").toString(), 0))
//							.put("FREZ_CODE", csvObjMap.getOrDefault("FREZ_CODE", ""));

					couchbaseConfig.getQueryResultCustomerMasterV6Scope("UPDATE "
							+ couchbaseConfig.getGamCollectionName()
							+ " USE KEYS $acid SET ACCT_CLS_FLG = $ACCT_CLS_FLG, ACCT_CLS_DATE = $ACCT_CLS_DATE",
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

	public void gamDataWriteToCsv() {
		List<JsonObject> dataList = couchbaseConfig.getQueryResultCustomerMasterV6Scope(
				"SELECT ACID, ENTITY_CRE_FLG, ACCT_CLS_FLG, ACCT_CLS_DATE, FREZ_CODE, TS_CNT FROM "
						+ couchbaseConfig.getGamCollectionName() + " WHERE CIF_ID!='NULL' LIMIT 500",
				null);
		String filePath = System.getProperty("user.dir") + "/gamJsonfile.csv";
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

	public static int safeParseInt(String value, int defaultValue) {
		try {
			value = value == null ? "" : value.trim();
			return value.isEmpty() ? defaultValue : Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public void updateFinCustomerData(List<String> cifList, List<JsonObject> listOfCSVFileData) {

		List<JsonObject> listAccClosedataFromGam = couchbaseConfig.getQueryResultCustomerMasterV6Scope(
				"SELECT CUST_FIRST_NAME,CUST_LAST_NAME,CUST_MIDDLE_NAME,ORGKEY,RISK_PROFILE_SCORE,BLACKLISTED,ENTITY_CRE_FLAG,CUST_TYPE,SUSPENDED,SEGMENTATION_CLASS,CONSTITUTION_CODE,CUSTOMERMINOR,ADDRESS_LINE1,ADDRESS_LINE2,ADDRESS_LINE3,STAFFFLAG,CUSTOMERNREFLG,DATEUSERFIELD5,DATEUSERFIELD4,CORP_ID,SUSPEND_REASON FROM "
						+ couchbaseConfig.getFinCustomerCollectionName() + " USE KEYS $cifList",
				JsonObject.create().put("cifList", cifList));
		Map<String, JsonObject> customerMap = listAccClosedataFromGam.stream()
				.collect(Collectors.toMap(obj -> obj.getString("ORGKEY"), obj -> obj));

		// Update CSV data only if ACCT_CLS_FLG = "N"
		for (JsonObject csvObj : listOfCSVFileData) {
			String ORGKEY = csvObj.getString("ORGKEY");

			try {
				if (!customerMap.containsKey(ORGKEY)) {
					fileStoringLogicService.failedToUpdateFile(csvObj, "ORGKEY not aviable in capella");

				}
				Map<String, Object> csvObjMap = csvObj.toMap();
				JsonObject queryParam = JsonObject.create().put("ORGKEY", ORGKEY)
						.put("CUST_FIRST_NAME", csvObjMap.getOrDefault("CUST_FIRST_NAME", ""))
						.put("CUST_LAST_NAME", csvObjMap.getOrDefault("CUST_LAST_NAME", ""))
						.put("CUST_MIDDLE_NAME", csvObjMap.getOrDefault("CUST_MIDDLE_NAME", ""))
						.put("RISK_PROFILE_SCORE", csvObjMap.getOrDefault("RISK_PROFILE_SCORE", ""))
						.put("BLACKLISTED", csvObjMap.getOrDefault("BLACKLISTED", ""))
						.put("ENTITY_CRE_FLAG", csvObjMap.getOrDefault("ENTITY_CRE_FLAG", ""))
						.put("CUST_TYPE", csvObjMap.getOrDefault("CUST_TYPE", ""))
						.put("SUSPENDED", csvObjMap.getOrDefault("SUSPENDED", ""))
						.put("SEGMENTATION_CLASS", csvObjMap.getOrDefault("SEGMENTATION_CLASS", ""))
						.put("CONSTITUTION_CODE", csvObjMap.getOrDefault("CONSTITUTION_CODE", ""))
						.put("CUSTOMERMINOR", csvObjMap.getOrDefault("CUSTOMERMINOR", ""))
						.put("ADDRESS_LINE1", csvObjMap.getOrDefault("ADDRESS_LINE1", ""))
						.put("ADDRESS_LINE2", csvObjMap.getOrDefault("ADDRESS_LINE2", ""))
						.put("ADDRESS_LINE3", csvObjMap.getOrDefault("ADDRESS_LINE3", ""))
						.put("STAFFFLAG", csvObjMap.getOrDefault("STAFFFLAG", ""))
						.put("CUSTOMERNREFLG", csvObjMap.getOrDefault("CUSTOMERNREFLG", ""))
						.put("DATEUSERFIELD5", csvObjMap.getOrDefault("DATEUSERFIELD5", ""))
						.put("DATEUSERFIELD4", csvObjMap.getOrDefault("DATEUSERFIELD4", ""))
						.put("CORP_ID", csvObjMap.getOrDefault("CORP_ID", ""))
						.put("SUSPEND_REASON", csvObjMap.getOrDefault("SUSPEND_REASON", ""));

				couchbaseConfig.getQueryResultCustomerMasterV6Scope("UPDATE "
						+ couchbaseConfig.getFinCustomerCollectionName()
						+ " USE KEYS $ORGKEY SET CUST_FIRST_NAME=$CUST_FIRST_NAME,CUST_LAST_NAME=$CUST_LAST_NAME,CUST_MIDDLE_NAME=$CUST_MIDDLE_NAME,ORGKEY=$ORGKEY,RISK_PROFILE_SCORE=$RISK_PROFILE_SCORE,BLACKLISTED=$BLACKLISTED,ENTITY_CRE_FLAG=$ENTITY_CRE_FLAG,CUST_TYPE=$CUST_TYPE,SUSPENDED=$SUSPENDED,SEGMENTATION_CLASS=$SEGMENTATION_CLASS,CONSTITUTION_CODE=$CONSTITUTION_CODE,CUSTOMERMINOR=$CUSTOMERMINOR,ADDRESS_LINE1=$ADDRESS_LINE1,ADDRESS_LINE2=$ADDRESS_LINE2,ADDRESS_LINE3=$ADDRESS_LINE3,STAFFFLAG=$STAFFFLAG,CUSTOMERNREFLG=$CUSTOMERNREFLG,DATEUSERFIELD5=$DATEUSERFIELD5,DATEUSERFIELD4=$DATEUSERFIELD4,CORP_ID=$CORP_ID,SUSPEND_REASON=$SUSPEND_REASON",
						queryParam);
//					logger.info("Successfully updated Data : {}", csvObj);
				fileStoringLogicService.successfullUpdateFile(queryParam);

			} catch (Exception e) {
				fileStoringLogicService.failedToUpdateFile(csvObj, e.getMessage());
				logger.error("Failed to update : {} : {}", csvObj, e.getMessage(), e);
			}
		}
	}

}