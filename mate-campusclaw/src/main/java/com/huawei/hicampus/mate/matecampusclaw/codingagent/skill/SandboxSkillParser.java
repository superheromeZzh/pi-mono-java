package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox.DockerSandboxClient;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox.ResourceLimits;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 沙箱内的 Skill 解析器
 * 将 SKILL.md 拷贝到沙箱容器中解析，确保安全
 */
@Slf4j
@Component
public class SandboxSkillParser {

    private final DockerSandboxClient sandboxClient;

    @Autowired
    public SandboxSkillParser(DockerSandboxClient sandboxClient) {
        this.sandboxClient = sandboxClient;
    }

    /**
     * 检查沙箱解析是否可用
     */
    public boolean isAvailable() {
        return sandboxClient != null && sandboxClient.isAvailable();
    }

    /**
     * 在沙箱中解析 SKILL.md 文件
     *
     * @param skillMdPath SKILL.md 文件路径（宿主机路径）
     * @param source      来源标记（"user" 或 "project"）
     * @return 解析后的 Skill
     * @throws SkillLoadException 解析失败
     */
    public Skill parseInSandbox(Path skillMdPath, String source) {
        if (!isAvailable()) {
            throw new SkillLoadException("Sandbox not available for skill parsing: " + skillMdPath);
        }

        try {
            // 1. 读取文件内容（宿主机）
            String content = Files.readString(skillMdPath, StandardCharsets.UTF_8);

            // 2. 使用 base64 编码内容，避免 shell 注入问题
            String base64Content = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));

            // 3. 构建沙箱解析脚本
            String parseScript = buildParseScript(base64Content);

            // 4. 在沙箱中执行解析
            var result = sandboxClient.execute(
                List.of("sh", "-c", parseScript),
                ResourceLimits.defaults()
            );

            if (result.isTimeout()) {
                throw new SkillLoadException("Skill parsing timed out in sandbox: " + skillMdPath);
            }

            if (result.getExitCode() != 0) {
                throw new SkillLoadException(
                    "Skill parsing failed in sandbox: " + skillMdPath +
                    "\nstderr: " + result.getStderr()
                );
            }

            // 5. 解析沙箱返回的 JSON 结果
            return parseSandboxResult(result.getStdout(), skillMdPath, source);

        } catch (IOException e) {
            throw new SkillLoadException("Failed to read skill file: " + skillMdPath, e);
        }
    }

    /**
     * 构建沙箱内的解析脚本 (POSIX sh 兼容，支持 busybox ash/dash)
     * 使用临时文件传递变量，避免子 shell 问题
     */
    private String buildParseScript(String base64Content) {
        return """
            # 解码 base64 内容
            CONTENT=$(echo '%s' | base64 -d)

            # 提取 frontmatter
            case "$CONTENT" in
                ---*) ;;
                *)
                    echo '{"name":"unnamed-skill","description":"","disableModelInvocation":false}'
                    exit 0
                    ;;
            esac

            # 提取 --- 之间的 YAML (排除第一个和最后一个 ---)
            YAML=$(echo "$CONTENT" | awk '/^---/{if(seen){exit}seen=1;next}/^---/{exit}1')

            # 解析字段 - 直接从 YAML 提取
            name_val=""
            desc_val=""
            disable_val="false"

            # 使用 grep 和 sed 提取 (POSIX 兼容)
            name_line=$(echo "$YAML" | grep "^[[:space:]]*name:")
            if [ -n "$name_line" ]; then
                name_val=$(echo "$name_line" | sed 's/^[[:space:]]*name:[[:space:]]*//;s/^[[:space:]]*//;s/[[:space:]]*$//')
            fi

            desc_line=$(echo "$YAML" | grep "^[[:space:]]*description:")
            if [ -n "$desc_line" ]; then
                desc_val=$(echo "$desc_line" | sed 's/^[[:space:]]*description:[[:space:]]*//;s/^[[:space:]]*//;s/[[:space:]]*$//')
            fi

            disable_line=$(echo "$YAML" | grep "^[[:space:]]*disable-model-invocation:")
            if [ -n "$disable_line" ]; then
                disable_raw=$(echo "$disable_line" | sed 's/^[[:space:]]*disable-model-invocation:[[:space:]]*//;s/^[[:space:]]*//;s/[[:space:]]*$//')
                disable_val=$(echo "$disable_raw" | tr '[:upper:]' '[:lower:]')
            fi

            # 默认值
            if [ -z "$name_val" ]; then
                name_val="unnamed-skill"
            fi

            # 转义 JSON 字符串 (简单转义双引号)
            name_escaped=$(echo "$name_val" | sed 's/"/\\\\"/g')
            desc_escaped=$(echo "$desc_val" | sed 's/"/\\\\"/g')

            # 布尔值
            if [ "$disable_val" = "true" ]; then
                disable_bool="true"
            else
                disable_bool="false"
            fi

            # 输出 JSON
            printf '{"name":"%%s","description":"%%s","disableModelInvocation":%%s}' "$name_escaped" "$desc_escaped" "$disable_bool"
            """.formatted(base64Content);
    }

    /**
     * 解析沙箱返回的 JSON 结果
     */
    private Skill parseSandboxResult(String json, Path filePath, String source) {
        // 简单 JSON 解析（避免引入 JSON 库依赖）
        try {
            String name = extractJsonField(json, "name");
            String description = extractJsonField(json, "description");
            String disableStr = extractJsonField(json, "disableModelInvocation");
            boolean disableModelInvocation = "true".equals(disableStr);

            // 验证 name
            if (name == null || name.isEmpty()) {
                Path parentDir = filePath.getParent();
                name = parentDir != null ? parentDir.getFileName().toString() : "unknown";
            }
            SkillLoader.validateName(name, filePath);

            // 验证 description
            if (description == null || description.isBlank()) {
                throw new SkillLoadException("Skill description is required: " + filePath);
            }
            if (description.length() > Skill.MAX_DESCRIPTION_LENGTH) {
                throw new SkillLoadException(
                    "Skill description exceeds " + Skill.MAX_DESCRIPTION_LENGTH + " characters: " + filePath);
            }

            return new Skill(
                name,
                description,
                filePath,
                filePath.getParent(),
                source,
                disableModelInvocation
            );
        } catch (Exception e) {
            throw new SkillLoadException("Failed to parse sandbox result: " + json, e);
        }
    }

    /**
     * 简单的 JSON 字段提取（不引入额外依赖）
     */
    private String extractJsonField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":\\s*\"([^\"]*)\"";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }

        // 处理布尔值
        String boolPattern = "\"" + fieldName + "\":\\s*(true|false)";
        java.util.regex.Pattern br = java.util.regex.Pattern.compile(boolPattern);
        java.util.regex.Matcher bm = br.matcher(json);
        if (bm.find()) {
            return bm.group(1);
        }

        return null;
    }

    /**
     * 在沙箱中加载 SKILL.md 的 body 内容（去掉 frontmatter 后的 Markdown 部分）
     *
     * @param skillMdPath SKILL.md 文件路径（宿主机路径）
     * @return body 内容
     * @throws SkillLoadException 加载失败
     */
    public String loadBodyInSandbox(Path skillMdPath) {
        if (!isAvailable()) {
            throw new SkillLoadException("Sandbox not available for loading skill body: " + skillMdPath);
        }

        try {
            // 1. 读取文件内容（宿主机）
            String content = Files.readString(skillMdPath, StandardCharsets.UTF_8);

            // 2. 使用 base64 编码内容
            String base64Content = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));

            // 3. 构建沙箱脚本：提取 body 部分 (POSIX sh 兼容)
            String extractScript = """
                # 解码 base64 内容
                CONTENT=$(echo '%s' | base64 -d)

                # 提取 body（去掉 frontmatter）- POSIX sh 兼容
                # 策略：删除从开头到第二个 --- 的所有内容
                strip_frontmatter() {
                    _content="$1"
                    case "$_content" in
                        ---*)
                            ;;
                        *)
                            echo "$_content"
                            return
                            ;;
                    esac

                    # 使用 awk 删除第一个 --- 到第二个 --- 之间的所有内容（包括这两个标记）
                    # 保留第二个 --- 之后的内容
                    echo "$_content" | awk '
                        /^---$/ {
                            if (count == 0) {
                                count = 1
                                next
                            } else if (count == 1) {
                                count = 2
                                next
                            }
                        }
                        count == 2 { print }
                    '
                }

                strip_frontmatter "$CONTENT"
                """.formatted(base64Content);

            // 4. 在沙箱中执行
            var result = sandboxClient.execute(
                List.of("sh", "-c", extractScript),
                ResourceLimits.defaults()
            );

            if (result.isTimeout()) {
                throw new SkillLoadException("Skill body loading timed out in sandbox: " + skillMdPath);
            }

            if (result.getExitCode() != 0) {
                throw new SkillLoadException(
                    "Skill body loading failed in sandbox: " + skillMdPath +
                    "\nstderr: " + result.getStderr()
                );
            }

            return result.getStdout();

        } catch (IOException e) {
            throw new SkillLoadException("Failed to read skill file for body extraction: " + skillMdPath, e);
        }
    }

    /**
     * 在沙箱中验证 skill 内容安全性
     *
     * @param skillMdPath SKILL.md 文件路径
     * @return 验证结果，空字符串表示通过，否则返回错误信息
     */
    public String validateSkillInSandbox(Path skillMdPath) {
        if (!isAvailable()) {
            return "Sandbox not available";
        }

        try {
            String content = Files.readString(skillMdPath, StandardCharsets.UTF_8);
            String base64Content = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));

            String validateScript = """
                CONTENT=$(echo '%s' | base64 -d)

                # 检查文件大小（最大 1MB）
                SIZE=$(echo "$CONTENT" | wc -c)
                if [ $SIZE -gt 1048576 ]; then
                    echo "ERROR: Skill file too large (max 1MB)"
                    exit 0
                fi

                # 检查是否包含危险路径
                if echo "$CONTENT" | grep -qE '(\\.\\./|/etc/passwd|/root/|/sys/|/proc/)'; then
                    echo "ERROR: Skill contains dangerous path references"
                    exit 0
                fi

                # 检查 frontmatter 是否完整 (POSIX sh 兼容)
                case "$CONTENT" in
                    ---*) ;;
                    *) echo "WARNING: No YAML frontmatter found" ;;
                esac

                echo "VALIDATION_PASSED"
                """.formatted(base64Content);

            var result = sandboxClient.execute(
                List.of("sh", "-c", validateScript),
                ResourceLimits.defaults()
            );

            if (result.isTimeout()) {
                return "Validation timed out";
            }

            String output = result.getStdout().trim();
            if (output.contains("ERROR:")) {
                return output;
            }

            return ""; // 验证通过

        } catch (IOException e) {
            return "Failed to read skill file: " + e.getMessage();
        }
    }
}
