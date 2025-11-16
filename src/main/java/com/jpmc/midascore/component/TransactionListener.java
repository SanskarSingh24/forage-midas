package com.jpmc.midascore.component;

import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.repository.TransactionRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class TransactionListener {

    private static final Logger logger = LoggerFactory.getLogger(TransactionListener.class);
    private static final String INCENTIVE_API_URL = "http://localhost:8082/incentive";

    private final List<Transaction> received = Collections.synchronizedList(new ArrayList<>());

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;

    public TransactionListener(UserRepository userRepository, TransactionRepository transactionRepository, RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.restTemplate = restTemplate;
    }

    @KafkaListener(topics = "${general.kafka-topic}", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void listen(Transaction transaction) {
        logger.info("Received transaction: {}", transaction);
        received.add(transaction);

        // validate sender and recipient exist
        UserRecord sender = userRepository.findById(transaction.getSenderId());
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());

        if (sender == null || recipient == null) {
            logger.warn("Discarding transaction because sender or recipient not found: {}", transaction);
            return;
        }

        // validate sender has enough balance
        if (sender.getBalance() < transaction.getAmount()) {
            logger.warn("Discarding transaction because insufficient funds: {}", transaction);
            return;
        }

        // query incentives API
        float incentiveAmount = 0f;
        try {
            Incentive incentive = restTemplate.postForObject(INCENTIVE_API_URL, transaction, Incentive.class);
            if (incentive != null && incentive.getAmount() >= 0) {
                incentiveAmount = incentive.getAmount();
                logger.info("Received incentive: {}", incentiveAmount);
            }
        } catch (Exception e) {
            logger.warn("Failed to query incentives API: {}", e.getMessage());
            incentiveAmount = 0f;
        }

        // apply transaction: deduct from sender, add to recipient
        sender.setBalance(sender.getBalance() - transaction.getAmount());
        // incentive is added only to recipient
        recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentiveAmount);

        // persist changes and transaction record
        userRepository.save(sender);
        userRepository.save(recipient);
        TransactionRecord record = new TransactionRecord(sender, recipient, transaction.getAmount(), incentiveAmount);
        transactionRepository.save(record);

        if ("waldorf".equalsIgnoreCase(sender.getName()) || "waldorf".equalsIgnoreCase(recipient.getName())) {
            UserRecord w = "waldorf".equalsIgnoreCase(sender.getName()) ? sender : recipient;
            logger.info("WALDORF_BALANCE {}", Math.floor(w.getBalance()));
        }
    }

    public List<Transaction> getReceived() {
        return received;
    }
}
