package com.villagebooker.repo.user;

import com.villagebooker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Emails are always stored lowercase (AuthService normalizes them), so an exact match is enough
// and it uses the unique index on app_user.email. IgnoreCase would turn into upper(email) = upper(?)
// and scan the whole table.
public interface UserRepo extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
