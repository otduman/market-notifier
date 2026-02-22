package com.dumanch1.marketnotifier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// @SpringBootApplication is a meta-annotation that combines three things:
//   1. @Configuration      → this class can define @Bean methods
//   2. @EnableAutoConfiguration → Spring Boot automatically configures Kafka,
//                                  Redis, WebFlux etc. based on what's on the classpath
//   3. @ComponentScan      → scans this package and all sub-packages for
//                            @Component, @Service, @Repository, @Controller beans
//
// @ConfigurationPropertiesScan scans for @ConfigurationProperties records/classes
// (BinanceProperties, AlertProperties) and registers them as beans automatically.
//
// @EnableScheduling activates Spring's @Scheduled method execution (used by
// AlertService.resolveSettledTrends() to periodically check for settled trends).
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MarketNotifierApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketNotifierApplication.class, args);
	}
}