package com.harshith.resumematcher.analysis.engine;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the important non-skill words of a job description (e.g. "payments", "scalable", "latency")
 * and checks which ones the resume uses.
 *
 * <p>Words are lowercased and lightly normalised so "services" matches "service" and
 * "technologies" matches "technology". Skill names are removed first (they're scored separately),
 * as are stop words (resources/analysis/stopwords.txt). The most frequent words win; ties go to
 * the word that appears first.
 */
@Component
public class KeywordExtractor {

    static final int MAX_KEYWORDS = 15;
    private static final int MIN_LENGTH = 3;
    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{N}]*");
    private static final Pattern EMAIL_OR_URL = Pattern.compile(
            "\\S+@\\S+|\\b(?:https?://|www\\.)\\S+|\\b[\\w-]+\\.(?:com|org|net|io|dev|co)\\b\\S*",
            Pattern.CASE_INSENSITIVE);

    private final SkillDictionary dictionary;
    private final Set<String> stopWords;

    public KeywordExtractor(SkillDictionary dictionary) {
        this.dictionary = dictionary;
        this.stopWords = loadStopWords();
    }

    public record KeywordResult(List<String> matched, List<String> missing) {
        public int total() {
            return matched.size() + missing.size();
        }
    }

    public KeywordResult compare(String jobDescription, String resumeText) {
        Set<String> resumeStems = stems(resumeText);
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String keyword : keywords(jobDescription)) {
            (resumeStems.contains(stem(keyword)) ? matched : missing).add(keyword);
        }
        return new KeywordResult(matched, missing);
    }

    /** Top keywords of the job description, most important first. */
    public List<String> keywords(String jobDescription) {
        record Count(String word, int firstIndex, int count) {
        }
        Map<String, Count> byStem = new LinkedHashMap<>();
        int index = 0;
        for (String word : words(dictionary.removeSkills(jobDescription))) {
            if (isCandidate(word)) {
                String stem = stem(word);
                int i = index;
                byStem.merge(stem, new Count(word, i, 1),
                        (old, now) -> new Count(old.word(), old.firstIndex(), old.count() + 1));
            }
            index++;
        }
        return byStem.values().stream()
                .sorted(Comparator.comparingInt(Count::count).reversed().thenComparingInt(Count::firstIndex))
                .limit(MAX_KEYWORDS)
                .map(Count::word)
                .toList();
    }

    private boolean isCandidate(String word) {
        return word.length() >= MIN_LENGTH && !stopWords.contains(word) && !stopWords.contains(stem(word));
    }

    private Set<String> stems(String text) {
        Set<String> set = new HashSet<>();
        for (String word : words(text)) {
            set.add(stem(word));
        }
        return set;
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        // Emails and links aren't wording: "jane@example.com" shouldn't match the keyword "example".
        String withoutLinks = EMAIL_OR_URL.matcher(text).replaceAll(" ");
        Matcher m = WORD.matcher(withoutLinks.toLowerCase(Locale.ROOT));
        while (m.find()) {
            words.add(m.group());
        }
        return words;
    }

    /** Very light plural folding; good enough to match "services" with "service". */
    static String stem(String word) {
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() > 3 && word.endsWith("s")
                && !word.endsWith("ss") && !word.endsWith("us") && !word.endsWith("is")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static Set<String> loadStopWords() {
        try (InputStream in = KeywordExtractor.class.getResourceAsStream("/analysis/stopwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("Stop word list not found");
            }
            Set<String> words = new HashSet<>();
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(w -> words.add(w.toLowerCase(Locale.ROOT)));
            return Set.copyOf(words);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
