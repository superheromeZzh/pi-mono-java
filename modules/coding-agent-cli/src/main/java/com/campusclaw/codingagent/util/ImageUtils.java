/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Image processing utilities: resize, EXIF handling, format conversion, and base64 encoding.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public final class ImageUtils {
    private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);

    /**
     * Maximum dimension for images sent to LLM (preserves aspect ratio).
     */
    public static final int MAX_LLM_DIMENSION = 2048;

    /**
     * Maximum file size for images (5 MB).
     */
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private ImageUtils() {}

    /**
     * Resize image to fit within maxDim x maxDim while preserving aspect ratio.
     *
     * @param source the source
     * @param maxDim the maxDim
     * @return the result
     */
    public static BufferedImage resize(BufferedImage source, int maxDim) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= maxDim && h <= maxDim) {
            return source;
        }

        double scale = Math.min((double) maxDim / w, (double) maxDim / h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);

        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, newW, newH, null);
        g.dispose();
        return resized;
    }

    /**
     * Convert image to PNG bytes.
     *
     * @param image the image
     * @return the result
     *
     * @throws IOException if the operation fails
     */
    public static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    /**
     * Convert image to JPEG bytes with default quality.
     *
     * @param image the image
     * @return the result
     *
     * @throws IOException if the operation fails
     */
    public static byte[] toJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    /**
     * Read image from file path.
     *
     * @param path the path
     * @return the result
     */
    public static Optional<BufferedImage> readImage(Path path) {
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            return Optional.ofNullable(img);
        } catch (IOException e) {
            log.debug("Failed to read image: {}", path, e);
            return Optional.empty();
        }
    }

    /**
     * Encode image bytes as base64 data URI.
     *
     * @param imageBytes the imageBytes
     * @param mimeType the mimeType
     * @return the result
     */
    public static String toBase64DataUri(byte[] imageBytes, String mimeType) {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * Encode file as base64 string.
     *
     * @param path the path
     * @return the result
     */
    public static Optional<String> fileToBase64(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return Optional.of(Base64.getEncoder().encodeToString(bytes));
        } catch (IOException e) {
            log.debug("Failed to read file for base64: {}", path, e);
            return Optional.empty();
        }
    }

    /**
     * Get image dimensions without fully loading the image.
     *
     * @param path the path
     * @return the result
     */
    public static Optional<int[]> getImageDimensions(Path path) {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return Optional.of(new int[] {reader.getWidth(0), reader.getHeight(0)});
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.debug("Failed to get image dimensions: {}", path, e);
            return Optional.empty();
        }
    }

    /**
     * Prepare an image for LLM: resize if needed, convert to PNG, encode as base64.
     *
     * @param path the path
     * @return the result
     */
    public static Optional<String> prepareForLlm(Path path) {
        return readImage(path).map(img -> {
            try {
                BufferedImage resized = resize(img, MAX_LLM_DIMENSION);
                byte[] pngBytes = toPng(resized);
                return Base64.getEncoder().encodeToString(pngBytes);
            } catch (IOException e) {
                log.debug("Failed to prepare image for LLM: {}", path, e);
                return null;
            }
        });
    }

    /**
     * Check if file is likely an image based on extension.
     *
     * @param path the path
     * @return the result
     */
    private static final List<String> IMAGE_FILE_EXTENSIONS =
            List.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg", ".ico", ".tiff");

    public static boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return IMAGE_FILE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
