package com.harshith.resumematcher.analysis.engine;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A job skill the resume doesn't have. For a group, none of its items were found either.
 * For an either-or group ({@code eitherOr}), {@code name} is "Power BI or Tableau" and
 * {@code subSkills} are the options.
 */
public record MissingSkill(
        String name,
        String category,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Skill> subSkills,
        // Boxed so results saved before this field existed (no "eitherOr" in the JSON) still load.
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) Boolean eitherOr) {

    public MissingSkill(String name, String category, List<Skill> subSkills) {
        this(name, category, subSkills, false);
    }

    public MissingSkill {
        subSkills = subSkills == null ? List.of() : List.copyOf(subSkills);
        eitherOr = Boolean.TRUE.equals(eitherOr);
    }

    /** "AWS (Amazon EC2, Amazon S3 or Amazon RDS)" for a group, just the name otherwise. */
    public String label() {
        if (subSkills.isEmpty() || eitherOr) {
            return name; // an either-or name already lists its options
        }
        List<String> subs = subSkills.stream().map(Skill::name).toList();
        String joined = subs.size() == 1 ? subs.getFirst()
                : subs.subList(0, subs.size() - 1).stream().collect(Collectors.joining(", "))
                + " or " + subs.getLast();
        return name + " (" + joined + ")";
    }
}
