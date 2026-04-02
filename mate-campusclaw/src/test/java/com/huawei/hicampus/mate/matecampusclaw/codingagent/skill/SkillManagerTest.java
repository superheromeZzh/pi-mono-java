package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillManagerTest {

    @TempDir
    Path tempDir;

    Path skillsDir;
    SkillManager manager;

    @BeforeEach
    void setUp() {
        skillsDir = tempDir.resolve("skills");
        manager = new SkillManager(skillsDir);
    }

    // -------------------------------------------------------------------
    // extractRepoName
    // -------------------------------------------------------------------

    @Nested
    class ExtractRepoName {

        @Test
        void extractsFromHttpsUrl() throws SkillInstallException {
            assertEquals("my-skill", SkillManager.extractRepoName("https://github.com/user/my-skill"));
        }

        @Test
        void extractsFromHttpsUrlWithGitSuffix() throws SkillInstallException {
            assertEquals("my-skill", SkillManager.extractRepoName("https://github.com/user/my-skill.git"));
        }

        @Test
        void extractsFromSshUrl() throws SkillInstallException {
            assertEquals("my-skill", SkillManager.extractRepoName("git@github.com:user/my-skill.git"));
        }

        @Test
        void stripsTrailingSlash() throws SkillInstallException {
            assertEquals("my-skill", SkillManager.extractRepoName("https://github.com/user/my-skill/"));
        }

        @Test
        void lowercasesName() throws SkillInstallException {
            assertEquals("my-skill", SkillManager.extractRepoName("https://github.com/user/My-Skill"));
        }

        @Test
        void replacesInvalidCharsWithHyphens() throws SkillInstallException {
            assertEquals("my-skill", SkillManager.extractRepoName("https://github.com/user/my_skill"));
        }
    }

    // -------------------------------------------------------------------
    // Link
    // -------------------------------------------------------------------

    @Nested
    class Link {

        @Test
        void linksValidSkillDirectory() throws Exception {
            Path source = createSkillDir(tempDir.resolve("my-local-skill"), "A local skill");

            String name = manager.link(source);

            assertEquals("my-local-skill", name);
            assertTrue(Files.isSymbolicLink(skillsDir.resolve(name)));
            assertEquals(source.toAbsolutePath().normalize(),
                    Files.readSymbolicLink(skillsDir.resolve(name)));
        }

        @Test
        void recordsInManifest() throws Exception {
            Path source = createSkillDir(tempDir.resolve("link-test"), "test");

            manager.link(source);

            List<InstalledSkillRecord> manifest = manager.loadManifest();
            assertEquals(1, manifest.size());
            assertEquals("link-test", manifest.get(0).name());
            assertEquals(InstalledSkillRecord.SOURCE_LINK, manifest.get(0).sourceType());
            assertEquals(source.toAbsolutePath().normalize().toString(), manifest.get(0).localPath());
        }

        @Test
        void rejectsNonDirectory() {
            Path file = tempDir.resolve("not-a-dir");
            assertThrows(SkillInstallException.class, () -> manager.link(file));
        }

        @Test
        void rejectsDirectoryWithoutSkill() throws Exception {
            Path dir = tempDir.resolve("empty-dir");
            Files.createDirectories(dir);

            assertThrows(SkillInstallException.class, () -> manager.link(dir));
        }

        @Test
        void rejectsWhenTargetAlreadyExists() throws Exception {
            Path source = createSkillDir(tempDir.resolve("dup-skill"), "skill");

            manager.link(source);
            assertThrows(SkillInstallException.class, () -> manager.link(source));
        }
    }

    // -------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------

    @Nested
    class ListSkills {

        @Test
        void returnsEmptyListWhenNoSkills() {
            List<SkillManager.SkillInfo> infos = manager.list();
            assertTrue(infos.isEmpty());
        }

        @Test
        void listsManuallyPlacedSkills() throws Exception {
            createSkillDir(skillsDir.resolve("manual-skill"), "A manual skill");

            List<SkillManager.SkillInfo> infos = manager.list();

            assertEquals(1, infos.size());
            assertEquals("manual-skill", infos.get(0).name());
            assertEquals("local", infos.get(0).sourceType());
            assertEquals("A manual skill", infos.get(0).description());
        }

        @Test
        void listsLinkedSkills() throws Exception {
            Path source = createSkillDir(tempDir.resolve("linked-skill"), "A linked skill");
            manager.link(source);

            List<SkillManager.SkillInfo> infos = manager.list();

            assertEquals(1, infos.size());
            assertEquals("linked-skill", infos.get(0).name());
            assertEquals(InstalledSkillRecord.SOURCE_LINK, infos.get(0).sourceType());
        }

        @Test
        void listsSortedByName() throws Exception {
            createSkillDir(skillsDir.resolve("zebra"), "Z skill");
            createSkillDir(skillsDir.resolve("alpha"), "A skill");

            List<SkillManager.SkillInfo> infos = manager.list();

            assertEquals(2, infos.size());
            assertEquals("alpha", infos.get(0).name());
            assertEquals("zebra", infos.get(1).name());
        }
    }

    // -------------------------------------------------------------------
    // Remove
    // -------------------------------------------------------------------

    @Nested
    class Remove {

        @Test
        void removesManuallyPlacedSkill() throws Exception {
            createSkillDir(skillsDir.resolve("to-remove"), "Remove me");

            manager.remove("to-remove");

            assertFalse(Files.exists(skillsDir.resolve("to-remove")));
        }

        @Test
        void removesSymlinkedSkill() throws Exception {
            Path source = createSkillDir(tempDir.resolve("linked-to-remove"), "Remove link");
            manager.link(source);

            manager.remove("linked-to-remove");

            assertFalse(Files.exists(skillsDir.resolve("linked-to-remove")));
            // Original directory should still exist
            assertTrue(Files.exists(source));
        }

        @Test
        void removesFromManifest() throws Exception {
            Path source = createSkillDir(tempDir.resolve("tracked"), "Tracked");
            manager.link(source);
            assertEquals(1, manager.loadManifest().size());

            manager.remove("tracked");

            assertEquals(0, manager.loadManifest().size());
        }

        @Test
        void throwsWhenSkillNotFound() {
            assertThrows(SkillInstallException.class, () -> manager.remove("nonexistent"));
        }
    }

    // -------------------------------------------------------------------
    // Manifest persistence
    // -------------------------------------------------------------------

    @Nested
    class ManifestPersistence {

        @Test
        void loadsEmptyManifestWhenFileDoesNotExist() {
            List<InstalledSkillRecord> records = manager.loadManifest();
            assertTrue(records.isEmpty());
        }

        @Test
        void saveAndLoadRoundTrip() throws Exception {
            Files.createDirectories(skillsDir);
            var records = List.of(
                    new InstalledSkillRecord("skill-a", "git",
                            "https://github.com/user/skill-a", null, "2026-01-01T00:00:00Z"),
                    new InstalledSkillRecord("skill-b", "link",
                            null, "/tmp/skill-b", "2026-01-02T00:00:00Z")
            );

            manager.saveManifest(records);
            List<InstalledSkillRecord> loaded = manager.loadManifest();

            assertEquals(2, loaded.size());
            assertEquals("skill-a", loaded.get(0).name());
            assertEquals("git", loaded.get(0).sourceType());
            assertEquals("skill-b", loaded.get(1).name());
            assertEquals("link", loaded.get(1).sourceType());
        }

        @Test
        void handlesCorruptManifest() throws Exception {
            Files.createDirectories(skillsDir);
            Files.writeString(skillsDir.resolve(".installed.json"), "not valid json{{{");

            List<InstalledSkillRecord> records = manager.loadManifest();
            assertTrue(records.isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // deleteRecursively
    // -------------------------------------------------------------------

    @Nested
    class DeleteRecursively {

        @Test
        void deletesNestedDirectoryStructure() throws Exception {
            Path dir = tempDir.resolve("nested");
            Files.createDirectories(dir.resolve("a/b/c"));
            Files.writeString(dir.resolve("a/b/c/file.txt"), "content");
            Files.writeString(dir.resolve("a/file.txt"), "content");

            SkillManager.deleteRecursively(dir);

            assertFalse(Files.exists(dir));
        }

        @Test
        void handlesNonExistentPath() {
            // Should not throw
            SkillManager.deleteRecursively(tempDir.resolve("does-not-exist"));
        }

        @Test
        void handlesNull() {
            // Should not throw
            SkillManager.deleteRecursively(null);
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private Path createSkillDir(Path dir, String description) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), """
                ---
                description: %s
                ---
                Skill content here.
                """.formatted(description));
        return dir;
    }
}
