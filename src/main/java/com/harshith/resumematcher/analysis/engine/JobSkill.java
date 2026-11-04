package com.harshith.resumematcher.analysis.engine;

import java.util.List;

/**
 * A skill the job asks for. If the job wrote it as a group, like "AWS (EC2, S3, RDS)",
 * {@code subSkills} holds the items in the parentheses and the whole group counts as one skill.
 */
public record JobSkill(Skill skill, Importance importance, List<Skill> subSkills) {

    public JobSkill {
        subSkills = subSkills == null ? List.of() : List.copyOf(subSkills);
    }

    public boolean isGroup() {
        return !subSkills.isEmpty();
    }
}
