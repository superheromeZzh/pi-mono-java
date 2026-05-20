/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceLoaderTest {

    private static Path makeProjectFile(Path cwd, String subPath, String name, String content) throws IOException {
        Path dir = cwd.resolve(".campusclaw").resolve(subPath);
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static Path makeUserFile(Path userDir, String subPath, String name, String content) throws IOException {
        Path dir = userDir.resolve(subPath);
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Nested
    class Discover {

        @Test
        void emptyDirsReturnEmpty(@TempDir Path cwd, @TempDir Path user) {
            ResourceLoader loader = new ResourceLoader(cwd, user);
            assertThat(loader.discover("skills")).isEmpty();
        }

        @Test
        void projectShadowsUser(@TempDir Path cwd, @TempDir Path user) throws IOException {
            makeProjectFile(cwd, "skills", "a.md", "project");
            makeUserFile(user, "skills", "a.md", "user");
            makeUserFile(user, "skills", "b.md", "userB");
            List<ResourceLoader.Resource> result = new ResourceLoader(cwd, user).discover("skills");
            assertThat(result).extracting(ResourceLoader.Resource::name).containsExactlyInAnyOrder("a.md", "b.md");

            // 'a' must be from project
            ResourceLoader.Resource a = result.stream()
                    .filter(r -> r.name().equals("a.md"))
                    .findFirst()
                    .get();
            assertThat(a.source()).isEqualTo("project");
        }

        @Test
        void onlyUserResources(@TempDir Path cwd, @TempDir Path user) throws IOException {
            makeUserFile(user, "skills", "x.md", "X");
            List<ResourceLoader.Resource> result = new ResourceLoader(cwd, user).discover("skills");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).source()).isEqualTo("user");
        }
    }

    @Nested
    class Load {

        @Test
        void projectFileWins(@TempDir Path cwd, @TempDir Path user) throws IOException {
            makeProjectFile(cwd, "skills", "a.md", "project body");
            makeUserFile(user, "skills", "a.md", "user body");
            assertThat(new ResourceLoader(cwd, user).load("skills", "a.md")).contains("project body");
        }

        @Test
        void userFileFallback(@TempDir Path cwd, @TempDir Path user) throws IOException {
            makeUserFile(user, "skills", "b.md", "user body");
            assertThat(new ResourceLoader(cwd, user).load("skills", "b.md")).contains("user body");
        }

        @Test
        void missingReturnsEmpty(@TempDir Path cwd, @TempDir Path user) {
            assertThat(new ResourceLoader(cwd, user).load("skills", "nothing.md"))
                    .isEmpty();
        }
    }
}
