/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link Autocomplete}, the file-path completion wrapper around
 * {@link Input}. Exercises the static {@code computeFileSuggestions} helper
 * directly against a real filesystem (via {@code @TempDir}) and the
 * keyboard-handling state machine (Tab/Shift+Tab/Enter/Escape).
 */
class AutocompleteTest {

    @Nested
    class ValueAndState {

        @Test
        void initialValueEmpty() {
            Autocomplete ac = new Autocomplete();
            assertEquals("", ac.getValue());
            assertFalse(ac.isShowingSuggestions());
        }

        @Test
        void setValueDismissesSuggestions() {
            Autocomplete ac = new Autocomplete();
            ac.setValue("foo");
            assertEquals("foo", ac.getValue());
            assertFalse(ac.isShowingSuggestions());
        }

        @Test
        void exposesUnderlyingInput() {
            Autocomplete ac = new Autocomplete("hint");
            assertNotNull(ac.getInput());
            assertEquals("hint", ac.getInput().getPlaceholder());
        }

        @Test
        void focusDelegatesToInput() {
            Autocomplete ac = new Autocomplete();
            ac.setFocused(true);
            assertTrue(ac.isFocused());
            ac.setFocused(false);
            assertFalse(ac.isFocused());
        }
    }

    @Nested
    class ComputeFileSuggestions {

        @Test
        void nonexistentDirReturnsEmpty() {
            List<String> suggestions = Autocomplete.computeFileSuggestions("/does/not/exist/x");
            assertTrue(suggestions.isEmpty());
        }

        @Test
        void existingDirListsEntries(@TempDir Path tmp) throws IOException {
            Files.createDirectory(tmp.resolve("alpha"));
            Files.writeString(tmp.resolve("beta.txt"), "");
            Files.writeString(tmp.resolve("gamma.md"), "");
            List<String> suggestions = Autocomplete.computeFileSuggestions(tmp.toString() + "/");
            assertEquals(3, suggestions.size());
        }

        @Test
        void prefixMatchFiltersEntries(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("alpha.txt"), "");
            Files.writeString(tmp.resolve("alpine.txt"), "");
            Files.writeString(tmp.resolve("beta.txt"), "");
            List<String> suggestions =
                    Autocomplete.computeFileSuggestions(tmp.resolve("alp").toString());
            assertEquals(2, suggestions.size());
            assertTrue(suggestions.stream().allMatch(s -> s.contains("alp")));
        }

        @Test
        void emptyInputUsesCurrentDir() {
            // Falls back to listing the current working directory; must return a list (possibly empty)
            // without throwing — Autocomplete uses this branch when the user has typed nothing yet.
            List<String> suggestions = assertDoesNotThrow(() -> Autocomplete.computeFileSuggestions(""));
            assertTrue(suggestions.stream().allMatch(s -> s != null && !s.isBlank()));
        }
    }

    @Nested
    class KeyboardHandling {

        @Test
        void tabTriggersCompletionAndShowsSuggestions(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("alpha.txt"), "");
            Files.writeString(tmp.resolve("alpine.txt"), "");
            Autocomplete ac = new Autocomplete();
            ac.setValue(tmp.resolve("alp").toString());
            ac.handleInput("\t");
            assertTrue(ac.isShowingSuggestions());
            assertEquals(2, ac.getSuggestions().size());
        }

        @Test
        void tabCyclesSuggestions(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("a1.txt"), "");
            Files.writeString(tmp.resolve("a2.txt"), "");
            Files.writeString(tmp.resolve("a3.txt"), "");
            Autocomplete ac = new Autocomplete();
            ac.setValue(tmp.resolve("a").toString());
            ac.handleInput("\t");
            String first = ac.getValue();
            ac.handleInput("\t");
            String second = ac.getValue();
            assertFalse(first.equals(second));
        }

        @Test
        void shiftTabCyclesBackward(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("a1.txt"), "");
            Files.writeString(tmp.resolve("a2.txt"), "");
            Autocomplete ac = new Autocomplete();
            ac.setValue(tmp.resolve("a").toString());
            ac.handleInput("\t");
            String afterTab = ac.getValue();
            ac.handleInput("\033[Z");
            assertFalse(afterTab.equals(ac.getValue()));
        }

        @Test
        void singleMatchAutoAccepted(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("alpha.txt"), "");
            Autocomplete ac = new Autocomplete();
            ac.setValue(tmp.resolve("alp").toString());
            ac.handleInput("\t");

            // Single match → auto-accepted, suggestions hidden
            assertFalse(ac.isShowingSuggestions());
            assertTrue(ac.getValue().endsWith("alpha.txt"));
        }

        @Test
        void enterSubmitsWhenNoSuggestions() {
            Autocomplete ac = new Autocomplete();
            ac.setValue("hello");
            AtomicReference<String> submitted = new AtomicReference<>();
            ac.setOnSubmit(submitted::set);
            ac.handleInput("\r");
            assertEquals("hello", submitted.get());
        }

        @Test
        void enterAcceptsSuggestionWhenShown(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("a1.txt"), "");
            Files.writeString(tmp.resolve("a2.txt"), "");
            Autocomplete ac = new Autocomplete();
            ac.setValue(tmp.resolve("a").toString());
            AtomicInteger submitCount = new AtomicInteger();
            ac.setOnSubmit(s -> submitCount.incrementAndGet());
            ac.handleInput("\t");
            assertTrue(ac.isShowingSuggestions());
            ac.handleInput("\r");

            // Suggestion accepted, NOT submitted
            assertEquals(0, submitCount.get());
            assertFalse(ac.isShowingSuggestions());
        }

        @Test
        void escapeWithSuggestionsDismissesThem(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("a1.txt"), "");
            Files.writeString(tmp.resolve("a2.txt"), "");
            Autocomplete ac = new Autocomplete();
            ac.setValue(tmp.resolve("a").toString());
            AtomicInteger escapeCount = new AtomicInteger();
            ac.setOnEscape(escapeCount::incrementAndGet);
            ac.handleInput("\t");
            ac.handleInput("\033");
            assertFalse(ac.isShowingSuggestions());

            // First escape only dismissed suggestions
            assertEquals(0, escapeCount.get());
        }

        @Test
        void escapeWithoutSuggestionsCallsCallback() {
            Autocomplete ac = new Autocomplete();
            AtomicInteger escapeCount = new AtomicInteger();
            ac.setOnEscape(escapeCount::incrementAndGet);
            ac.handleInput("\033");
            assertEquals(1, escapeCount.get());
        }

        @Test
        void typingDismissesSuggestionsAndForwardsToInput(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("a1.txt"), "");
            Files.writeString(tmp.resolve("a2.txt"), "");
            Autocomplete ac = new Autocomplete();
            ac.setValue(tmp.resolve("a").toString());
            ac.handleInput("\t");
            assertTrue(ac.isShowingSuggestions());
            ac.handleInput("x");
            assertFalse(ac.isShowingSuggestions());
        }
    }

    @Nested
    class Rendering {

        @Test
        void rendersInputLineWhenNoSuggestions() {
            Autocomplete ac = new Autocomplete();
            ac.setValue("hello");
            List<String> lines = ac.render(40);
            assertFalse(lines.isEmpty());
        }

        @Test
        void rendersSuggestionsBelowInput(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("a1.txt"), "");
            Files.writeString(tmp.resolve("a2.txt"), "");
            Autocomplete ac = new Autocomplete();
            ac.setValue(tmp.resolve("a").toString());
            ac.handleInput("\t");
            List<String> lines = ac.render(80);

            // At least input line + 2 suggestions
            assertTrue(lines.size() >= 3);
        }

        @Test
        void cacheReusedForSameInputs() {
            Autocomplete ac = new Autocomplete();
            ac.setValue("hello");
            List<String> first = ac.render(40);
            List<String> second = ac.render(40);
            assertEquals(first, second);
        }
    }

    @Nested
    class GetSuggestionsImmutable {

        @Test
        void exposesUnmodifiableList() {
            Autocomplete ac = new Autocomplete();
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> ac.getSuggestions()
                    .add("x"));
        }
    }
}
