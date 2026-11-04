package com.harshith.resumematcher.analysis.engine;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A job skill the resume has. For a group, {@code subSkills} shows which items were found.
 *
 * @param impliedBy set when the resume doesn't name this skill but names one that implies it,
 *                  e.g. JPA "implied by" Spring Data JPA; absent for direct matches
 */
public record MatchedSkill(
        String name,
        String category,
        Importance importance,
        @JsonInclude(JsonInclude.Include.NON_NULL) String impliedBy,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<SubSkill> subSkills) {

    public MatchedSkill {
        subSkills = subSkills == null ? List.of() : List.copyOf(subSkills);
    }
}
