package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages skill lifecycle: install from git, link local directories, list, and remove.
 * Tracks installed skills in a {@code .installed.json} manifest file.
 */
public class SkillManager {

    private static final Logger log = LoggerFactory.getLogger(SkillManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MANIFEST_FILE = ".installed.json";
    private static final long GIT_TIMEOUT_SECONDS = 120;

    /**
     * Pattern to extract repository name from a git URL.
     * Handles: https://github.com/user/repo.git, git@github.com:user/repo.git, github.com/user/repo
     */
    private static final Pattern REPO_NAME_PATTERN = Pattern.compile(
            "(?:.*/|:)([^/]+?)(?:\\.git)?/?$");

    private final Path skillsDir;
    private final SkillLoader skillLoader;

    public SkillManager(Path skillsDir) {
        this.skillsDir = skillsDir;
        this.skillLoader = new SkillLoader();
    }

    // -- Install from git --------------------------------------------------

    /**
     * Clones a git repository into the skills directory.
     *
     * @param gitUrl the git clone URL
     * @return the installed skill's directory name
     */
    public String install(String gitUrl) throws SkillInstallException {
        String repoName = extractRepoName(gitUrl);
        Path targetDir = skillsDir.resolve(repoName);

        if (Files.exists(targetDir)) {
            throw new SkillInstallException("Directory already exists: " + targetDir
                    + "\nUse 'campusclaw skill remove " + repoName + "' first, or choose a different name.");
        }

        // Clone
        try {
            Files.createDirectories(skillsDir);
            int exitCode = runGitClone(gitUrl, targetDir);
            if (exitCode != 0) {
                deleteRecursively(targetDir);
                throw new SkillInstallException("git clone failed (exit code " + exitCode + ") for: " + gitUrl);
            }
        } catch (IOException | InterruptedException e) {
            deleteRecursively(targetDir);
            throw new SkillInstallException("Failed to clone: " + gitUrl + " — " + e.getMessage(), e);
        }

        // Validate that the clone contains skill(s)
        List<Skill> skills = skillLoader.loadFromDirectory(targetDir, "user");
        if (skills.isEmpty()) {
            // Maybe the repo root itself is a skill (has SKILL.md at root)
            Path rootSkill = targetDir.resolve(SkillLoader.SKILL_FILENAME);
            if (!Files.isRegularFile(rootSkill)) {
                deleteRecursively(targetDir);
                throw new SkillInstallException(
                        "No SKILL.md found in repository: " + gitUrl
                                + "\nThe repository must contain at least one SKILL.md file.");
            }
        }

        // Record in manifest
        var record = new InstalledSkillRecord(
                repoName,
                InstalledSkillRecord.SOURCE_GIT,
                gitUrl,
                null,
                Instant.now().toString()
        );
        addToManifest(record);

        return repoName;
    }

    // -- Link local directory -----------------------------------------------

    /**
     * Creates a symbolic link in the skills directory pointing to a local path.
     *
     * @param localPath path to a directory containing SKILL.md (or subdirectories with SKILL.md)
     * @return the link name created
     */
    public String link(Path localPath) throws SkillInstallException {
        Path resolved = localPath.toAbsolutePath().normalize();

        if (!Files.isDirectory(resolved)) {
            throw new SkillInstallException("Not a directory: " + resolved);
        }

        // Validate it contains at least one skill
        List<Skill> skills = skillLoader.loadFromDirectory(resolved, "user");
        Path rootSkill = resolved.resolve(SkillLoader.SKILL_FILENAME);
        if (skills.isEmpty() && !Files.isRegularFile(rootSkill)) {
            throw new SkillInstallException(
                    "No SKILL.md found in: " + resolved
                            + "\nThe directory must contain at least one SKILL.md file.");
        }

        String linkName = resolved.getFileName().toString().toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (linkName.isEmpty()) {
            throw new SkillInstallException("Cannot derive a valid skill name from path: " + resolved);
        }

        Path linkPath = skillsDir.resolve(linkName);
        if (Files.exists(linkPath)) {
            throw new SkillInstallException("Directory already exists: " + linkPath
                    + "\nUse 'campusclaw skill remove " + linkName + "' first.");
        }

        try {
            Files.createDirectories(skillsDir);
            Files.createSymbolicLink(linkPath, resolved);
        } catch (IOException e) {
            throw new SkillInstallException("Failed to create symlink: " + e.getMessage(), e);
        }

        var record = new InstalledSkillRecord(
                linkName,
                InstalledSkillRecord.SOURCE_LINK,
                null,
                resolved.toString(),
                Instant.now().toString()
        );
        addToManifest(record);

        return linkName;
    }

    // -- Import from archive ------------------------------------------------

    /**
     * Extracts a ZIP or TAR.GZ archive into the skills directory.
     * Supports: .zip, .tar.gz, .tgz
     *
     * @param archivePath path to the archive file
     * @return the installed skill's directory name
     */
    public String importArchive(Path archivePath) throws SkillInstallException {
        return importArchive(archivePath, null);
    }

    /**
     * Extracts a ZIP or TAR.GZ archive into the skills directory.
     *
     * @param archivePath    path to the archive file
     * @param originalName   original filename for deriving skill name (nullable; falls back to archivePath filename)
     * @return the installed skill's directory name
     */
    public String importArchive(Path archivePath, String originalName) throws SkillInstallException {
        Path resolved = archivePath.toAbsolutePath().normalize();

        if (!Files.isRegularFile(resolved)) {
            throw new SkillInstallException("File not found: " + resolved);
        }

        String fileName = (originalName != null && !originalName.isBlank())
                ? originalName.toLowerCase()
                : resolved.getFileName().toString().toLowerCase();
        boolean isZip = fileName.endsWith(".zip");
        boolean isTarGz = fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz");

        if (!isZip && !isTarGz) {
            throw new SkillInstallException(
                    "Unsupported archive format: " + fileName
                            + "\nSupported formats: .zip, .tar.gz, .tgz");
        }

        // Derive skill name from archive filename (strip extension)
        String baseName = fileName;
        if (fileName.endsWith(".tar.gz")) {
            baseName = fileName.substring(0, fileName.length() - 7);
        } else if (fileName.endsWith(".tgz")) {
            baseName = fileName.substring(0, fileName.length() - 4);
        } else if (fileName.endsWith(".zip")) {
            baseName = fileName.substring(0, fileName.length() - 4);
        }
        String skillName = baseName
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (skillName.isEmpty()) {
            throw new SkillInstallException("Cannot derive a valid skill name from: " + fileName);
        }

        Path targetDir = skillsDir.resolve(skillName);
        if (Files.exists(targetDir)) {
            throw new SkillInstallException("Directory already exists: " + targetDir
                    + "\nUse 'campusclaw skill remove " + skillName + "' first.");
        }

        // Extract to a temp directory first, then move
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("campusclaw-skill-import-");
        } catch (IOException e) {
            throw new SkillInstallException("Failed to create temp directory: " + e.getMessage(), e);
        }

        try {
            if (isZip) {
                extractZip(resolved, tempDir);
            } else {
                extractTarGz(resolved, tempDir);
            }

            // If the archive has a single top-level directory, use its contents
            Path extractRoot = unwrapSingleRoot(tempDir);

            // Validate that it contains at least one SKILL.md
            List<Skill> skills = skillLoader.loadFromDirectory(extractRoot, "user");
            Path rootSkill = extractRoot.resolve(SkillLoader.SKILL_FILENAME);
            if (skills.isEmpty() && !Files.isRegularFile(rootSkill)) {
                throw new SkillInstallException(
                        "No SKILL.md found in archive: " + resolved
                                + "\nThe archive must contain at least one SKILL.md file.");
            }

            // Move to skills directory
            Files.createDirectories(skillsDir);
            Files.move(extractRoot, targetDir);
        } catch (IOException e) {
            deleteRecursively(targetDir);
            throw new SkillInstallException("Failed to extract archive: " + e.getMessage(), e);
        } finally {
            deleteRecursively(tempDir);
        }

        // Record in manifest
        var record = new InstalledSkillRecord(
                skillName,
                InstalledSkillRecord.SOURCE_ARCHIVE,
                null,
                resolved.toString(),
                Instant.now().toString()
        );
        addToManifest(record);

        return skillName;
    }

    private void extractZip(Path zipFile, Path destDir) throws IOException {
        try (InputStream fis = Files.newInputStream(zipFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName()).normalize();
                // Guard against zip-slip
                if (!entryPath.startsWith(destDir)) {
                    throw new IOException("Zip entry outside target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath);
                }
                zis.closeEntry();
            }
        }
    }

    private void extractTarGz(Path tarGzFile, Path destDir) throws IOException {
        // Use system tar command — available on macOS and Linux
        try {
            var pb = new ProcessBuilder("tar", "xzf", tarGzFile.toString(), "-C", destDir.toString())
                    .redirectErrorStream(true);
            var process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean completed = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("tar extraction timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("tar extraction failed:\n" + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("tar extraction interrupted", e);
        }
    }

    /**
     * If the extracted directory contains exactly one subdirectory and no files,
     * return that subdirectory (common pattern: archive has a single root folder).
     * Otherwise return the directory itself.
     */
    private Path unwrapSingleRoot(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            List<Path> children = entries.toList();
            if (children.size() == 1 && Files.isDirectory(children.get(0))) {
                return children.get(0);
            }
        }
        return dir;
    }

    // -- List ---------------------------------------------------------------

    /**
     * Describes an installed skill for display.
     */
    public record SkillInfo(
            String name,
            String sourceType,
            String source,
            String description
    ) {}

    /**
     * Lists all skills in the skills directory, cross-referencing the manifest.
     */
    public List<SkillInfo> list() {
        List<InstalledSkillRecord> manifest = loadManifest();
        List<SkillInfo> result = new ArrayList<>();

        // Load skills from disk
        List<Skill> diskSkills = skillLoader.loadFromDirectory(skillsDir, "user");

        for (Skill skill : diskSkills) {
            // Try to find manifest entry for this skill's parent directory
            String dirName = skill.baseDir().getFileName().toString();
            // Walk up to find the top-level directory under skillsDir
            String topDir = resolveTopDir(skill.baseDir());

            Optional<InstalledSkillRecord> record = manifest.stream()
                    .filter(r -> r.name().equals(topDir) || r.name().equals(dirName))
                    .findFirst();

            String sourceType;
            String source;
            if (record.isPresent()) {
                sourceType = record.get().sourceType();
                source = record.get().gitUrl() != null ? record.get().gitUrl() : record.get().localPath();
            } else {
                // Check if it's a symlink
                Path dirPath = skill.baseDir();
                if (Files.isSymbolicLink(dirPath) || Files.isSymbolicLink(skillsDir.resolve(topDir))) {
                    sourceType = "link";
                    try {
                        source = Files.readSymbolicLink(
                                Files.isSymbolicLink(dirPath) ? dirPath : skillsDir.resolve(topDir)
                        ).toString();
                    } catch (IOException e) {
                        source = "?";
                    }
                } else {
                    sourceType = "local";
                    source = skill.baseDir().toString();
                }
            }

            result.add(new SkillInfo(skill.name(), sourceType, source, skill.description()));
        }

        // Sort by name
        result.sort(Comparator.comparing(SkillInfo::name));
        return result;
    }

    // -- Remove -------------------------------------------------------------

    /**
     * Removes an installed skill by name.
     *
     * @param name the skill directory name (as shown by list)
     */
    public void remove(String name) throws SkillInstallException {
        Path targetDir = skillsDir.resolve(name);

        if (!Files.exists(targetDir) && !Files.isSymbolicLink(targetDir)) {
            throw new SkillInstallException("Skill not found: " + name
                    + "\nUse 'campusclaw skill list' to see installed skills.");
        }

        // Remove from filesystem
        if (Files.isSymbolicLink(targetDir)) {
            try {
                Files.delete(targetDir);
            } catch (IOException e) {
                throw new SkillInstallException("Failed to remove symlink: " + e.getMessage(), e);
            }
        } else {
            deleteRecursively(targetDir);
        }

        // Remove from manifest
        removeFromManifest(name);
    }

    // -- Update (git pull) --------------------------------------------------

    /**
     * Updates a git-installed skill by pulling latest changes.
     *
     * @param name the skill directory name
     */
    public void update(String name) throws SkillInstallException {
        Path targetDir = skillsDir.resolve(name);

        if (!Files.isDirectory(targetDir)) {
            throw new SkillInstallException("Skill not found: " + name);
        }

        // Check it's a git repo
        if (!Files.isDirectory(targetDir.resolve(".git"))) {
            throw new SkillInstallException("Not a git-installed skill: " + name
                    + " (no .git directory)");
        }

        try {
            var pb = new ProcessBuilder("git", "pull", "--ff-only")
                    .directory(targetDir.toFile())
                    .redirectErrorStream(true);
            var process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean completed = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new SkillInstallException("git pull timed out for: " + name);
            }
            if (process.exitValue() != 0) {
                throw new SkillInstallException("git pull failed for " + name + ":\n" + output);
            }
        } catch (IOException | InterruptedException e) {
            throw new SkillInstallException("Failed to update: " + name + " — " + e.getMessage(), e);
        }
    }

    // -- Manifest I/O -------------------------------------------------------

    List<InstalledSkillRecord> loadManifest() {
        Path manifestPath = skillsDir.resolve(MANIFEST_FILE);
        if (!Files.exists(manifestPath)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(manifestPath);
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (IOException e) {
            log.warn("Failed to read skill manifest: {}", manifestPath, e);
            return new ArrayList<>();
        }
    }

    void saveManifest(List<InstalledSkillRecord> records) {
        Path manifestPath = skillsDir.resolve(MANIFEST_FILE);
        try {
            Path tempFile = manifestPath.resolveSibling(MANIFEST_FILE + ".tmp");
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(records);
            Files.writeString(tempFile, json);
            Files.move(tempFile, manifestPath, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save skill manifest: {}", manifestPath, e);
        }
    }

    private void addToManifest(InstalledSkillRecord record) {
        List<InstalledSkillRecord> records = loadManifest();
        // Remove existing entry with same name
        records.removeIf(r -> r.name().equals(record.name()));
        records.add(record);
        saveManifest(records);
    }

    private void removeFromManifest(String name) {
        List<InstalledSkillRecord> records = loadManifest();
        records.removeIf(r -> r.name().equals(name));
        saveManifest(records);
    }

    // -- Helpers ------------------------------------------------------------

    static String extractRepoName(String gitUrl) throws SkillInstallException {
        // Normalize: strip trailing slashes
        String url = gitUrl.replaceAll("/+$", "");

        Matcher m = REPO_NAME_PATTERN.matcher(url);
        if (m.find()) {
            String name = m.group(1).toLowerCase()
                    .replaceAll("[^a-z0-9-]", "-")
                    .replaceAll("-+", "-")
                    .replaceAll("^-|-$", "");
            if (!name.isEmpty()) {
                return name;
            }
        }
        throw new SkillInstallException("Cannot determine repository name from URL: " + gitUrl);
    }

    private int runGitClone(String gitUrl, Path targetDir) throws IOException, InterruptedException {
        var pb = new ProcessBuilder("git", "clone", "--depth", "1", gitUrl, targetDir.toString())
                .redirectErrorStream(true);
        var process = pb.start();
        // Consume output to avoid blocking
        process.getInputStream().readAllBytes();
        boolean completed = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return -1;
        }
        return process.exitValue();
    }

    private String resolveTopDir(Path baseDir) {
        Path relative = skillsDir.relativize(baseDir);
        return relative.getName(0).toString();
    }

    static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to delete directory: {}", dir, e);
        }
    }
}
