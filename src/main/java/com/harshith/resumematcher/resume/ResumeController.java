package com.harshith.resumematcher.resume;

import com.harshith.resumematcher.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService service;

    public ResumeController(ResumeService service) {
        this.service = service;
    }

    @Operation(summary = "Upload a PDF resume (max 2 MB, 5 pages). Only the extracted text is saved.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeDetail upload(@AuthenticationPrincipal Jwt jwt,
                               @RequestPart(value = "file", required = false) MultipartFile file) {
        return service.upload(CurrentUser.id(jwt), file);
    }

    @Operation(summary = "List your resumes (without the text)")
    @GetMapping
    public List<ResumeSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @Operation(summary = "Get one of your resumes, including its text")
    @GetMapping("/{id}")
    public ResumeDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @Operation(summary = "Delete one of your resumes")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.delete(CurrentUser.id(jwt), id);
    }
}
