package com.harshith.resumematcher.analysis.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides, for each skill in a job description, whether it is required or preferred.
 *
 * <p>How it reads the text:
 * <ol>
 *   <li>Split into short pieces: lines, bullets and sentences.</li>
 *   <li>A short piece with no skills that looks like a heading ("Requirements:", "Nice to have")
 *       sets the current section. Neutral headings ("Responsibilities", "Benefits") reset it.</li>
 *   <li>Wording in the piece itself wins over the section: "must", "required" make it required;
 *       "nice to have", "a plus", "preferred" make it preferred. If both appear, it's required.</li>
 *   <li>Otherwise the section decides. No section or wording means required.</li>
 *   <li>A skill that is required anywhere in the posting stays required.</li>
 *   <li>"AWS (EC2, S3, RDS)" is one skill group, not four skills.</li>
 * </ol>
 */
@Component
public class RequirementClassifier {

    private static final Pattern PREFERRED_WORDING = Pattern.compile(
            "\\b(nice[- ]to[- ]haves?|preferred|preferably|an? plus|are a plus|bonus|ideally|desirable|desired"
                    + "|good to have|would be (great|nice)|advantageous|an advantage|not required|optional)\\b");
    private static final Pattern REQUIRED_WORDING = Pattern.compile(
            "\\b(required|requires?|must|mandatory|essential|needs? to|you('ll| will)? need|requirements?)\\b");
    // "not required" / "not a must" are preferred wording, so hide them from the required check.
    private static final Pattern NEGATED_REQUIRED = Pattern.compile(
            "\\bnot (required|mandatory|a must|essential|necessary)\\b");

    private static final Pattern PREFERRED_HEADING = Pattern.compile(
            "\\b(nice[- ]to[- ]haves?|preferred|bonus|pluses|plus|desired|desirable|good to have"
                    + "|extra credit|optional)\\b");
    private static final Pattern REQUIRED_HEADING = Pattern.compile(
            "\\b(requirements?|required|must[- ]haves?|qualifications|what you('ll| will)? need"
                    + "|what you('ll| will)? bring|you have|you bring|about you|who you are|skills"
                    + "|experience|essential|mandatory)\\b");
    private static final Pattern NEUTRAL_HEADING = Pattern.compile(
            "\\b(responsibilities|what you('ll| will) do|duties|the role|about (us|the role|the team)"
                    + "|who we are|benefits|perks|compensation|overview|tech stack|our stack|location"
                    + "|salary|how to apply|why join|why us)\\b");

    private static final Pattern PARENTHESES = Pattern.compile("\\(([^()]*)\\)");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?;])\\s+|;");
    private static final Pattern BULLETS = Pattern.compile("[•·▪◦●]");
    private static final int MAX_HEADING_WORDS = 6;

    private final SkillDictionary dictionary;

    public RequirementClassifier(SkillDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /**
     * Skills in the job description, in order of first appearance, each marked required or preferred.
     *
     * <p>"Parent (child, child)" with no required/preferred wording inside the parentheses, such as
     * "AWS (EC2, S3, RDS)", becomes one group: the parent with the children as sub-skills. A child
     * that the job also lists on its own elsewhere stays a separate skill and leaves the group.
     */
    public List<JobSkill> classify(String jobDescription) {
        record Entry(Importance importance, Set<Skill> subSkills) {
        }
        Map<Skill, Entry> entries = new LinkedHashMap<>();
        Set<Skill> standalone = new HashSet<>();
        Importance section = null; // null = no section yet / neutral section

        for (String piece : pieces(jobDescription)) {
            String lower = piece.toLowerCase(Locale.ROOT);
            List<Skill> skills = dictionary.findIn(piece);

            if (skills.isEmpty()) {
                if (looksLikeHeading(piece)) {
                    section = headingSection(lower, section);
                }
                continue;
            }

            Importance wording = wording(lower);
            Importance importance = wording != null ? wording : section == null ? Importance.REQUIRED : section;
            Map<Skill, Skill> parentOf = groupsIn(piece);

            for (Skill skill : skills) {
                Skill parent = parentOf.get(skill);
                if (parent != null) {
                    entries.computeIfAbsent(parent, p -> new Entry(importance, new LinkedHashSet<>()))
                            .subSkills().add(skill);
                    continue;
                }
                standalone.add(skill);
                Entry old = entries.get(skill);
                if (old == null) {
                    entries.put(skill, new Entry(importance, new LinkedHashSet<>()));
                } else if (old.importance() != Importance.REQUIRED && importance == Importance.REQUIRED) {
                    // Required anywhere beats preferred elsewhere.
                    entries.put(skill, new Entry(Importance.REQUIRED, old.subSkills()));
                }
            }
        }

        List<JobSkill> result = new ArrayList<>();
        entries.forEach((skill, entry) -> {
            List<Skill> subs = entry.subSkills().stream().filter(s -> !standalone.contains(s)).toList();
            result.add(new JobSkill(skill, entry.importance(), subs));
        });
        return result;
    }

    /** For "AWS (EC2, S3, RDS)" in this piece: EC2 → AWS, S3 → AWS, RDS → AWS. */
    private Map<Skill, Skill> groupsIn(String piece) {
        Map<Skill, Skill> parentOf = new HashMap<>();
        Matcher m = PARENTHESES.matcher(piece);
        while (m.find()) {
            List<Skill> children = dictionary.findIn(m.group(1));
            if (children.isEmpty()) {
                continue;
            }
            dictionary.skillAtEnd(piece.substring(0, m.start())).ifPresent(parent -> {
                for (Skill child : children) {
                    if (!child.equals(parent)) {
                        parentOf.putIfAbsent(child, parent);
                    }
                }
            });
        }
        return parentOf;
    }

    /** Lines, bullets and sentences. */
    List<String> pieces(String text) {
        List<String> pieces = new ArrayList<>();
        for (String line : text.split("\\R")) {
            for (String bullet : BULLETS.split(line)) {
                for (String sentence : SENTENCE_END.split(splitParentheses(bullet))) {
                    String cleaned = sentence.replaceFirst("^[\\s\\-*–—>+]+", "").strip();
                    if (!cleaned.isEmpty()) {
                        pieces.add(cleaned);
                    }
                }
            }
        }
        return pieces;
    }

    // A parenthesis with both a skill and its own wording becomes a separate piece:
    //   "Java, Spring Boot (Kafka a plus)" -> "Kafka a plus" is preferred, Java stays required.
    // Anything else stays attached: in "Docker (preferred)" the word still applies to Docker.
    private String splitParentheses(String text) {
        Matcher m = PARENTHESES.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String inner = m.group(1);
            boolean separate = !dictionary.findIn(inner).isEmpty()
                    && wording(inner.toLowerCase(Locale.ROOT)) != null;
            m.appendReplacement(sb, Matcher.quoteReplacement(separate ? " ; " + inner + " ; " : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean looksLikeHeading(String piece) {
        return piece.endsWith(":") || piece.split("\\s+").length <= MAX_HEADING_WORDS;
    }

    private static Importance headingSection(String lower, Importance current) {
        if (PREFERRED_HEADING.matcher(lower).find()) {
            return Importance.PREFERRED;
        }
        if (REQUIRED_HEADING.matcher(lower).find()) {
            return Importance.REQUIRED;
        }
        if (NEUTRAL_HEADING.matcher(lower).find()) {
            return null;
        }
        return current; // a short line that isn't a heading, e.g. a job title
    }

    /** Importance from the piece's own wording, or null if it doesn't say. */
    private static Importance wording(String lower) {
        boolean required = REQUIRED_WORDING.matcher(NEGATED_REQUIRED.matcher(lower).replaceAll(" ")).find();
        if (required) {
            return Importance.REQUIRED;
        }
        return PREFERRED_WORDING.matcher(lower).find() ? Importance.PREFERRED : null;
    }
}
