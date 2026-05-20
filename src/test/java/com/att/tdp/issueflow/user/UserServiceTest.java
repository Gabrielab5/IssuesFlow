package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void findAllReturnsUserResponses() {
        when(userRepository.findAll()).thenReturn(List.of(user(1L, "admin", "admin@example.com", UserRole.ADMIN)));

        List<UserResponse> responses = userService.findAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().username()).isEqualTo("admin");
    }

    @Test
    void findByIdReturnsUserResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", "admin@example.com", UserRole.ADMIN)));

        UserResponse response = userService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void createHappyPathHashesPasswordAndPersistsUser() {
        CreateUserRequest request = new CreateUserRequest(
                "jdoe",
                "jdoe@example.com",
                "John Doe",
                "DEVELOPER",
                "secret123"
        );
        when(userRepository.existsByUsername("jdoe")).thenReturn(false);
        when(userRepository.existsByEmail("jdoe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        UserResponse response = userService.create(request);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.username()).isEqualTo("jdoe");
        assertThat(response.email()).isEqualTo("jdoe@example.com");
        assertThat(response.fullName()).isEqualTo("John Doe");
        assertThat(response.role()).isEqualTo(UserRole.DEVELOPER);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    void createDuplicateUsernameThrowsConflict() {
        CreateUserRequest request = new CreateUserRequest(
                "jdoe",
                "jdoe@example.com",
                "John Doe",
                "DEVELOPER",
                "secret123"
        );
        when(userRepository.existsByUsername("jdoe")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Username is already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createDuplicateEmailThrowsConflict() {
        CreateUserRequest request = new CreateUserRequest(
                "jdoe",
                "jdoe@example.com",
                "John Doe",
                "DEVELOPER",
                "secret123"
        );
        when(userRepository.existsByUsername("jdoe")).thenReturn(false);
        when(userRepository.existsByEmail("jdoe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email is already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateChangesFullNameAndRole() {
        User user = user(2L, "jdoe", "jdoe@example.com", UserRole.DEVELOPER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        userService.update(2L, new UpdateUserRequest("Jane Doe", "ADMIN"));

        assertThat(user.getFullName()).isEqualTo("Jane Doe");
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void updateNonExistentThrowsNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(404L, new UpdateUserRequest("Jane Doe", "ADMIN")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found with id: 404");
    }

    @Test
    void deleteCascadesNothingWeird() {
        User user = user(2L, "jdoe", "jdoe@example.com", UserRole.DEVELOPER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        userService.delete(2L);

        verify(userRepository).findById(2L);
        verify(userRepository).delete(user);
        verifyNoMoreInteractions(userRepository);
    }

    private User user(Long id, String username, String email, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(username + " Full");
        user.setRole(role);
        user.setPasswordHash("hash");
        return user;
    }
}
