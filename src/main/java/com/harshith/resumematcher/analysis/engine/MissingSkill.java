package com.harshith.resumematcher.analysis.engine;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.stream.Collectors;

/** A job skill the resume doesn't have. For a group, none of its items were found either. */
public record MissingSkill(
        String name,
        String category,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Skill> subSkills) {

    public MissingSkill {
        subSkills = subSkills == null ? List.of() : List.copyOf(subSkills);
    }

    /** "AWS (Amazon EC2, Amazon S3 or Amazon RDS)" for a group, just the name otherwise. */
    public String label() {
        if (subSkills.isEmpty()) {
            return name;
        }
        List<String> subs = subSkills.stream().map(Skill::name).toList();
        String joined = subs.size() == 1 ? subs.getFirst()
                : subs.subList(0, subs.size() - 1).stream().collect(Collectors.joining(", "))
                + " or " + subs.getLast();
        return name + " (" + joined + ")";
    }
}
