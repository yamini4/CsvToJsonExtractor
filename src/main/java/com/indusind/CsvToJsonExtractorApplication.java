package com.indusind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = {
		org.springframework.boot.autoconfigure.couchbase.CouchbaseAutoConfiguration.class }, scanBasePackages = "com.indusind")
public class CsvToJsonExtractorApplication {
	public static void main(String[] args) {
		System.setProperty("logging.level.org.springframework.integration", "DEBUG");
		System.setProperty("com.jcraft.jsch.logging", "DEBUG");
		SpringApplication.run(CsvToJsonExtractorApplication.class, args);
	}
}
