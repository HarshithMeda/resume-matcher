package com.harshith.resumematcher.privacy;

import java.util.List;

/** Plain-language privacy summary for the frontend (GET /api/privacy). */
public record PrivacyNotice(
        String summary,
        List<String> whatWeStore,
        List<String> whatWeNeverStore,
        int retentionDays,
        String retention,
        List<String> yourControls) {

    static PrivacyNotice forRetention(int days) {
        return new PrivacyNotice(
                "We keep only what's needed to match your resume to jobs, and delete it automatically.",
                List.of(
                        "Your email address and a hashed version of your password (never the password itself).",
                        "The text extracted from resumes you upload, and their file names.",
                        "Analysis results: score, matched and missing skills, keywords and tips.",
                        "Your IP address and email, in memory only and for at most an hour, to limit"
                                + " login attempts and sign-ups. They are not saved to the database."),
                List.of(
                        "Original PDF files. They're read in memory, the text is extracted, and the file is discarded.",
                        "Job descriptions you paste. They're used for the analysis and then discarded.",
                        "Resume or job description text in server logs."),
                days,
                "Resumes and analyses are deleted automatically " + days + " days after they're created."
                        + " Your account stays until you delete it.",
                List.of(
                        "Delete any resume at any time; its analyses are deleted with it.",
                        "Download all your data as JSON (GET /api/me/export).",
                        "Delete your account and everything linked to it (DELETE /api/me, with your password)."));
    }
}
