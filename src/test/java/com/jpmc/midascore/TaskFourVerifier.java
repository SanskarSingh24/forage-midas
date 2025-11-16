package com.jpmc.midascore;

import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
public class TaskFourVerifier {
    static final Logger logger = LoggerFactory.getLogger(TaskFourVerifier.class);

    @Autowired
    private KafkaProducer kafkaProducer;

    @Autowired
    private UserPopulator userPopulator;

    @Autowired
    private FileLoader fileLoader;

    @Autowired
    private UserRepository userRepository;

    @Test
    void verify_wilbur_balance() throws InterruptedException {
        userPopulator.populate();
        String[] transactionLines = fileLoader.loadStrings("/test_data/alskdjfh.fhdjsk");
        for (String transactionLine : transactionLines) {
            kafkaProducer.send(transactionLine);
        }
        Thread.sleep(3000);

        // Query wilbur's balance
        UserRecord wilbur = null;
        for (UserRecord user : userRepository.findAll()) {
            if ("wilbur".equalsIgnoreCase(user.getName())) {
                wilbur = user;
                break;
            }
        }

        if (wilbur != null) {
            logger.info("WILBUR_BALANCE {}", Math.floor(wilbur.getBalance()));
        } else {
            logger.warn("Wilbur not found in database");
        }
    }
}
