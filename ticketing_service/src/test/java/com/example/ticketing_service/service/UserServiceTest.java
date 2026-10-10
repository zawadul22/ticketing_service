package com.example.ticketing_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing_service.TestcontainersConfiguration;
import com.example.ticketing_service.dto.RegisterRequest;
import com.example.ticketing_service.dto.UserResponse;
import com.example.ticketing_service.entity.Role;
import com.example.ticketing_service.entity.User;
import com.example.ticketing_service.exception.DuplicateEmailException;
import com.example.ticketing_service.repository.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UserServiceTest {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${app.admin.email}")
	private String adminEmail;

	@Test
	void registerStoresNormalizedEmailAndHashedPassword() {
		UserResponse response = userService.register(new RegisterRequest("  Alice@Example.COM ", "secret123"));

		assertThat(response.id()).isNotNull();
		assertThat(response.email()).isEqualTo("alice@example.com");
		assertThat(response.role()).isEqualTo(Role.USER);

		User saved = userRepository.findByEmail("alice@example.com").orElseThrow();
		assertThat(saved.getPasswordHash()).isNotEqualTo("secret123");
		assertThat(passwordEncoder.matches("secret123", saved.getPasswordHash())).isTrue();
	}

	@Test
	void registerRejectsEmailThatDiffersOnlyInCase() {
		userService.register(new RegisterRequest("carol@example.com", "secret123"));

		assertThatThrownBy(() -> userService.register(new RegisterRequest("CAROL@example.com", "other-pass")))
				.isInstanceOf(DuplicateEmailException.class);
	}

	@Test
	void adminIsSeededAtStartup() {
		User admin = userRepository.findByEmail(adminEmail).orElseThrow();

		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(userService.createAdminIfMissing(adminEmail, "whatever1")).isFalse();
	}

}
