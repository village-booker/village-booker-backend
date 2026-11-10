package com.villagebooker.repo.notification;

import com.villagebooker.model.EmailMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailMessageRepo extends JpaRepository<EmailMessage, Long> {

    List<EmailMessage> findByRecipientIdOrderById(Long recipientId);
}
