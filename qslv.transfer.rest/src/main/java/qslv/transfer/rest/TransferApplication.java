package qslv.transfer.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"qslv.transfer.rest", "qslv.util"})
public class TransferApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(TransferApplication.class);
        application.run(args);
	}

}
