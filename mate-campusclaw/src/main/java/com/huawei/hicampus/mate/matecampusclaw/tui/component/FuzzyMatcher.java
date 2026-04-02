package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fuzzy string matching for autocomplete and search functionality.
 * <p>
 * Implements a simple fuzzy matching algorithm: each character in the query must appear
 * (in order) in the candidate string. Matching is case-insensitive. Results are scored
 * by match quality — consecutive matches and matches at word boundaries score higher.
 */
public class FuzzyMatcher {

    /**
     * A single fuzzy match result with scoring information.
     */
    public record MatchResult<T>(T item, String text, int score, List<Integer> matchPositions) {
    }

    /**
     * Tests whether the query fuzzy-matches the candidate string.
     *
     * @param query     the search query
     * @param candidate the candidate string to test
     * @return true if all query characters appear in order in the candidate
     */
    public static boolean matches(String query, String candidate) {
        if (query == null || query.isEmpty()) return true;
        if (candidate == null || candidate.isEmpty()) return false;

        String lowerQuery = query.toLowerCase();
        String lowerCandidate = candidate.toLowerCase();

        int qi = 0;
        for (int ci = 0; ci < lowerCandidate.length() && qi < lowerQuery.length(); ci++) {
            if (lowerCandidate.charAt(ci) == lowerQuery.charAt(qi)) {
                qi++;
            }
        }
        return qi == lowerQuery.length();
    }

    /**
     * Scores a fuzzy match. Higher scores indicate better matches.
     * Returns -1 if the query does not match the candidate.
     *
     * <p>Scoring factors:
     * <ul>
     *   <li>+10 for each matched character</li>
     *   <li>+5 bonus for consecutive matched characters</li>
     *   <li>+10 bonus for matching at a word boundary (start of string, after '/', '.', '-', '_', ' ')</li>
     *   <li>+15 bonus for exact prefix match</li>
     *   <li>-1 penalty for each gap between matched characters</li>
     * </ul>
     *
     * @param query     the search query
     * @param candidate the candidate string
     * @return the match score, or -1 if no match
     */
    public static int score(String query, String candidate) {
        if (query == null || query.isEmpty()) return 0;
        if (candidate == null || candidate.isEmpty()) return -1;

        String lowerQuery = query.toLowerCase();
        String lowerCandidate = candidate.toLowerCase();

        int score = 0;
        int qi = 0;
        int lastMatchIndex = -1;

        // Prefix bonus
        if (lowerCandidate.startsWith(lowerQuery)) {
            score += 15;
        }

        for (int ci = 0; ci < lowerCandidate.length() && qi < lowerQuery.length(); ci++) {
            if (lowerCandidate.charAt(ci) == lowerQuery.charAt(qi)) {
                score += 10; // base match score

                // Consecutive match bonus
                if (lastMatchIndex >= 0 && ci == lastMatchIndex + 1) {
                    score += 5;
                }

                // Word boundary bonus
                if (ci == 0 || isWordBoundary(lowerCandidate.charAt(ci - 1))) {
                    score += 10;
                }

                // Gap penalty
                if (lastMatchIndex >= 0) {
                    int gap = ci - lastMatchIndex - 1;
                    score -= gap;
                }

                lastMatchIndex = ci;
                qi++;
            }
        }

        return qi == lowerQuery.length() ? score : -1;
    }

    /**
     * Finds match positions for highlighting. Returns the indices in the candidate
     * string where each query character matched, or null if no match.
     *
     * @param query     the search query
     * @param candidate the candidate string
     * @return list of match positions, or null if no match
     */
    public static List<Integer> matchPositions(String query, String candidate) {
        if (query == null || query.isEmpty()) return List.of();
        if (candidate == null || candidate.isEmpty()) return null;

        String lowerQuery = query.toLowerCase();
        String lowerCandidate = candidate.toLowerCase();

        List<Integer> positions = new ArrayList<>();
        int qi = 0;
        for (int ci = 0; ci < lowerCandidate.length() && qi < lowerQuery.length(); ci++) {
            if (lowerCandidate.charAt(ci) == lowerQuery.charAt(qi)) {
                positions.add(ci);
                qi++;
            }
        }

        return qi == lowerQuery.length() ? positions : null;
    }

    /**
     * Filters and ranks a list of items by fuzzy matching against their string representation.
     * Results are sorted by score (highest first).
     *
     * @param query      the search query
     * @param candidates list of candidate strings
     * @return sorted list of match results (best matches first)
     */
    public static List<MatchResult<String>> filter(String query, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (query == null || query.isEmpty()) {
            List<MatchResult<String>> results = new ArrayList<>();
            for (String c : candidates) {
                results.add(new MatchResult<>(c, c, 0, List.of()));
            }
            return results;
        }

        List<MatchResult<String>> results = new ArrayList<>();
        for (String candidate : candidates) {
            int matchScore = score(query, candidate);
            if (matchScore >= 0) {
                List<Integer> positions = matchPositions(query, candidate);
                results.add(new MatchResult<>(candidate, candidate, matchScore, positions != null ? positions : List.of()));
            }
        }

        results.sort(Comparator.comparingInt(MatchResult<String>::score).reversed());
        return results;
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static boolean isWordBoundary(char c) {
        return c == '/' || c == '.' || c == '-' || c == '_' || c == ' ' || c == '\\';
    }
}
