package com.harshith.resumematcher.auth;

import com.harshith.resumematcher.error.ApiException;
import com.harshith.resumematcher.user.User;
import com.harshith.resumematcher.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    // One message for "no such email" and "wrong password", so attackers can't
    // use login to find out which emails have accounts.
    static final String BAD_CREDENTIALS = "Invalid email or password";
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Checked when the email doesn't exist, so that case takes as long as a wrong password.
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalize(request.email());
        if (tooLongForBcrypt(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at most 72 bytes");
        }
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        try {
            return users.saveAndFlush(new User(email, passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the unique constraint catches it.
            throw emailTaken();
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        if (tooLongForBcrypt(request.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        Optional<User> user = users.findByEmail(normalize(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordOk) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        return jwtService.issue(user.get());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean tooLongForBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
    }
}
