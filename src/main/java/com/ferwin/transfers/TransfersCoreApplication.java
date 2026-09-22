package com.ferwin.transfers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TransfersCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransfersCoreApplication.class, args);
	}
}
