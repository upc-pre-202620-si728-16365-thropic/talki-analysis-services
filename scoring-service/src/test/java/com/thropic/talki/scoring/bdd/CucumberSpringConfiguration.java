package com.thropic.talki.scoring.bdd;

import com.thropic.talki.scoring.contract.AbstractRabbitIT;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/** Levanta scoring-service completo, con HTTP real, H2 y RabbitMQ real. */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration extends AbstractRabbitIT {
}
