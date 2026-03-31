package com.campusclaw.codingagent.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * MIME type detection based on file extension and content probing.
 */
public final class MimeDetector {
    private MimeDetector() {}

    private static final Map<String, String> EXTENSION_MAP = Map.ofEntries(
        // Text
        Map.entry("txt", "text/plain"),
        Map.entry("md", "text/markdown"),
        Map.entry("html", "text/html"),
        Map.entry("htm", "text/html"),
        Map.entry("css", "text/css"),
        Map.entry("csv", "text/csv"),
        Map.entry("xml", "text/xml"),
        Map.entry("yaml", "text/yaml"),
        Map.entry("yml", "text/yaml"),
        Map.entry("toml", "text/x-toml"),
        // Code
        Map.entry("js", "text/javascript"),
        Map.entry("mjs", "text/javascript"),
        Map.entry("ts", "text/typescript"),
        Map.entry("tsx", "text/typescript"),
        Map.entry("jsx", "text/javascript"),
        Map.entry("java", "text/x-java"),
        Map.entry("py", "text/x-python"),
        Map.entry("rb", "text/x-ruby"),
        Map.entry("go", "text/x-go"),
        Map.entry("rs", "text/x-rust"),
        Map.entry("c", "text/x-c"),
        Map.entry("cpp", "text/x-c++"),
        Map.entry("h", "text/x-c"),
        Map.entry("hpp", "text/x-c++"),
        Map.entry("cs", "text/x-csharp"),
        Map.entry("swift", "text/x-swift"),
        Map.entry("kt", "text/x-kotlin"),
        Map.entry("scala", "text/x-scala"),
        Map.entry("sh", "text/x-shellscript"),
        Map.entry("bash", "text/x-shellscript"),
        Map.entry("zsh", "text/x-shellscript"),
        Map.entry("sql", "text/x-sql"),
        Map.entry("r", "text/x-r"),
        Map.entry("lua", "text/x-lua"),
        Map.entry("php", "text/x-php"),
        Map.entry("pl", "text/x-perl"),
        Map.entry("ex", "text/x-elixir"),
        Map.entry("erl", "text/x-erlang"),
        Map.entry("hs", "text/x-haskell"),
        Map.entry("clj", "text/x-clojure"),
        Map.entry("dart", "text/x-dart"),
        // Config
        Map.entry("json", "application/json"),
        Map.entry("jsonl", "application/x-jsonlines"),
        Map.entry("properties", "text/x-java-properties"),
        Map.entry("ini", "text/x-ini"),
        Map.entry("cfg", "text/x-ini"),
        Map.entry("conf", "text/x-ini"),
        Map.entry("gradle", "text/x-gradle"),
        // Images
        Map.entry("png", "image/png"),
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("gif", "image/gif"),
        Map.entry("webp", "image/webp"),
        Map.entry("svg", "image/svg+xml"),
        Map.entry("bmp", "image/bmp"),
        Map.entry("ico", "image/x-icon"),
        Map.entry("tiff", "image/tiff"),
        Map.entry("tif", "image/tiff"),
        // Documents
        Map.entry("pdf", "application/pdf"),
        Map.entry("doc", "application/msword"),
        Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("xls", "application/vnd.ms-excel"),
        Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        // Archives
        Map.entry("zip", "application/zip"),
        Map.entry("tar", "application/x-tar"),
        Map.entry("gz", "application/gzip"),
        Map.entry("jar", "application/java-archive"),
        // Binary
        Map.entry("wasm", "application/wasm"),
        Map.entry("class", "application/java-vm"),
        Map.entry("so", "application/x-sharedlib"),
        Map.entry("dylib", "application/x-sharedlib"),
        Map.entry("exe", "application/x-executable"),
        Map.entry("dll", "application/x-dosexec")
    );

    /** Detect MIME type by file extension. */
    public static String fromExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = name.substring(dot + 1).toLowerCase();
        return EXTENSION_MAP.getOrDefault(ext, "application/octet-stream");
    }

    /** Detect MIME type by file content probing (uses java.nio). */
    public static Optional<String> probe(Path path) {
        try {
            String type = Files.probeContentType(path);
            return Optional.ofNullable(type);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Best-effort MIME detection: try probe first, fall back to extension. */
    public static String detect(Path path) {
        return probe(path).orElseGet(() -> fromExtension(path));
    }

    /** Check if MIME type represents a text file. */
    public static boolean isText(String mimeType) {
        return mimeType.startsWith("text/") || "application/json".equals(mimeType)
            || "application/x-jsonlines".equals(mimeType) || "application/xml".equals(mimeType);
    }

    /** Check if MIME type represents an image. */
    public static boolean isImage(String mimeType) {
        return mimeType.startsWith("image/");
    }

    /** Check if MIME type represents a binary file. */
    public static boolean isBinary(String mimeType) {
        return !isText(mimeType) && !isImage(mimeType);
    }

    /** Get file extension from path. */
    public static String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }
}
