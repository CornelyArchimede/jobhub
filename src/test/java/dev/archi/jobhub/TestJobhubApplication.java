package dev.archi.jobhub;

import org.springframework.boot.SpringApplication;

public class TestJobhubApplication {

	public static void main(String[] args) {
		SpringApplication.from(JobhubApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
