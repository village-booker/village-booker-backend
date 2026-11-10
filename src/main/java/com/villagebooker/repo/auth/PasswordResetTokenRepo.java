package com.villagebooker.repo.auth;

import com.villagebooker.model.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepo extends JpaRepository<PasswordResetToken, Long> {

    // locked, so two requests with the same link cannot both use it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    boolean existsByUserIdAndCreatedAtAfter(Long userId, Instant after);

    @Modifying(flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :at where t.user.id = :userId and t.usedAt is null")
    int closeUnused(@Param("userId") Long userId, @Param("at") Instant at);
}
