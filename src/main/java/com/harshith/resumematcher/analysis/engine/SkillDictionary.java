package com.harshith.resumematcher.analysis.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The skills dictionary (resources/analysis/skills.json) and the code that finds skills in text.
 *
 * <p>Matching is whole-word and case-insensitive, with custom word edges instead of regex {@code \b},
 * because {@code \b} can't handle names like "C++", "C#" or ".NET":
 * <ul>
 *   <li>a match can't be preceded by a letter, digit, "_" or "." ("Node.js" doesn't contain "JS");</li>
 *   <li>a match can't be followed by a letter, digit, "_", "+", "#" or ".letter"
 *       ("Java" doesn't match "JavaScript", "C" doesn't match "C++" or "C#");</li>
 *   <li>1-2 character names (C, R, Go, C#) also can't touch "-", "&amp;" or "'"
 *       ("C-level", "R&amp;D", "Go-to-market", "Objective-C").</li>
 * </ul>
 */
@Component
public class SkillDictionary {

    static final String RESOURCE = "/analysis/skills.json";
    static final Set<String> CATEGORIES = Set.of(
            "language", "framework", "database", "cloud", "devops",
            "messaging", "tool", "testing", "practice", "data");

    private static final String BEFORE = "(?<![\\p{L}\\p{N}_.])";
    private static final String AFTER = "(?![\\p{L}\\p{N}_+#])(?!\\.[\\p{L}\\p{N}])";
    private static final String BEFORE_SHORT = "(?<![\\p{L}\\p{N}_.\\-&'])";
    private static final String AFTER_SHORT = AFTER + "(?![\\-&'])";

    private final List<CompiledSkill> skills;
    private final Map<Skill, List<Skill>> implies;

    public SkillDictionary() {
        this(SkillDictionary.class.getResourceAsStream(RESOURCE));
    }

    SkillDictionary(InputStream json) {
        if (json == null) {
            throw new IllegalStateException("Skill dictionary not found: " + RESOURCE);
        }
        try (json) {
            SkillFile file = JsonMapper.builder().build().readValue(json, SkillFile.class);
            this.skills = compile(file.skills());
            this.implies = resolveImplies(file.skills(), skills);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read skill dictionary", e);
        }
    }

    public int size() {
        return skills.size();
    }

    public List<Skill> all() {
        return skills.stream().map(CompiledSkill::skill).toList();
    }

    /**
     * Skills mentioned in the text, in order of first appearance.
     *
     * <p>A mention that sits inside a longer skill's mention doesn't count on its own:
     * "GitHub Actions" is not also "GitHub", "Tailwind CSS" is not also "CSS".
     */
    public List<Skill> findIn(String text) {
        record Span(Skill skill, int start, int end) {
            boolean insideLonger(Span other) {
                return !other.skill.equals(skill) && other.start <= start && end <= other.end
                        && other.end - other.start > end - start;
            }
        }
        List<Span> spans = new ArrayList<>();
        for (CompiledSkill s : skills) {
            Matcher m = s.pattern().matcher(text);
            while (m.find()) {
                spans.add(new Span(s.skill(), m.start(), m.end()));
            }
        }
        Map<Skill, Integer> firstPosition = new java.util.LinkedHashMap<>();
        for (Span span : spans) {
            boolean shadowed = spans.stream().anyMatch(span::insideLonger);
            if (!shadowed) {
                firstPosition.merge(span.skill(), span.start(), Math::min);
            }
        }
        return firstPosition.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * The skill whose mention ends exactly at the end of the text (ignoring trailing spaces), if any.
     * If several do ("Spring Data JPA" and "JPA"), the longest mention wins.
     */
    public Optional<Skill> skillAtEnd(String text) {
        String trimmed = text.stripTrailing();
        Skill best = null;
        int bestStart = Integer.MAX_VALUE;
        for (CompiledSkill s : skills) {
            Matcher m = s.pattern().matcher(trimmed);
            while (m.find()) {
                if (m.end() == trimmed.length() && m.start() < bestStart) {
                    best = s.skill();
                    bestStart = m.start();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** The text with every skill mention blanked out (used so keywords don't double-count skills). */
    public String removeSkills(String text) {
        String result = text;
        for (CompiledSkill s : skills) {
            result = s.pattern().matcher(result).replaceAll(" ");
        }
        return result;
    }

    // ---- loading ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SkillFile(List<SkillDefinition> skills) {
    }

    record SkillDefinition(String name, String category, List<String> aliases, List<String> caseSensitive,
                           List<String> implies) {
    }

    private record CompiledSkill(Skill skill, Pattern pattern) {
    }

    /**
     * Skills a resume is assumed to have because of skills it names, e.g. "Spring Data JPA" implies
     * JPA, Hibernate and Spring Framework. Only for the resume side; one level (not transitive).
     *
     * @param found skills found in the resume, in order
     * @return implied skill → the first found skill that implies it; skills already in {@code found} are left out
     */
    public Map<Skill, Skill> impliedBy(List<Skill> found) {
        Set<Skill> direct = Set.copyOf(found);
        Map<Skill, Skill> result = new java.util.LinkedHashMap<>();
        for (Skill source : found) {
            for (Skill target : implies.getOrDefault(source, List.of())) {
                if (!direct.contains(target)) {
                    result.putIfAbsent(target, source);
                }
            }
        }
        return result;
    }

    private static Map<Skill, List<Skill>> resolveImplies(List<SkillDefinition> definitions, List<CompiledSkill> compiled) {
        Map<String, Skill> byName = new HashMap<>();
        compiled.forEach(c -> byName.put(c.skill().name(), c.skill()));
        Map<Skill, List<Skill>> result = new HashMap<>();
        for (SkillDefinition d : definitions) {
            if (d.implies() == null || d.implies().isEmpty()) {
                continue;
            }
            List<Skill> targets = new ArrayList<>();
            for (String name : d.implies()) {
                Skill target = byName.get(name);
                if (target == null || name.equals(d.name())) {
                    throw new IllegalStateException("Skill '" + d.name() + "' has an invalid implies entry: " + name);
                }
                targets.add(target);
            }
            result.put(byName.get(d.name()), List.copyOf(targets));
        }
        return Map.copyOf(result);
    }

    private static List<CompiledSkill> compile(List<SkillDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalStateException("Skill dictionary is empty");
        }
        Map<String, String> aliasOwner = new HashMap<>(); // lowercased alias -> skill name
        List<CompiledSkill> compiled = new ArrayList<>();

        for (SkillDefinition d : definitions) {
            if (d.name() == null || d.name().isBlank() || !CATEGORIES.contains(d.category())) {
                throw new IllegalStateException("Invalid skill entry: " + d.name() + " / " + d.category());
            }
            List<String> sensitive = d.caseSensitive() == null ? List.of() : d.caseSensitive();
            List<String> insensitive = new ArrayList<>(d.aliases() == null ? List.of() : d.aliases());
            if (!sensitive.contains(d.name())) {
                insensitive.add(d.name());
            }

            record Form(String alias, boolean ignoreCase) {
            }
            List<Form> forms = new ArrayList<>();
            for (String alias : insensitive) {
                claim(aliasOwner, alias, d.name());
                forms.add(new Form(alias, true));
            }
            for (String alias : sensitive) {
                claim(aliasOwner, alias, d.name());
                forms.add(new Form(alias, false));
            }
            // Longest first: regex takes the first alternative that matches, and we want the whole
            // mention ("Tailwind CSS", not just "Tailwind") so shorter skills inside it are ignored.
            forms.sort(Comparator.comparingInt((Form f) -> f.alias().length()).reversed());
            Pattern pattern = Pattern.compile(forms.stream()
                    .map(f -> aliasRegex(f.alias(), f.ignoreCase()))
                    .collect(Collectors.joining("|")));
            compiled.add(new CompiledSkill(new Skill(d.name(), d.category()), pattern));
        }
        return List.copyOf(compiled);
    }

    /** Each alias may belong to only one skill, otherwise results would be ambiguous. */
    private static void claim(Map<String, String> owners, String alias, String skill) {
        String key = alias.strip().toLowerCase(Locale.ROOT);
        String previous = owners.putIfAbsent(key, skill);
        if (previous != null && !previous.equals(skill)) {
            throw new IllegalStateException("Alias '" + alias + "' is used by both " + previous + " and " + skill);
        }
    }

    private static String aliasRegex(String alias, boolean ignoreCase) {
        String[] words = alias.strip().split("\\s+");
        // "Spring Boot" also matches "Spring  Boot" or a line break between the words (common in PDF text).
        String body = java.util.Arrays.stream(words).map(Pattern::quote).collect(Collectors.joining("\\s+"));
        if (ignoreCase) {
            body = "(?iu:" + body + ")";
        }
        boolean isShort = words.length == 1 && alias.strip().length() <= 2;
        return isShort
                ? BEFORE_SHORT + body + AFTER_SHORT
                : BEFORE + body + AFTER;
    }
}
