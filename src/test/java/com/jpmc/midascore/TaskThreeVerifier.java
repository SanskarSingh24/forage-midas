package com.jpmc.midascore;

import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
public class TaskThreeVerifier {

    @Autowired
    private UserPopulator userPopulator;

    @Autowired
    private FileLoader fileLoader;

    @Autowired
    private KafkaProducer kafkaProducer;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void verifyWaldorfBalance() throws Exception {
        // populate users
        userPopulator.populate();

        // send all transactions from the TaskThree file
        String[] transactions = fileLoader.loadStrings("/test_data/mnbvcxz.vbnm");
        for (String t : transactions) {
            kafkaProducer.send(t);
        }

        // wait up to 15s for the listener to process and persist updates
        long deadline = System.currentTimeMillis() + 15000;
        Float balance = null;
        while (System.currentTimeMillis() < deadline) {
            for (UserRecord u : userRepository.findAll()) {
                if ("waldorf".equals(u.getName())) {
                    balance = u.getBalance();
                    if (Math.abs(balance - 444.55f) > 0.001f) {
                        break;
                    }
                }
            }
            if (balance != null && Math.abs(balance - 444.55f) > 0.001f) break;
            Thread.sleep(200);
        }

        if (balance == null) {
            for (UserRecord u : userRepository.findAll()) {
                if ("waldorf".equals(u.getName())) {
                    balance = u.getBalance();
                }
            }
        }

        long floored = (long) Math.floor(balance == null ? 0.0f : balance);
        System.out.println("WALDORF_BALANCE " + floored);
        org.junit.jupiter.api.Assertions.assertNotNull(balance, "waldorf balance should be present");
    }
}
