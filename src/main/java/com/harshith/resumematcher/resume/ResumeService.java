package com.harshith.resumematcher.resume;

import com.harshith.resumematcher.error.ApiException;
import com.harshith.resumematcher.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class ResumeService {

    static final int MAX_RESUMES_PER_USER = 5;
    static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final ResumeRepository resumes;
    private final UserRepository users;
    private final PdfTextExtractor extractor;
    private final TransactionTemplate tx;

    public ResumeService(ResumeRepository resumes, UserRepository users,
                         PdfTextExtractor extractor, PlatformTransactionManager txManager) {
        this.resumes = resumes;
        this.users = users;
        this.extractor = extractor;
        this.tx = new TransactionTemplate(txManager);
    }

    public ResumeDetail upload(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No file uploaded. Send the PDF in a form field named 'file'.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw tooLarge();
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The upload couldn't be read");
        }

        // Parse outside the transaction so we don't hold a database lock while reading the PDF.
        String text = extractor.extract(bytes);
        String fileName = cleanFileName(file.getOriginalFilename());

        return tx.execute(status -> {
            // Lock this user's row so two uploads at once can't both see "4 resumes" and make 6.
            users.lockById(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));
            if (resumes.countByUserId(userId) >= MAX_RESUMES_PER_USER) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "You can save up to " + MAX_RESUMES_PER_USER + " resumes. Delete one before uploading another.");
            }
            return ResumeDetail.from(resumes.save(new Resume(userId, fileName, text)));
        });
    }

    @Transactional(readOnly = true)
    public List<ResumeSummary> list(long userId) {
        return resumes.findSummariesByUserId(userId);
    }

    @Transactional(readOnly = true)
    public ResumeDetail get(long userId, long resumeId) {
        return resumes.findByIdAndUserId(resumeId, userId)
                .map(ResumeDetail::from)
                .orElseThrow(ResumeService::notFound);
    }

    @Transactional
    public void delete(long userId, long resumeId) {
        if (resumes.deleteByIdAndUserId(resumeId, userId) == 0) {
            throw notFound();
        }
    }

    /** Keep just the base name, drop control characters, cap the length. */
    static String cleanFileName(String original) {
        if (original == null) {
            return "resume.pdf";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.codePoints()
                .filter(cp -> !Character.isISOControl(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .strip();
        if (name.isEmpty()) {
            return "resume.pdf";
        }
        if (name.codePointCount(0, name.length()) > MAX_FILE_NAME_LENGTH) {
            name = name.substring(0, name.offsetByCodePoints(0, MAX_FILE_NAME_LENGTH));
        }
        return name;
    }

    static ApiException tooLarge() {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE, "File is too large (maximum 2 MB)");
    }

    // Same answer for "doesn't exist" and "belongs to someone else", so ids can't be probed.
    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Resume not found");
    }
}
