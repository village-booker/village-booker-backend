package com.villagebooker.service;

import com.villagebooker.common.exception.NotFoundException;
import com.villagebooker.repo.UserRepo;
import com.villagebooker.dto.user.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepo userRepo;

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userRepo.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found."));
    }
}
