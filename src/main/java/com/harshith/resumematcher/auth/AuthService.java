package com.harshith.resumematcher.auth;

import com.harshith.resumematcher.error.ApiException;
import com.harshith.resumematcher.ratelimit.RateLimitedException;
import com.harshith.resumematcher.ratelimit.RateLimits;
import com.harshith.resumematcher.ratelimit.SlidingWindowLimiter;
import com.harshith.resumematcher.user.User;
import com.harshith.resumematcher.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private final RateLimits limits;
    // Checked when the email doesn't exist, so that case takes as long as a wrong password.
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RateLimits limits) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.limits = limits;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    @Transactional
    public User register(RegisterRequest request, String clientIp) {
        SlidingWindowLimiter perIp = limits.registrationsPerIp();
        perIp.tryAcquire(clientIp).ifPresent(wait -> {
            throw new RateLimitedException("Too many accounts have been created from your network."
                    + " Try again in " + RateLimitedException.describe(wait) + ".", wait);
        });
        try {
            return createUser(request);
        } catch (RuntimeException e) {
            perIp.release(clientIp); // only accounts actually created count toward the limit
            throw e;
        }
    }

    private User createUser(RegisterRequest request) {
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
    public TokenResponse login(LoginRequest request, String clientIp) {
        return authenticate(request.email(), request.password(), clientIp)
                .map(jwtService::issue)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS));
    }

    /**
     * Checks an email + password, with the failed-login limits (per email and per IP).
     * Used by login and by account deletion.
     *
     * @return the user if the password is right; empty if not (counted as a failed attempt)
     * @throws RateLimitedException if there were too many failed attempts recently
     */
    public Optional<User> authenticate(String rawEmail, String password, String clientIp) {
        String email = normalize(rawEmail);
        SlidingWindowLimiter perEmail = limits.loginFailuresPerEmail();
        SlidingWindowLimiter perIp = limits.loginFailuresPerIp();

        // Take a slot first (atomic), so parallel guesses can't slip past the limit.
        Optional<Duration> emailWait = perEmail.tryAcquire(email);
        if (emailWait.isPresent()) {
            throw tooManyFailures(emailWait.get());
        }
        Optional<Duration> ipWait = perIp.tryAcquire(clientIp);
        if (ipWait.isPresent()) {
            perEmail.release(email);
            throw tooManyFailures(ipWait.get());
        }

        Optional<User> user = tooLongForBcrypt(password) ? Optional.empty() : users.findByEmail(email);
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordOk = !tooLongForBcrypt(password) && passwordEncoder.matches(password, hash);

        if (user.isEmpty() || !passwordOk) {
            return Optional.empty(); // the slots stay taken: this was a failed attempt
        }
        // Success: clear this email's failures and don't count this attempt against the IP.
        perEmail.reset(email);
        perIp.release(clientIp);
        return user;
    }

    private static RateLimitedException tooManyFailures(Duration wait) {
        return new RateLimitedException("Too many failed login attempts. Try again in "
                + RateLimitedException.describe(wait) + ".", wait);
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
