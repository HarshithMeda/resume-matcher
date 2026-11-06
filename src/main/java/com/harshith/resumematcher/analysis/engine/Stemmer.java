package com.harshith.resumematcher.analysis.engine;

/**
 * A small, predictable stemmer: a handful of suffix rules applied in a fixed order, so different
 * forms of a word share one key. The key doesn't have to be a real word, only consistent:
 * <pre>
 *   process, processes, processing, processed  -> process
 *   document, documented, documenting, documentation -> document
 *   schedule, scheduled, scheduling            -> schedul
 *   technology, technologies                   -> technology
 *   plan, planned, planning                    -> plan
 *   optimize, optimization, optimizing         -> optimiz
 * </pre>
 * Rules, in order (each step applies at most one rule; stems keep at least 3 letters):
 * <ol>
 *   <li>plurals: -ies → -y, -sses → -ss, -es after s/x/z/ch/sh, otherwise -s (not -ss, -us, -is);</li>
 *   <li>nouns from verbs: -ization/-isation → -ize, -ation → (nothing);</li>
 *   <li>verb endings: -ied → -y, -ing, -ed; then a doubled final consonant is undoubled
 *       (planning → plan), except l, s, z and f (billing → bill);</li>
 *   <li>a final -e is dropped (schedule → schedul, so it meets scheduling).</li>
 * </ol>
 * Words shorter than 4 letters are left alone. Deliberately no -ment, -er or -al rules: they merge
 * unrelated words (segment/seg, retailer/retail, technical/technic).
 */
public final class Stemmer {

    private static final int MIN_STEM = 3;

    private Stemmer() {
    }

    public static String stem(String word) {
        String w = word.toLowerCase(java.util.Locale.ROOT);
        if (w.length() < 4) {
            return w;
        }
        w = plural(w);
        w = derivational(w);
        w = verbEnding(w);
        if (w.length() > MIN_STEM && w.endsWith("e")) {
            w = w.substring(0, w.length() - 1);
        }
        return w;
    }

    private static String plural(String w) {
        if (w.endsWith("ies") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("sses")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("es") && (w.endsWith("ses") || w.endsWith("xes") || w.endsWith("zes")
                || w.endsWith("ches") || w.endsWith("shes")) && w.length() - 2 >= MIN_STEM) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is")
                && w.length() - 1 >= MIN_STEM) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    private static String derivational(String w) {
        for (String suffix : new String[] {"ization", "isation"}) {
            if (w.endsWith(suffix) && w.length() - suffix.length() >= MIN_STEM) {
                return w.substring(0, w.length() - suffix.length()) + "ize";
            }
        }
        if (w.endsWith("ation") && w.length() - 5 >= 4) {
            return w.substring(0, w.length() - 5);
        }
        return w;
    }

    private static String verbEnding(String w) {
        if (w.endsWith("ied") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        String stem = null;
        if (w.endsWith("ing") && w.length() - 3 >= MIN_STEM) {
            stem = w.substring(0, w.length() - 3);
        } else if (w.endsWith("ed") && w.length() - 2 >= MIN_STEM) {
            stem = w.substring(0, w.length() - 2);
        }
        if (stem == null || !hasVowel(stem)) {
            return w; // "thing", "need" and the like stay as they are
        }
        int n = stem.length();
        char last = stem.charAt(n - 1);
        if (n >= 4 && last == stem.charAt(n - 2) && "lszf".indexOf(last) < 0 && !isVowel(last)) {
            return stem.substring(0, n - 1); // planning -> plann -> plan
        }
        return stem;
    }

    private static boolean hasVowel(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (isVowel(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVowel(char c) {
        return "aeiouy".indexOf(c) >= 0;
    }
}
