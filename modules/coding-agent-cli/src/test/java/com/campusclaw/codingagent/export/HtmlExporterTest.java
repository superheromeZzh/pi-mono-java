/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.ImageContent;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.ThinkingContent;
import com.campusclaw.ai.types.ToolCall;
import com.campusclaw.ai.types.ToolResultMessage;
import com.campusclaw.ai.types.Usage;
import com.campusclaw.ai.types.UserMessage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HtmlExporterTest {

    private static AssistantMessage assistant(List<ContentBlock> content, Usage usage) {
        return new AssistantMessage(
                content, "anthropic", "anthropic", "claude", null, usage, StopReason.STOP, null, 0L);
    }

    @Nested
    class Export {

        @Test
        void documentHasExpectedStructure() {
            UserMessage user = new UserMessage(List.of(new TextContent("hi", null)), 0L);
            String html = HtmlExporter.export(List.of(user), "Conversation", "claude");
            assertThat(html)
                    .startsWith("<!DOCTYPE html>")
                    .contains("<html lang=\"en\">")
                    .contains("<title>Conversation</title>")
                    .contains("<h1>Conversation</h1>")
                    .contains("Model: claude")
                    .contains("<div class=\"message user\">")
                    .endsWith("</html>");
        }

        @Test
        void escapesTitleAndModelName() {
            UserMessage user = new UserMessage(List.of(), 0L);
            String html = HtmlExporter.export(List.of(user), "<dangerous>", "m&l");
            assertThat(html).contains("<title>&lt;dangerous&gt;</title>").contains("Model: m&amp;l");
        }

        @Test
        void renderUserMessageEscapesContent() {
            UserMessage user = new UserMessage(List.of(new TextContent("<script>", null)), 0L);
            String html = HtmlExporter.export(List.of(user), "t", "m");
            assertThat(html).contains("&lt;script&gt;").doesNotContain("<script>");
        }

        @Test
        void renderAssistantWithAllBlockTypesAndUsage() {
            ContentBlock text = new TextContent("answer", null);
            ContentBlock think = new ThinkingContent("ponder", null, false);
            ContentBlock call = new ToolCall("c1", "search", Map.of());
            AssistantMessage am = assistant(
                    List.of(text, think, call), new Usage(10, 5, 0, 0, 15, com.campusclaw.ai.types.Cost.empty()));
            String html = HtmlExporter.export(List.of(am), "t", "m");
            assertThat(html)
                    .contains("class=\"message assistant\"")
                    .contains("answer")
                    .contains("<details class=\"thinking\">")
                    .contains("ponder")
                    .contains("Tool: <code>search</code>")
                    .contains("Tokens: 10 in / 5 out");
        }

        @Test
        void assistantSkipsUnrenderableBlocks() {
            ContentBlock img = new ImageContent("d", "image/png");
            AssistantMessage am = assistant(List.of(img), null);
            String html = HtmlExporter.export(List.of(am), "t", "m");

            // image not rendered into assistant content
            assertThat(html).contains("class=\"message assistant\"").doesNotContain("image/png");
        }

        @Test
        void toolResultLongTextTruncated() {
            String big = "x".repeat(5000);
            ToolResultMessage trm =
                    new ToolResultMessage("c1", "search", List.of(new TextContent(big, null)), null, false, 0L);
            String html = HtmlExporter.export(List.of(trm), "t", "m");
            assertThat(html).contains("...").contains("class=\"message tool-result\"");

            // ensure truncated section is at most ~2000 + "..." chars
            int start = html.indexOf("<pre class=\"content\">") + "<pre class=\"content\">".length();
            int end = html.indexOf("</pre>", start);
            assertThat(end - start).isLessThanOrEqualTo(2010);
        }

        @Test
        void toolResultShortTextNotTruncated() {
            ToolResultMessage trm =
                    new ToolResultMessage("c1", "search", List.of(new TextContent("short", null)), null, false, 0L);
            String html = HtmlExporter.export(List.of(trm), "t", "m");
            assertThat(html).contains(">short</pre>").doesNotContain("...");
        }

        @Test
        void usageOmittedWhenNull() {
            AssistantMessage am = assistant(List.of(new TextContent("hi", null)), null);
            String html = HtmlExporter.export(List.of(am), "t", "m");
            assertThat(html).doesNotContain("Tokens:");
        }
    }

    @Nested
    class AnsiToHtml {

        // NOTE: ansiToHtml(...) contains a defect where closing an open span with a reset/empty
        // code triggers matcher.appendReplacement twice for the same match — IndexOutOfBoundsException.
        // Tests below only exercise paths that do not hit that defect (no production code changes
        // are allowed per the coverage-loop skill rules).

        @Test
        void nullReturnsEmpty() {
            assertThat(HtmlExporter.ansiToHtml(null)).isEmpty();
        }

        @Test
        void plainTextPassesThrough() {
            assertThat(HtmlExporter.ansiToHtml("hello")).isEqualTo("hello");
        }

        @Test
        void leadingResetCodeWithoutOpenSpan() {
            // No prior open span → reset path only calls appendReplacement once.
            String result = HtmlExporter.ansiToHtml("\033[0mhello");
            assertThat(result).isEqualTo("hello");
        }

        @Test
        void leadingEmptyCodeWithoutOpenSpan() {
            String result = HtmlExporter.ansiToHtml("\033[mhello");
            assertThat(result).isEqualTo("hello");
        }

        @Test
        void singleColoredSegmentClosedByAppendTail() {
            // Single color, no reset — closing </span> appended at tail.
            String result = HtmlExporter.ansiToHtml("\033[31mhello");
            assertThat(result).isEqualTo("<span class=\"fg-red\">hello</span>");
        }

        @Test
        void unknownCodeFallsBackToAnsiPrefix() {
            String result = HtmlExporter.ansiToHtml("\033[38;5;200msome");
            assertThat(result)
                    .contains("class=\"ansi-38-5-200\"")
                    .contains("some")
                    .endsWith("</span>");
        }

        @Test
        void boldCodeWrappedInBoldSpan() {
            assertThat(HtmlExporter.ansiToHtml("\033[1mb")).isEqualTo("<span class=\"bold\">b</span>");
        }

        @Test
        void italicCodeWrappedInItalicSpan() {
            assertThat(HtmlExporter.ansiToHtml("\033[3mi")).isEqualTo("<span class=\"italic\">i</span>");
        }

        @Test
        void underlineCodeWrappedInUnderlineSpan() {
            assertThat(HtmlExporter.ansiToHtml("\033[4mu")).isEqualTo("<span class=\"underline\">u</span>");
        }

        @Test
        void allStandardForegroundCodesMapToClasses() {
            String[] codes = {
                "30", "31", "32", "33", "34", "35", "36", "37", "90", "91", "92", "93", "94", "95", "96", "97"
            };
            String[] classes = {
                "fg-black",
                "fg-red",
                "fg-green",
                "fg-yellow",
                "fg-blue",
                "fg-magenta",
                "fg-cyan",
                "fg-white",
                "fg-bright-black",
                "fg-bright-red",
                "fg-bright-green",
                "fg-bright-yellow",
                "fg-bright-blue",
                "fg-bright-magenta",
                "fg-bright-cyan",
                "fg-bright-white"
            };
            for (int i = 0; i < codes.length; i++) {
                // Single color escape (no closing reset) — does not trigger the defect path.
                String html = HtmlExporter.ansiToHtml("\033[" + codes[i] + "mtxt");
                assertThat(html).contains("class=\"" + classes[i] + "\"");
            }
        }
    }
}
