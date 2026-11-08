package com.villagebooker.repo.user;

import com.villagebooker.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// Emails are always stored lowercase (AuthService normalizes them), so an exact match is enough
// and it uses the unique index on app_user.email. IgnoreCase would turn into upper(email) = upper(?)
// and scan the whole table.
// JpaSpecificationExecutor: the admin's user list with optional filters (text, role)
public interface UserRepo extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // JwtAuthFilter on every request with a token: empty = the user no longer exists
    @Query("select case when u.blockedAt is null then false else true end from User u where u.id = :id")
    Optional<Boolean> findBlockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
