package com.harshith.resumematcher.resume;

import com.harshith.resumematcher.error.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Turns PDF bytes into plain text, enforcing the upload rules.
 * Works entirely in memory: the PDF is never written to disk.
 * Error messages are fixed strings, so no file content can leak into responses or logs.
 */
@Component
public class PdfTextExtractor {

    static final int MAX_PAGES = 5;
    /** Below this many non-space characters we assume a scanned (image-only) PDF. */
    static final int MIN_TEXT_CHARS = 100;
    /** Five pages of real resume text is far below this; more means something odd. */
    static final int MAX_TEXT_CHARS = 50_000;
    /** Cap on memory PDFBox may use while decoding (guards against "zip bomb" PDFs). */
    private static final long MAX_DECODE_BYTES = 32L * 1024 * 1024;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public String extract(byte[] bytes) {
        if (!startsWithPdfMagic(bytes)) {
            // Checked on the actual bytes; the file name and Content-Type header are ignored.
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF files are accepted");
        }

        try (PDDocument doc = Loader.loadPDF(bytes, "", null, null,
                MemoryUsageSetting.setupMainMemoryOnly(MAX_DECODE_BYTES).streamCache)) {

            if (doc.isEncrypted()) {
                // Opened with an empty password but still has restrictions: treat as protected too.
                throw passwordProtected();
            }
            if (doc.getNumberOfPages() > MAX_PAGES) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "PDF has too many pages (maximum " + MAX_PAGES + ")");
            }
            if (doc.getNumberOfPages() == 0) {
                throw unreadable();
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // reads multi-column layouts in visual order
            String text = clean(stripper.getText(doc));

            if (countNonWhitespace(text) < MIN_TEXT_CHARS) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "This PDF has almost no selectable text. It looks like a scanned image, "
                                + "and scanned resumes aren't supported yet. "
                                + "Please upload a PDF exported from a word processor.");
            }
            if (text.length() > MAX_TEXT_CHARS) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "PDF contains more text than a resume normally has");
            }
            return text;

        } catch (InvalidPasswordException e) {
            throw passwordProtected();
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            // Corrupt or hostile file. PDFBox messages aren't passed on or logged.
            throw unreadable();
        }
    }

    private static boolean startsWithPdfMagic(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** PostgreSQL TEXT can't store NUL characters; also drop other invisible control characters. */
    private static String clean(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        raw.codePoints()
                .filter(cp -> cp == '\n' || cp == '\t' || !Character.isISOControl(cp))
                .forEach(sb::appendCodePoint);
        return sb.toString().replace("\r", "").strip();
    }

    private static int countNonWhitespace(String s) {
        return (int) s.codePoints().filter(cp -> !Character.isWhitespace(cp)).count();
    }

    private static ApiException passwordProtected() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                "Password-protected PDFs aren't supported. Please upload an unlocked copy.");
    }

    private static ApiException unreadable() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "This PDF couldn't be read. It may be damaged.");
    }
}
