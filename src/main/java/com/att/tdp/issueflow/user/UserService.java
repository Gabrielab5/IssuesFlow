package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.common.annotation.Audited;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long userId) {
        return UserMapper.toResponse(findUser(userId));
    }

    @Transactional
    @Audited(action = "CREATE", entityType = "User")
    public UserResponse create(CreateUserRequest request) {
        assertUsernameAvailable(request.username());
        assertEmailAvailable(request.email());

        User user = UserMapper.toEntity(request, passwordEncoder.encode(request.password()));
        try {
            return UserMapper.toResponse(userRepository.save(user));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Username or email is already in use");
        }
    }

    @Transactional
    @Audited(action = "UPDATE", entityType = "User")
    public void update(Long userId, UpdateUserRequest request) {
        User user = findUser(userId);
        UserMapper.updateEntity(user, request);
    }

    @Transactional
    @Audited(action = "DELETE", entityType = "User")
    public void delete(Long userId) {
        User user = findUser(userId);
        userRepository.delete(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }

    private void assertUsernameAvailable(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is already in use");
        }
    }

    private void assertEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already in use");
        }
    }
}
