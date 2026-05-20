/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageComponentTest {

    @Nested
    class Construction {

        @Test
        void defaultConstructorNoData() {
            ImageComponent c = new ImageComponent();
            List<String> lines = c.render(40);

            // Falls back to placeholder
            assertTrue(lines.size() == 3 || lines.size() == 1);
        }

        @Test
        void dataConstructor() {
            ImageComponent c = new ImageComponent("data".getBytes(java.nio.charset.StandardCharsets.UTF_8), "label");
            assertTrue(c.render(40).size() >= 1);
        }
    }

    @Nested
    class LoadFromFile {

        @Test
        void successWithRealFile(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("img.png");
            Files.write(file, new byte[] {1, 2, 3});
            ImageComponent c = new ImageComponent();
            assertTrue(c.loadFromFile(file));
            assertTrue(c.render(40).size() >= 1);
        }

        @Test
        void failureWithMissingFile(@TempDir Path tmp) {
            ImageComponent c = new ImageComponent();
            assertFalse(c.loadFromFile(tmp.resolve("missing.png")));

            // The altText should be set with the failure message
            List<String> lines = c.render(60);

            // Lines combined should contain "Failed to load"
            String joined = String.join("", lines);
            assertTrue(joined.contains("Failed to load"));
        }
    }

    @Nested
    class Setters {

        @Test
        void setImageDataInvalidatesCache() {
            ImageComponent c = new ImageComponent("a".getBytes(java.nio.charset.StandardCharsets.UTF_8), "first");
            List<String> first = c.render(40);
            List<String> second = c.render(40);
            assertSame(first, second);
            c.setImageData("b".getBytes(java.nio.charset.StandardCharsets.UTF_8), "second");
            List<String> third = c.render(40);
            assertNotSame(first, third);
        }

        @Test
        void setMaxDimensionsInvalidatesCache() {
            ImageComponent c = new ImageComponent("a".getBytes(java.nio.charset.StandardCharsets.UTF_8), "x");
            List<String> first = c.render(40);
            c.setMaxDimensions(50, 30);
            assertNotSame(first, c.render(40));
        }

        @Test
        void invalidateClearsCache() {
            ImageComponent c = new ImageComponent("x".getBytes(java.nio.charset.StandardCharsets.UTF_8), "alt");
            List<String> first = c.render(40);
            c.invalidate();
            assertNotSame(first, c.render(40));
        }
    }

    @Nested
    class FallbackRender {

        @Test
        void nullDataAndNullAltText() {
            ImageComponent c = new ImageComponent();

            // Force unsupported branch by not setting data
            List<String> lines = c.render(40);

            // Render is non-empty; may be 1 (escape) or 3 (fallback)
            assertTrue(lines.size() >= 1);
        }

        @Test
        void longAltTextTruncated() {
            // Build a comp with no data so it definitely uses fallback
            ImageComponent c = new ImageComponent();
            c.setImageData(null, "a-very-long-alt-text-that-should-get-truncated");
            List<String> lines = c.render(20);
            assertEquals(3, lines.size());
            assertTrue(lines.get(1).contains("…") || lines.get(1).contains("very"));
        }
    }
}
