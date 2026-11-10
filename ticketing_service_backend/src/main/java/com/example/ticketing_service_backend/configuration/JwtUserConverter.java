package com.example.ticketing_service_backend.configuration;

import java.util.List;

import com.example.ticketing_service_backend.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;

    public JwtUserConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new BadCredentialsException("Invalid token subject");
        }
        if (jwt.getExpiresAt() == null) {
            throw new BadCredentialsException("Token expiration required");
        }
        var user = users.findById(userId).orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        // Read the current role from the database rather than trusting a stale role claim.
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())), user.getId().toString());
    }
}
