package com.example.ticketing_service_backend.service;

import java.time.Instant;

import com.example.ticketing_service_backend.configuration.JwtProperties;
import com.example.ticketing_service_backend.dto.AuthResponse;
import com.example.ticketing_service_backend.dto.UserResponse;
import com.example.ticketing_service_backend.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public AuthResponse issue(User user) {
        var issuedAt = Instant.now();
        var expiresAt = issuedAt.plusSeconds(properties.ttlSeconds());
        var claims = JwtClaimsSet.builder().issuer(JwtProperties.ISSUER)
                .subject(user.getId().toString()).issuedAt(issuedAt).expiresAt(expiresAt).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new AuthResponse(token.getTokenValue(), "Bearer", expiresAt, UserResponse.from(user));
    }
}
