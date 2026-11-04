package com.harshith.resumematcher.analysis.engine;

/** An item inside a skill group, and whether the resume mentions it. */
public record SubSkill(String name, String category, boolean found) {
}
