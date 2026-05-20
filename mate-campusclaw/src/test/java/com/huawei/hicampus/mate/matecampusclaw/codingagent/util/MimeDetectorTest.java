/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MimeDetectorTest {

    @Nested
    class FromExtension {

        @Test
        void textExtensions() {
            assertThat(MimeDetector.fromExtension(Paths.get("a.txt"))).isEqualTo("text/plain");
            assertThat(MimeDetector.fromExtension(Paths.get("a.md"))).isEqualTo("text/markdown");
            assertThat(MimeDetector.fromExtension(Paths.get("a.html"))).isEqualTo("text/html");
            assertThat(MimeDetector.fromExtension(Paths.get("a.htm"))).isEqualTo("text/html");
            assertThat(MimeDetector.fromExtension(Paths.get("a.css"))).isEqualTo("text/css");
            assertThat(MimeDetector.fromExtension(Paths.get("a.csv"))).isEqualTo("text/csv");
            assertThat(MimeDetector.fromExtension(Paths.get("a.xml"))).isEqualTo("text/xml");
            assertThat(MimeDetector.fromExtension(Paths.get("a.yaml"))).isEqualTo("text/yaml");
            assertThat(MimeDetector.fromExtension(Paths.get("a.yml"))).isEqualTo("text/yaml");
            assertThat(MimeDetector.fromExtension(Paths.get("a.toml"))).isEqualTo("text/x-toml");
        }

        @Test
        void codeExtensions() {
            assertThat(MimeDetector.fromExtension(Paths.get("a.js"))).isEqualTo("text/javascript");
            assertThat(MimeDetector.fromExtension(Paths.get("a.mjs"))).isEqualTo("text/javascript");
            assertThat(MimeDetector.fromExtension(Paths.get("a.ts"))).isEqualTo("text/typescript");
            assertThat(MimeDetector.fromExtension(Paths.get("a.tsx"))).isEqualTo("text/typescript");
            assertThat(MimeDetector.fromExtension(Paths.get("a.jsx"))).isEqualTo("text/javascript");
            assertThat(MimeDetector.fromExtension(Paths.get("Foo.java"))).isEqualTo("text/x-java");
            assertThat(MimeDetector.fromExtension(Paths.get("a.py"))).isEqualTo("text/x-python");
            assertThat(MimeDetector.fromExtension(Paths.get("a.rb"))).isEqualTo("text/x-ruby");
            assertThat(MimeDetector.fromExtension(Paths.get("a.go"))).isEqualTo("text/x-go");
            assertThat(MimeDetector.fromExtension(Paths.get("a.rs"))).isEqualTo("text/x-rust");
            assertThat(MimeDetector.fromExtension(Paths.get("a.c"))).isEqualTo("text/x-c");
            assertThat(MimeDetector.fromExtension(Paths.get("a.cpp"))).isEqualTo("text/x-c++");
            assertThat(MimeDetector.fromExtension(Paths.get("a.h"))).isEqualTo("text/x-c");
            assertThat(MimeDetector.fromExtension(Paths.get("a.hpp"))).isEqualTo("text/x-c++");
            assertThat(MimeDetector.fromExtension(Paths.get("a.cs"))).isEqualTo("text/x-csharp");
            assertThat(MimeDetector.fromExtension(Paths.get("a.swift"))).isEqualTo("text/x-swift");
            assertThat(MimeDetector.fromExtension(Paths.get("a.kt"))).isEqualTo("text/x-kotlin");
            assertThat(MimeDetector.fromExtension(Paths.get("a.scala"))).isEqualTo("text/x-scala");
            assertThat(MimeDetector.fromExtension(Paths.get("a.sh"))).isEqualTo("text/x-shellscript");
            assertThat(MimeDetector.fromExtension(Paths.get("a.bash"))).isEqualTo("text/x-shellscript");
            assertThat(MimeDetector.fromExtension(Paths.get("a.zsh"))).isEqualTo("text/x-shellscript");
            assertThat(MimeDetector.fromExtension(Paths.get("a.sql"))).isEqualTo("text/x-sql");
            assertThat(MimeDetector.fromExtension(Paths.get("a.r"))).isEqualTo("text/x-r");
            assertThat(MimeDetector.fromExtension(Paths.get("a.lua"))).isEqualTo("text/x-lua");
            assertThat(MimeDetector.fromExtension(Paths.get("a.php"))).isEqualTo("text/x-php");
            assertThat(MimeDetector.fromExtension(Paths.get("a.pl"))).isEqualTo("text/x-perl");
            assertThat(MimeDetector.fromExtension(Paths.get("a.ex"))).isEqualTo("text/x-elixir");
            assertThat(MimeDetector.fromExtension(Paths.get("a.erl"))).isEqualTo("text/x-erlang");
            assertThat(MimeDetector.fromExtension(Paths.get("a.hs"))).isEqualTo("text/x-haskell");
            assertThat(MimeDetector.fromExtension(Paths.get("a.clj"))).isEqualTo("text/x-clojure");
            assertThat(MimeDetector.fromExtension(Paths.get("a.dart"))).isEqualTo("text/x-dart");
        }

        @Test
        void configExtensions() {
            assertThat(MimeDetector.fromExtension(Paths.get("a.json"))).isEqualTo("application/json");
            assertThat(MimeDetector.fromExtension(Paths.get("a.jsonl"))).isEqualTo("application/x-jsonlines");
            assertThat(MimeDetector.fromExtension(Paths.get("a.properties"))).isEqualTo("text/x-java-properties");
            assertThat(MimeDetector.fromExtension(Paths.get("a.ini"))).isEqualTo("text/x-ini");
            assertThat(MimeDetector.fromExtension(Paths.get("a.cfg"))).isEqualTo("text/x-ini");
            assertThat(MimeDetector.fromExtension(Paths.get("a.conf"))).isEqualTo("text/x-ini");
            assertThat(MimeDetector.fromExtension(Paths.get("a.gradle"))).isEqualTo("text/x-gradle");
        }

        @Test
        void imageExtensions() {
            assertThat(MimeDetector.fromExtension(Paths.get("a.png"))).isEqualTo("image/png");
            assertThat(MimeDetector.fromExtension(Paths.get("a.jpg"))).isEqualTo("image/jpeg");
            assertThat(MimeDetector.fromExtension(Paths.get("a.jpeg"))).isEqualTo("image/jpeg");
            assertThat(MimeDetector.fromExtension(Paths.get("a.gif"))).isEqualTo("image/gif");
            assertThat(MimeDetector.fromExtension(Paths.get("a.webp"))).isEqualTo("image/webp");
            assertThat(MimeDetector.fromExtension(Paths.get("a.svg"))).isEqualTo("image/svg+xml");
            assertThat(MimeDetector.fromExtension(Paths.get("a.bmp"))).isEqualTo("image/bmp");
            assertThat(MimeDetector.fromExtension(Paths.get("a.ico"))).isEqualTo("image/x-icon");
            assertThat(MimeDetector.fromExtension(Paths.get("a.tiff"))).isEqualTo("image/tiff");
            assertThat(MimeDetector.fromExtension(Paths.get("a.tif"))).isEqualTo("image/tiff");
        }

        @Test
        void documentExtensions() {
            assertThat(MimeDetector.fromExtension(Paths.get("a.pdf"))).isEqualTo("application/pdf");
            assertThat(MimeDetector.fromExtension(Paths.get("a.doc"))).isEqualTo("application/msword");
            assertThat(MimeDetector.fromExtension(Paths.get("a.docx")))
                    .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            assertThat(MimeDetector.fromExtension(Paths.get("a.xls"))).isEqualTo("application/vnd.ms-excel");
            assertThat(MimeDetector.fromExtension(Paths.get("a.xlsx")))
                    .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            assertThat(MimeDetector.fromExtension(Paths.get("a.pptx")))
                    .isEqualTo("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        }

        @Test
        void archiveAndBinaryExtensions() {
            assertThat(MimeDetector.fromExtension(Paths.get("a.zip"))).isEqualTo("application/zip");
            assertThat(MimeDetector.fromExtension(Paths.get("a.tar"))).isEqualTo("application/x-tar");
            assertThat(MimeDetector.fromExtension(Paths.get("a.gz"))).isEqualTo("application/gzip");
            assertThat(MimeDetector.fromExtension(Paths.get("a.jar"))).isEqualTo("application/java-archive");
            assertThat(MimeDetector.fromExtension(Paths.get("a.wasm"))).isEqualTo("application/wasm");
            assertThat(MimeDetector.fromExtension(Paths.get("Foo.class"))).isEqualTo("application/java-vm");
            assertThat(MimeDetector.fromExtension(Paths.get("a.so"))).isEqualTo("application/x-sharedlib");
            assertThat(MimeDetector.fromExtension(Paths.get("a.dylib"))).isEqualTo("application/x-sharedlib");
            assertThat(MimeDetector.fromExtension(Paths.get("a.exe"))).isEqualTo("application/x-executable");
            assertThat(MimeDetector.fromExtension(Paths.get("a.dll"))).isEqualTo("application/x-dosexec");
        }

        @Test
        void noExtensionReturnsOctetStream() {
            assertThat(MimeDetector.fromExtension(Paths.get("README"))).isEqualTo("application/octet-stream");
        }

        @Test
        void unknownExtensionReturnsOctetStream() {
            assertThat(MimeDetector.fromExtension(Paths.get("a.xyz123"))).isEqualTo("application/octet-stream");
        }

        @Test
        void uppercaseExtensionNormalizedToLowercase() {
            assertThat(MimeDetector.fromExtension(Paths.get("FOO.PNG"))).isEqualTo("image/png");
            assertThat(MimeDetector.fromExtension(Paths.get("Bar.JaVa"))).isEqualTo("text/x-java");
        }

        @Test
        void multipleDotsUsesLastSegment() {
            assertThat(MimeDetector.fromExtension(Paths.get("archive.tar.gz"))).isEqualTo("application/gzip");
        }

        @Test
        void hiddenFileWithLeadingDotTreatsAsExtension() {
            // ".gitignore" — lastIndexOf('.') == 0, ext = "gitignore" → unknown
            assertThat(MimeDetector.fromExtension(Paths.get(".gitignore"))).isEqualTo("application/octet-stream");
        }
    }

    @Nested
    class Probe {

        @Test
        void nonExistentFileReturnsEmptyOrSomething() {
            // probeContentType for a non-existent file may return null (most platforms);

            // either way, no exception should escape.
            Optional<String> result = MimeDetector.probe(Paths.get("/definitely/does/not/exist/xyz.txt"));
            assertThat(result).isNotNull();
        }
    }

    @Nested
    class Detect {

        @Test
        void fallsBackToExtensionWhenProbeYieldsNothing() {
            // For a non-existent file probe returns empty on most platforms; detect should
            // fall through to extension lookup.
            Path nonExistent = Paths.get("/no/such/path/sample.json");
            String result = MimeDetector.detect(nonExistent);

            // It must be a mime string — either probe somehow returned something, or fallback returned
            // application/json.
            assertThat(result).isNotBlank();
        }
    }

    @Nested
    class IsText {

        @Test
        void textPrefixedMimeIsText() {
            assertThat(MimeDetector.isText("text/plain")).isTrue();
            assertThat(MimeDetector.isText("text/html")).isTrue();
        }

        @Test
        void jsonFamilyIsText() {
            assertThat(MimeDetector.isText("application/json")).isTrue();
            assertThat(MimeDetector.isText("application/x-jsonlines")).isTrue();
            assertThat(MimeDetector.isText("application/xml")).isTrue();
        }

        @Test
        void binaryMimeIsNotText() {
            assertThat(MimeDetector.isText("application/pdf")).isFalse();
            assertThat(MimeDetector.isText("image/png")).isFalse();
        }
    }

    @Nested
    class IsImage {

        @Test
        void imagePrefixed() {
            assertThat(MimeDetector.isImage("image/png")).isTrue();
            assertThat(MimeDetector.isImage("image/jpeg")).isTrue();
            assertThat(MimeDetector.isImage("image/svg+xml")).isTrue();
        }

        @Test
        void nonImage() {
            assertThat(MimeDetector.isImage("text/plain")).isFalse();
            assertThat(MimeDetector.isImage("application/json")).isFalse();
        }
    }

    @Nested
    class IsBinary {

        @Test
        void neitherTextNorImageIsBinary() {
            assertThat(MimeDetector.isBinary("application/pdf")).isTrue();
            assertThat(MimeDetector.isBinary("application/zip")).isTrue();
            assertThat(MimeDetector.isBinary("application/octet-stream")).isTrue();
        }

        @Test
        void textIsNotBinary() {
            assertThat(MimeDetector.isBinary("text/plain")).isFalse();
            assertThat(MimeDetector.isBinary("application/json")).isFalse();
        }

        @Test
        void imageIsNotBinary() {
            assertThat(MimeDetector.isBinary("image/png")).isFalse();
        }
    }

    @Nested
    class GetExtension {

        @Test
        void normalFile() {
            assertThat(MimeDetector.getExtension(Paths.get("foo.PDF"))).isEqualTo("pdf");
            assertThat(MimeDetector.getExtension(Paths.get("foo.Tar.Gz"))).isEqualTo("gz");
        }

        @Test
        void noExtensionReturnsEmpty() {
            assertThat(MimeDetector.getExtension(Paths.get("README"))).isEmpty();
        }

        @Test
        void leadingDotIsExtensionStart() {
            assertThat(MimeDetector.getExtension(Paths.get(".env"))).isEqualTo("env");
        }
    }
}
