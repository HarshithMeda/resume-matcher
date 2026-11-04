package com.harshith.resumematcher.analysis.engine;

import java.util.List;

public record ScoreBreakdown(String formula, List<ScoreComponent> components) {
}
