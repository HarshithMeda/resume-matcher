package com.harshith.resumematcher.user;

import com.harshith.resumematcher.analysis.AnalysisService;
import com.harshith.resumematcher.auth.AuthService;
import com.harshith.resumematcher.error.ApiException;
import com.harshith.resumematcher.resume.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AccountService {

    private final UserRepository users;
    private final AuthService auth;
    private final ResumeService resumes;
    private final AnalysisService analyses;
    private final Clock clock;

    public AccountService(UserRepository users, AuthService auth, ResumeService resumes,
                          AnalysisService analyses, Clock clock) {
        this.users = users;
        this.auth = auth;
        this.resumes = resumes;
        this.analyses = analyses;
        this.clock = clock;
    }

    /**
     * Deletes the account after checking the password. The database deletes the user's resumes
     * and analyses with it (ON DELETE CASCADE). Wrong passwords count toward the login limits.
     */
    @Transactional
    public void deleteAccount(long userId, String password, String clientIp) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));
        boolean passwordOk = auth.authenticate(user.getEmail(), password, clientIp)
                .filter(u -> u.getId().equals(userId))
                .isPresent();
        if (!passwordOk) {
            // 403, not 401: the token is fine, the password isn't.
            throw new ApiException(HttpStatus.FORBIDDEN, "Incorrect password");
        }
        users.delete(user);
    }

    @Transactional(readOnly = true)
    public AccountExport export(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));
        return new AccountExport("resume-matcher-export-v1", clock.instant(), UserResponse.from(user),
                resumes.exportAll(userId), analyses.exportAll(userId));
    }
}
