package com.harshith.resumematcher.analysis.engine;

import java.util.List;

/**
 * A skill the job asks for. Two kinds of groups count as one skill:
 * <ul>
 *   <li>a parent with items, "AWS (EC2, S3, RDS)": {@code skill} is AWS, {@code subSkills} the items;</li>
 *   <li>either-or alternatives, "Power BI or Tableau": {@code alternatives} lists every option and
 *       {@code skill} is the first one.</li>
 * </ul>
 * Either way the job is satisfied by any one of them.
 */
public record JobSkill(Skill skill, Importance importance, List<Skill> subSkills, List<Skill> alternatives) {

    public JobSkill {
        subSkills = subSkills == null ? List.of() : List.copyOf(subSkills);
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    public JobSkill(Skill skill, Importance importance, List<Skill> subSkills) {
        this(skill, importance, subSkills, List.of());
    }

    public boolean isGroup() {
        return !subSkills.isEmpty();
    }

    public boolean isEitherOr() {
        return !alternatives.isEmpty();
    }

    /** "Java", or "Power BI or Tableau", or "AWS, Azure or GCP". */
    public String displayName() {
        return isEitherOr() ? Text.joinWithOr(alternatives.stream().map(Skill::name).toList()) : skill.name();
    }
}
