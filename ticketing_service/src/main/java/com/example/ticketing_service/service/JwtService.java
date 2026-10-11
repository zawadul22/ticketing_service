package com.example.ticketing_service.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.example.ticketing_service.config.JwtProperties;
import com.example.ticketing_service.dto.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

	private final JwtEncoder jwtEncoder;
	private final JwtProperties jwtProperties;

	public Jwt issueToken(UserResponse user) {
		// JWT timestamps are whole seconds; truncating keeps the returned expiresAt equal to the token's exp
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(JwtProperties.ISSUER)
				// The subject is the user id, so controllers know who's calling without a DB lookup
				.subject(user.id().toString())
				.issuedAt(now)
				.expiresAt(now.plus(jwtProperties.ttl()))
				.claim("email", user.email())
				.claim("role", user.role().name())
				.build();
		// Spring's encoder defaults to RS256; an HMAC secret needs HS256 set explicitly
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
	}

}
