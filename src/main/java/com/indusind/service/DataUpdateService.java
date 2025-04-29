package com.indusind.service;

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
							.put("ACCT_CLS_FLG", csvObjMap.getOrDefault("ACCT_CLS_FLG", ""))
							.put("ACCT_CLS_DATE",
									utility.getParsedDate(Utility.getTrimmedValue(csvObjMap, "ACCT_CLS_DATE")))
							.put("PARTICULAR", "Record Deleted in Finacle DB");
//							.put("ENTITY_CRE_FLG", csvObjMap.getOrDefault("ENTITY_CRE_FLG", ""))

					// .put("TS_CNT", csvObjMap.getOrDefault("TS_CNT", 0))// String TS_CNT value
//							.put("TS_CNT", safeParseInt(csvObjMap.getOrDefault("TS_CNT", "0").toString(), 0))
//							.put("FREZ_CODE", csvObjMap.getOrDefault("FREZ_CODE", ""));

					couchbaseConfig.getQueryResultCustomerMasterV6Scope("UPDATE "
							+ couchbaseConfig.getGamCollectionName()
							+ " USE KEYS $acid SET ACCT_CLS_FLG = $ACCT_CLS_FLG, ACCT_CLS_DATE = $ACCT_CLS_DATE, PARTICULAR = $PARTICULAR",
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

}