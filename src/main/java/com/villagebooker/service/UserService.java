package com.villagebooker.service;

import com.villagebooker.common.bank.BankAccountNumber;
import com.villagebooker.common.exception.BadRequestException;
import com.villagebooker.common.exception.ConflictException;
import com.villagebooker.common.exception.ForbiddenException;
import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.config.TermsProperties;
import com.villagebooker.dto.auth.AuthResponse;
import com.villagebooker.dto.user.BecomeHostRequest;
import com.villagebooker.dto.user.UserResponse;
import com.villagebooker.enums.Role;
import com.villagebooker.model.User;
import com.villagebooker.repo.UserRepo;
import com.villagebooker.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepo userRepo;
    private final JwtService jwtService;
    private final TermsProperties terms;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public AuthResponse becomeHost(Long userId, BecomeHostRequest request) {
        User user = findUser(userId);
        if (user.getRole() == Role.OWNER) {
            throw new ConflictException("User is already a host.");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenException("An admin account cannot become a host.");
        }
        String bankAccount = BankAccountNumber.normalize(request.bankAccount())
                .orElseThrow(() -> new BadRequestException("Bank account number is not valid."));

        user.setBankAccount(bankAccount);
        user.setHostTermsVersion(terms.hostVersion());
        user.setHostTermsAcceptedAt(Instant.now(clock));
        user.setRole(Role.OWNER);

        return AuthResponse.of(jwtService.issue(user), user);
    }

    private User findUser(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found."));
    }
}
