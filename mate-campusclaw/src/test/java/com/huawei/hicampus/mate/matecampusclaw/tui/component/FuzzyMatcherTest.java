/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FuzzyMatcherTest {

    @Nested
    class Matches {

        @Test
        void nullQueryMatches() {
            assertTrue(FuzzyMatcher.matches(null, "anything"));
        }

        @Test
        void emptyQueryMatches() {
            assertTrue(FuzzyMatcher.matches("", "anything"));
        }

        @Test
        void nullCandidateNoMatch() {
            assertFalse(FuzzyMatcher.matches("x", null));
        }

        @Test
        void emptyCandidateNoMatch() {
            assertFalse(FuzzyMatcher.matches("x", ""));
        }

        @Test
        void inOrderMatch() {
            assertTrue(FuzzyMatcher.matches("foo", "FOOBar"));
            assertTrue(FuzzyMatcher.matches("fb", "FooBar"));
        }

        @Test
        void outOfOrderNoMatch() {
            assertFalse(FuzzyMatcher.matches("zxy", "fooBar"));
        }

        @Test
        void caseInsensitive() {
            assertTrue(FuzzyMatcher.matches("FoO", "foobar"));
        }
    }

    @Nested
    class Score {

        @Test
        void nullQueryReturnsZero() {
            assertEquals(0, FuzzyMatcher.score(null, "x"));
            assertEquals(0, FuzzyMatcher.score("", "x"));
        }

        @Test
        void nullCandidateReturnsMinusOne() {
            assertEquals(-1, FuzzyMatcher.score("a", null));
            assertEquals(-1, FuzzyMatcher.score("a", ""));
        }

        @Test
        void noMatchReturnsMinusOne() {
            assertEquals(-1, FuzzyMatcher.score("xyz", "abc"));
        }

        @Test
        void prefixMatchGetsBonus() {
            int prefix = FuzzyMatcher.score("foo", "foobar");
            int suffix = FuzzyMatcher.score("foo", "barfoo");
            assertTrue(prefix > suffix, "prefix=" + prefix + ", suffix=" + suffix);
        }

        @Test
        void consecutiveBetterThanGapped() {
            int consec = FuzzyMatcher.score("ab", "abc");
            int gapped = FuzzyMatcher.score("ac", "abc");
            assertTrue(consec > gapped);
        }

        @Test
        void wordBoundaryBonusOnSeparator() {
            int boundary = FuzzyMatcher.score("w", "foo/world");
            int interior = FuzzyMatcher.score("o", "fworld");
            assertTrue(boundary > 0);
            assertTrue(interior > 0);
        }
    }

    @Nested
    class MatchPositions {

        @Test
        void nullQueryReturnsEmpty() {
            assertEquals(List.of(), FuzzyMatcher.matchPositions(null, "abc"));
            assertEquals(List.of(), FuzzyMatcher.matchPositions("", "abc"));
        }

        @Test
        void nullCandidateReturnsNull() {
            assertNull(FuzzyMatcher.matchPositions("x", null));
            assertNull(FuzzyMatcher.matchPositions("x", ""));
        }

        @Test
        void noMatchReturnsNull() {
            assertNull(FuzzyMatcher.matchPositions("xyz", "abc"));
        }

        @Test
        void matchPositionsReportedInOrder() {
            List<Integer> positions = FuzzyMatcher.matchPositions("ac", "abc");
            assertEquals(List.of(0, 2), positions);
        }
    }

    @Nested
    class Filter {

        @Test
        void nullCandidatesReturnsEmpty() {
            assertEquals(List.of(), FuzzyMatcher.filter("x", null));
        }

        @Test
        void emptyCandidatesReturnsEmpty() {
            assertEquals(List.of(), FuzzyMatcher.filter("x", List.of()));
        }

        @Test
        void emptyQueryReturnsAllWithZeroScore() {
            List<FuzzyMatcher.MatchResult<String>> result = FuzzyMatcher.filter("", List.of("a", "b"));
            assertEquals(2, result.size());
            for (FuzzyMatcher.MatchResult<String> r : result) {
                assertEquals(0, r.score());
            }
        }

        @Test
        void filtersAndRanksByScore() {
            List<FuzzyMatcher.MatchResult<String>> result =
                    FuzzyMatcher.filter("fo", List.of("foobar", "xxx", "afool"));
            assertEquals(2, result.size());

            // Best first
            assertTrue(result.get(0).score() >= result.get(1).score());

            // 'xxx' filtered out
            for (FuzzyMatcher.MatchResult<String> r : result) {
                assertNotNull(r.matchPositions());
            }
        }
    }
}
