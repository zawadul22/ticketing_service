package com.example.ticketing_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.ticketing_service.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AdminSeeder implements ApplicationRunner {

	private final UserService userService;
	private final String adminEmail;
	private final String adminPassword;

	public AdminSeeder(UserService userService, @Value("${app.admin.email}") String adminEmail,
			@Value("${app.admin.password}") String adminPassword) {
		this.userService = userService;
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userService.createAdminIfMissing(adminEmail, adminPassword)) {
			log.info("Created admin user {}", adminEmail);
		}
	}

}
