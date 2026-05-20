/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.pkg;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageManagerTest {

    private static Path makePackage(Path root, String name, String json) throws IOException {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("package.json"), json);
        return dir;
    }

    @Nested
    class Scan {

        @Test
        void missingDirectoryIsNoOp(@TempDir Path tmp) {
            PackageManager m = new PackageManager(tmp.resolve("missing"));
            m.scan();
            assertThat(m.getInstalled()).isEmpty();
        }

        @Test
        void emptyDirectoryYieldsEmpty(@TempDir Path tmp) {
            PackageManager m = new PackageManager(tmp);
            m.scan();
            assertThat(m.getInstalled()).isEmpty();
        }

        @Test
        void readsManifest(@TempDir Path tmp) throws IOException {
            makePackage(
                    tmp,
                    "alpha",
                    "{\"name\":\"alpha\",\"version\":\"1.0\",\"description\":\"x\",\"skills\":[\"s.md\"]}");
            PackageManager m = new PackageManager(tmp);
            m.scan();
            assertThat(m.isInstalled("alpha")).isTrue();
            assertThat(m.get("alpha")).isPresent();
            assertThat(m.get("alpha").orElseThrow().version()).isEqualTo("1.0");
        }

        @Test
        void manifestWithoutNameDefaultsToDir(@TempDir Path tmp) throws IOException {
            makePackage(tmp, "alpha", "{}");
            PackageManager m = new PackageManager(tmp);
            m.scan();
            assertThat(m.get("alpha")).isPresent();
            assertThat(m.get("alpha").orElseThrow().version()).isEqualTo("0.0.0");
        }

        @Test
        void corruptManifestSkipped(@TempDir Path tmp) throws IOException {
            makePackage(tmp, "bad", "not json");
            PackageManager m = new PackageManager(tmp);
            m.scan();
            assertThat(m.getInstalled()).isEmpty();
        }

        @Test
        void directoryWithoutPackageJsonSkipped(@TempDir Path tmp) throws IOException {
            Files.createDirectories(tmp.resolve("orphan"));
            PackageManager m = new PackageManager(tmp);
            m.scan();
            assertThat(m.getInstalled()).isEmpty();
        }

        @Test
        void rescanClears(@TempDir Path tmp) throws IOException {
            makePackage(tmp, "alpha", "{\"name\":\"alpha\"}");
            PackageManager m = new PackageManager(tmp);
            m.scan();
            assertThat(m.getInstalled()).hasSize(1);

            // Delete the package, rescan
            Files.delete(tmp.resolve("alpha/package.json"));
            Files.delete(tmp.resolve("alpha"));
            m.scan();
            assertThat(m.getInstalled()).isEmpty();
        }
    }

    @Nested
    class GetAllSkillPaths {

        @Test
        void noPackagesReturnsEmpty(@TempDir Path tmp) {
            PackageManager m = new PackageManager(tmp);
            m.scan();
            assertThat(m.getAllSkillPaths()).isEmpty();
        }

        @Test
        void skillsFromManifest(@TempDir Path tmp) throws IOException {
            makePackage(tmp, "alpha", "{\"name\":\"alpha\",\"skills\":[\"a/s.md\",\"b/s.md\"]}");
            PackageManager m = new PackageManager(tmp);
            m.scan();
            assertThat(m.getAllSkillPaths()).hasSize(2);
        }

        @Test
        void skillsFromSkillsDir(@TempDir Path tmp) throws IOException {
            Path pkg = makePackage(tmp, "alpha", "{\"name\":\"alpha\"}");
            Path skillsDir = pkg.resolve("skills");
            Files.createDirectories(skillsDir);
            Files.writeString(skillsDir.resolve("a.md"), "skill a");
            Files.writeString(skillsDir.resolve("b.md"), "skill b");
            Files.writeString(skillsDir.resolve("ignore.txt"), "ignore me");
            PackageManager m = new PackageManager(tmp);
            m.scan();
            assertThat(m.getAllSkillPaths()).hasSize(2);
        }
    }
}
