package com.villagebooker.repo.notification;

import com.villagebooker.model.EmailMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailMessageRepo extends JpaRepository<EmailMessage, Long>, JpaSpecificationExecutor<EmailMessage> {

    List<EmailMessage> findByRecipientIdOrderById(Long recipientId);

    boolean existsByRecipientIdAndCreatedAtAfter(Long recipientId, Instant after);

    @EntityGraph(attributePaths = "recipient")
    Optional<EmailMessage> findWithRecipientById(Long id);

    @Override
    @EntityGraph(attributePaths = "recipient")
    Page<EmailMessage> findAll(Specification<EmailMessage> spec, Pageable pageable);
}
