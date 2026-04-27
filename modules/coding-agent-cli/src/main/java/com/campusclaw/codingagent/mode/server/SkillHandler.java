package com.campusclaw.codingagent.mode.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.campusclaw.codingagent.config.AppPaths;
import com.campusclaw.codingagent.skill.SkillConflictException;
import com.campusclaw.codingagent.skill.SkillInstallException;
import com.campusclaw.codingagent.skill.SkillLoader;
import com.campusclaw.codingagent.skill.SkillManager;
import com.campusclaw.codingagent.skill.SkillStateStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Handles skill CRUD endpoints:
 * <ul>
 *   <li>POST   /api/skills                  — upload archive to create a skill</li>
 *   <li>GET    /api/skills                  — list all installed skills</li>
 *   <li>DELETE /api/skills/{name}           — remove a skill by package name</li>
 *   <li>POST   /api/skills/{name}/enable    — enable a skill by skill name</li>
 *   <li>POST   /api/skills/{name}/disable   — disable a skill by skill name</li>
 * </ul>
 */
public class SkillHandler {

    private static final Logger log = LoggerFactory.getLogger(SkillHandler.class);
    private static final String RELOAD_HINT = "Affects new sessions; run /reload in existing sessions.";

    private final SkillManager skillManager;
    private final SkillLoader skillLoader;

    public SkillHandler(SkillManager skillManager, SkillLoader skillLoader) {
        this.skillManager = skillManager;
        this.skillLoader = skillLoader;
    }

    /**
     * POST /api/skills — multipart file upload (.zip, .tar.gz, .tgz).
     */
    public Mono<ServerResponse> upload(ServerRequest request) {
        return request.multipartData().flatMap(parts -> {
            var part = parts.getFirst("file");
            if (!(part instanceof FilePart filePart)) {
                return ServerResponse.badRequest()
                        .bodyValue(Map.of("error", "Missing 'file' field in multipart request"));
            }

            String filename = filePart.filename();
            if (!filename.endsWith(".zip") && !filename.endsWith(".tar.gz") && !filename.endsWith(".tgz")) {
                return ServerResponse.badRequest()
                        .bodyValue(Map.of("error",
                                "Unsupported format: " + filename + ". Supported: .zip, .tar.gz, .tgz"));
            }

            return Mono.fromCallable(() -> Files.createTempFile("skill-upload-", ".tmp"))
                    .flatMap(tempFile -> filePart.transferTo(tempFile)
                            .then(Mono.fromCallable(() -> importAndDescribe(tempFile, filename))
                                    .subscribeOn(Schedulers.boundedElastic())))
                    .flatMap(result -> ServerResponse.ok().bodyValue(result))
                    .onErrorResume(SkillConflictException.class, e ->
                            ServerResponse.status(409).bodyValue(Map.of(
                                    "error", "Skill name conflict",
                                    "conflicts", e.conflicts())))
                    .onErrorResume(SkillInstallException.class, e ->
                            ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage())))
                    .onErrorResume(Exception.class, e -> {
                        log.error("Skill upload failed", e);
                        return ServerResponse.status(500)
                                .bodyValue(Map.of("error", "Internal error: " + e.getMessage()));
                    });
        });
    }

    /**
     * GET /api/skills — list all installed skills.
     */
    public Mono<ServerResponse> list(ServerRequest request) {
        return Mono.fromCallable(skillManager::list)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(skills -> ServerResponse.ok().bodyValue(skills))
                .onErrorResume(Exception.class, e ->
                        ServerResponse.status(500).bodyValue(Map.of("error", e.getMessage())));
    }

    /**
     * DELETE /api/skills/{name} — remove a skill package.
     */
    public Mono<ServerResponse> delete(ServerRequest request) {
        String name = request.pathVariable("name");
        return Mono.fromCallable(() -> {
                    skillManager.remove(name);
                    return Map.of("message", "Removed skill: " + name);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> ServerResponse.ok().bodyValue(result))
                .onErrorResume(SkillInstallException.class, e ->
                        ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        ServerResponse.status(500).bodyValue(Map.of("error", e.getMessage())));
    }

    /** POST /api/skills/{name}/enable — enable a skill by its skill name (idempotent). */
    public Mono<ServerResponse> enable(ServerRequest request) {
        return toggle(request, true);
    }

    /** POST /api/skills/{name}/disable — disable a skill by its skill name (idempotent). */
    public Mono<ServerResponse> disable(ServerRequest request) {
        return toggle(request, false);
    }

    private Mono<ServerResponse> toggle(ServerRequest request, boolean enable) {
        String name = request.pathVariable("name");
        return Mono.fromCallable(() -> {
                    if (!skillExistsByName(name)) {
                        return ToggleResult.notFound(name);
                    }
                    SkillStateStore store = skillManager.stateStore();
                    if (enable) {
                        store.enable(name);
                    } else {
                        store.disable(name);
                    }
                    return ToggleResult.ok(name, enable);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> {
                    if (result.notFound) {
                        return ServerResponse.status(404)
                                .bodyValue(Map.of("error", "Skill not found: " + result.name));
                    }
                    return ServerResponse.ok().bodyValue(Map.of(
                            "name", result.name,
                            "enabled", result.enabled,
                            "hint", RELOAD_HINT));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed to toggle skill {}", name, e);
                    return ServerResponse.status(500)
                            .bodyValue(Map.of("error", "Internal error: " + e.getMessage()));
                });
    }

    private boolean skillExistsByName(String name) {
        return skillManager.list().stream().anyMatch(info -> info.name().equals(name));
    }

    // -- helpers --------------------------------------------------------------

    private Map<String, Object> importAndDescribe(Path tempFile, String originalFilename) throws Exception {
        try {
            String name = skillManager.importArchive(tempFile, originalFilename);
            var skills = skillLoader.loadFromDirectory(
                    AppPaths.USER_SKILLS_DIR.resolve(name), "user");
            var skillList = new ArrayList<Map<String, String>>();
            for (var skill : skills) {
                skillList.add(Map.of(
                        "name", skill.name(),
                        "description", skill.description()));
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("name", name);
            result.put("skills", skillList);
            return result;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private record ToggleResult(String name, boolean enabled, boolean notFound) {
        static ToggleResult ok(String name, boolean enabled) {
            return new ToggleResult(name, enabled, false);
        }

        static ToggleResult notFound(String name) {
            return new ToggleResult(name, false, true);
        }
    }
}
