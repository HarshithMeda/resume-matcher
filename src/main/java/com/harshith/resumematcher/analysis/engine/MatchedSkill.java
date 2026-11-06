package com.harshith.resumematcher.analysis.engine;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A job skill the resume has. For a group, {@code subSkills} shows which items were found.
 *
 * @param impliedBy set when the resume doesn't name this skill but names one that implies it,
 *                  e.g. JPA "implied by" Spring Data JPA; absent for direct matches
 * @param eitherOr  true for "Power BI or Tableau": {@code subSkills} are the options, and the
 *                  found ones are what matched
 */
public record MatchedSkill(
        String name,
        String category,
        Importance importance,
        @JsonInclude(JsonInclude.Include.NON_NULL) String impliedBy,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<SubSkill> subSkills,
        // Boxed so results saved before this field existed (no "eitherOr" in the JSON) still load.
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) Boolean eitherOr) {

    public MatchedSkill(String name, String category, Importance importance, String impliedBy, List<SubSkill> subSkills) {
        this(name, category, importance, impliedBy, subSkills, false);
    }

    public MatchedSkill {
        subSkills = subSkills == null ? List.of() : List.copyOf(subSkills);
        eitherOr = Boolean.TRUE.equals(eitherOr);
    }
}
