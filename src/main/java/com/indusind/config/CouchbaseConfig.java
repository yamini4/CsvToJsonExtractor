package com.indusind.config;

import java.time.Duration;
import java.util.List;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;

import lombok.Getter;

@Configuration
public class CouchbaseConfig {

	@Value("${spring.couchbase.connection-string}")
	private String connectionString;

	@Value("${spring.couchbase.username}")
	private String userName;

	@Value("${spring.couchbase.password}")
	private String password;

	@Value("${spring.couchbase.bucket.name}")
	private String bucketName;

	@Value("${spring.couchbase.scope.name}")
	private String scopeName;

	@Getter
	@Value("${spring.couchbase.collection.fin_gam.name}")
	private String gamCollectionName;

	@Getter
	@Value("${csvFileName}")
	private String csvFileName;

	private Cluster cluster;
	private Bucket iCacheBucket;
	private Scope customerMasterScope;

	private static final Logger logger = LoggerFactory.getLogger(CouchbaseConfig.class);

	public CouchbaseConfig() {
	}

	@PostConstruct
	public void init() {
		try {
			logger.info("init method invoked : {}", connectionString);
//			ClusterEnvironment environment = ClusterEnvironment.builder()
//				    .securityConfig(SecurityConfig
//				        .enableTls(true)
//				        .trustManagerFactory(TrustSource
//				            .factory(InsecureTrustManagerFactory.INSTANCE)))
//				    .build();
			this.cluster = Cluster.connect(connectionString, userName, password);
//			ClusterEnvironment env = ClusterEnvironment.builder().securityConfig(SecurityConfig.enableTls(true))
//					.build();
//			this.cluster = Cluster.connect(connectionString,
//					ClusterOptions.clusterOptions(userName, password).environment(env));
//

//			ClusterEnvironment environment = ClusterEnvironment.builder().securityConfig(SecurityConfig.enableTls(true))
//					.build();
//
//			Cluster cluster = Cluster.connect(connectionString,
//					ClusterOptions.clusterOptions(userName, password).environment(environment));

			this.cluster.waitUntilReady(Duration.ofSeconds(60));
			this.iCacheBucket = cluster.bucket(bucketName);
			this.iCacheBucket.waitUntilReady(Duration.ofSeconds(60));

			this.customerMasterScope = iCacheBucket.scope(scopeName);
			logger.info("Couchbase connection established successfully.");
		} catch (Exception e) {
			logger.error("Error initializing Couchbase: {}", e.getMessage());
			e.printStackTrace();
		}
	}

	public List<JsonObject> getQueryResultCustomerMasterV6Scope(String query, JsonObject parameters) {
		return customerMasterScope.query(query, QueryOptions.queryOptions().adhoc(false).parameters(parameters))
				.rowsAsObject();
	}
}