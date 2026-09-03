package com.redhun.aiswarya_ledger_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class AiswaryaLedgerApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiswaryaLedgerApiApplication.class, args);
	}

}
