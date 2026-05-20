/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageUtilsTest {

    private static BufferedImage makeImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        // fill with a single color to give the encoder something
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, 0x123456);
            }
        }
        return img;
    }

    private static Path writePng(Path dir, String name, BufferedImage img) throws IOException {
        Path p = dir.resolve(name);
        ImageIO.write(img, "png", p.toFile());
        return p;
    }

    @Nested
    class Resize {

        @Test
        void smallImageReturnedUnchanged() {
            BufferedImage src = makeImage(100, 50);
            BufferedImage out = ImageUtils.resize(src, 200);
            assertThat(out).isSameAs(src);
        }

        @Test
        void largeImageDownscaledPreservingRatio() {
            BufferedImage src = makeImage(2048, 1024);
            BufferedImage out = ImageUtils.resize(src, 1024);
            assertThat(out.getWidth()).isEqualTo(1024);
            assertThat(out.getHeight()).isEqualTo(512);
        }

        @Test
        void tallImageScaledByHeight() {
            BufferedImage src = makeImage(100, 1000);
            BufferedImage out = ImageUtils.resize(src, 500);
            assertThat(out.getHeight()).isEqualTo(500);
            assertThat(out.getWidth()).isEqualTo(50);
        }
    }

    @Nested
    class Encoding {

        @Test
        void toPngProducesNonEmptyBytes() throws IOException {
            byte[] bytes = ImageUtils.toPng(makeImage(10, 10));
            assertThat(bytes).isNotEmpty();
        }

        @Test
        void toJpegProducesNonEmptyBytes() throws IOException {
            byte[] bytes = ImageUtils.toJpeg(makeImage(10, 10));
            assertThat(bytes).isNotEmpty();
        }

        @Test
        void toBase64DataUriWraps() {
            byte[] payload = "hi".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String uri = ImageUtils.toBase64DataUri(payload, "image/png");
            assertThat(uri).startsWith("data:image/png;base64,").contains("aGk=");
        }
    }

    @Nested
    class FileIo {

        @Test
        void readImageMissingReturnsEmpty(@TempDir Path tmp) {
            assertThat(ImageUtils.readImage(tmp.resolve("nope.png"))).isEmpty();
        }

        @Test
        void readImageValid(@TempDir Path tmp) throws IOException {
            Path p = writePng(tmp, "img.png", makeImage(8, 8));
            assertThat(ImageUtils.readImage(p)).isPresent();
        }

        @Test
        void fileToBase64Missing(@TempDir Path tmp) {
            assertThat(ImageUtils.fileToBase64(tmp.resolve("nope.bin"))).isEmpty();
        }

        @Test
        void fileToBase64Roundtrip(@TempDir Path tmp) throws IOException {
            Path p = tmp.resolve("payload.bin");
            Files.write(p, new byte[] {1, 2, 3});
            Optional<String> b64 = ImageUtils.fileToBase64(p);
            assertThat(b64).contains("AQID");
        }

        @Test
        void getImageDimensionsValid(@TempDir Path tmp) throws IOException {
            Path p = writePng(tmp, "img.png", makeImage(7, 4));
            Optional<int[]> dims = ImageUtils.getImageDimensions(p);
            assertThat(dims).isPresent();
            assertThat(dims.get()).containsExactly(7, 4);
        }

        @Test
        void getImageDimensionsMissing(@TempDir Path tmp) {
            assertThat(ImageUtils.getImageDimensions(tmp.resolve("nope.png"))).isEmpty();
        }

        @Test
        void getImageDimensionsNonImage(@TempDir Path tmp) throws IOException {
            Path p = tmp.resolve("not.txt");
            Files.writeString(p, "hello");
            assertThat(ImageUtils.getImageDimensions(p)).isEmpty();
        }

        @Test
        void prepareForLlmReturnsBase64(@TempDir Path tmp) throws IOException {
            Path p = writePng(tmp, "img.png", makeImage(16, 16));
            assertThat(ImageUtils.prepareForLlm(p)).isPresent();
        }

        @Test
        void prepareForLlmMissingFileEmpty(@TempDir Path tmp) {
            assertThat(ImageUtils.prepareForLlm(tmp.resolve("nope.png"))).isEmpty();
        }
    }

    @Nested
    class ExtensionDetection {

        @Test
        void recognisedImageExtensions() {
            String[] exts = {"png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "tiff"};
            for (String e : exts) {
                assertThat(ImageUtils.isImageFile(Paths.get("a." + e))).as(e).isTrue();
                assertThat(ImageUtils.isImageFile(Paths.get("a." + e.toUpperCase(java.util.Locale.ROOT))))
                        .as("upper-" + e)
                        .isTrue();
            }
        }

        @Test
        void nonImageFalse() {
            assertThat(ImageUtils.isImageFile(Paths.get("a.txt"))).isFalse();
            assertThat(ImageUtils.isImageFile(Paths.get("README"))).isFalse();
        }
    }
}
