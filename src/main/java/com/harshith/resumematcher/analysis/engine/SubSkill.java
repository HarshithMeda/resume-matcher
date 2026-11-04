package com.harshith.resumematcher.analysis.engine;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An item inside a skill group, and whether the resume has it.
 *
 * @param impliedBy set when it was found only through another skill (see {@link MatchedSkill})
 */
public record SubSkill(
        String name,
        String category,
        boolean found,
        @JsonInclude(JsonInclude.Include.NON_NULL) String impliedBy) {
}
