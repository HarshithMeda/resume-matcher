package com.harshith.resumematcher.analysis.engine;

import java.util.List;

final class Text {

    private Text() {
    }

    /** ["A"] -> "A"; ["A","B"] -> "A and B"; ["A","B","C","D"] with max 3 -> "A, B, C and 1 more". */
    static String joinNames(List<String> names, int max) {
        if (names.isEmpty()) {
            return "";
        }
        List<String> shown = names.subList(0, Math.min(max, names.size()));
        int hidden = names.size() - shown.size();
        if (hidden > 0) {
            return String.join(", ", shown) + " and " + hidden + " more";
        }
        if (shown.size() == 1) {
            return shown.getFirst();
        }
        return String.join(", ", shown.subList(0, shown.size() - 1)) + " and " + shown.getLast();
    }
}
