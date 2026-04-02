package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.huawei.hicampus.mate.matecampusclaw.agent.Agent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEventListener;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.context.ContextFileLoader;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.context.ContextFileLoader.ContextFile;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.PromptTemplate;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.PromptTemplateEntry;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.PromptTemplateLoader;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.SystemPromptBuilder;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.SystemPromptConfig;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.Skill;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.SkillExpander;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.SkillLoader;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.SkillRegistry;

/**
 * Manages a single agent session lifecycle: initialization, prompt handling,
 * skill expansion, and history access.
 *
 * <p>Coordinates {@link Agent}, {@link ModelRegistry}, {@link SkillLoader},
 * {@link SystemPromptBuilder}, and {@link SkillExpander} to provide a cohesive
 * session abstraction for the coding agent CLI.
 */
public class AgentSession {

    static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    static final Path USER_AGENT_DIR = AppPaths.USER_AGENT_DIR;
    static final Path USER_SKILLS_DIR = AppPaths.USER_SKILLS_DIR;
    static final String PROJECT_SKILLS_SUBDIR = AppPaths.PROJECT_SKILLS_SUBDIR;

    private final CampusClawAiService piAiService;
    private final ModelRegistry modelRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final SkillLoader skillLoader;
    private final SkillExpander skillExpander;
    private final List<AgentTool> tools;
    private final ContextFileLoader contextFileLoader;
    private final PromptTemplateLoader promptTemplateLoader;
    private SessionManager sessionManager;

    private final SkillRegistry skillRegistry = new SkillRegistry();
    private List<PromptTemplateEntry> promptTemplates = List.of();
    private Agent agent;
    private boolean initialized;

    public AgentSession(
            CampusClawAiService piAiService,
            ModelRegistry modelRegistry,
            SystemPromptBuilder promptBuilder,
            SkillLoader skillLoader,
            SkillExpander skillExpander,
            List<AgentTool> tools
    ) {
        this.piAiService = Objects.requireNonNull(piAiService, "piAiService");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.skillLoader = Objects.requireNonNull(skillLoader, "skillLoader");
        this.skillExpander = Objects.requireNonNull(skillExpander, "skillExpander");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.contextFileLoader = new ContextFileLoader();
        this.promptTemplateLoader = new PromptTemplateLoader();
    }

    /**
     * Initializes the session: resolves the model, loads skills, builds the
     * system prompt, registers tools, and configures the agent.
     *
     * @param config the session configuration
     * @throws IllegalStateException    if the session is already initialized
     * @throws IllegalArgumentException if the model cannot be resolved
     */
    public void initialize(SessionConfig config) {
        Objects.requireNonNull(config, "config");
        if (initialized) {
            throw new IllegalStateException("Session is already initialized");
        }

        // 1. Resolve model
        String modelId = config.model() != null ? config.model() : DEFAULT_MODEL;
        Model model = resolveModel(modelId);

        // 2. Load skills (user-level + project-level)
        Path cwd = config.cwd() != null ? config.cwd() : Path.of(System.getProperty("user.dir"));
        loadSkills(cwd);

        // 3. Load context files (AGENTS.md / CLAUDE.md)
        List<ContextFile> contextFiles = contextFileLoader.loadProjectContextFiles(cwd, USER_AGENT_DIR);

        // 4. Discover SYSTEM.md and APPEND_SYSTEM.md
        String systemPromptOverride = contextFileLoader.loadSystemPrompt(cwd, USER_AGENT_DIR);
        String appendSystemPrompt = contextFileLoader.loadAppendSystemPrompt(cwd, USER_AGENT_DIR);

        // 5. Load prompt templates
        promptTemplates = promptTemplateLoader.load(cwd, USER_AGENT_DIR);

        // 6. Build system prompt
        List<Skill> visibleSkills = skillRegistry.getVisibleSkills();
        Map<String, String> env = buildEnvironmentMap();

        SystemPromptConfig promptConfig = new SystemPromptConfig(
                tools, visibleSkills, cwd, config.customPrompt(), env,
                contextFiles, systemPromptOverride, appendSystemPrompt
        );
        String systemPrompt = promptBuilder.build(promptConfig);

        // 7. Create and configure Agent
        agent = createAgent(piAiService);
        agent.setModel(model);
        agent.setSystemPrompt(systemPrompt);
        agent.setTools(tools);

        initialized = true;
    }

    /**
     * Sends a user prompt to the agent. Expands {@code /skill:name} commands
     * before forwarding to the underlying agent.
     *
     * @param userInput the raw user input
     * @return a future that completes when the agent finishes processing
     * @throws IllegalStateException if the session is not initialized
     */
    public CompletableFuture<Void> prompt(String userInput) {
        requireInitialized();
        Objects.requireNonNull(userInput, "userInput");

        // Expand prompt templates first (/templatename args...)
        String expanded = expandPromptTemplate(userInput);
        // Then expand skill commands (/skill:name args...)
        expanded = skillExpander.expand(expanded, skillRegistry);
        return agent.prompt(expanded);
    }

    /**
     * Aborts the current agent execution.
     *
     * @throws IllegalStateException if the session is not initialized
     */
    public void abort() {
        requireInitialized();
        agent.abort();
    }

    /**
     * Returns the conversation history.
     *
     * @return an unmodifiable list of messages
     * @throws IllegalStateException if the session is not initialized
     */
    public List<Message> getHistory() {
        requireInitialized();
        return agent.getState().getMessages();
    }

    /**
     * Returns the underlying agent instance.
     *
     * @return the configured agent
     * @throws IllegalStateException if the session is not initialized
     */
    public Agent getAgent() {
        requireInitialized();
        return agent;
    }

    /**
     * Returns the skill registry for this session.
     */
    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    /**
     * Returns whether this session has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Subscribes to agent events. Delegates to the underlying {@link Agent}.
     *
     * @param listener the event listener
     * @return a runnable that removes the listener when called
     * @throws IllegalStateException if the session is not initialized
     */
    public Runnable subscribe(AgentEventListener listener) {
        requireInitialized();
        return agent.subscribe(listener);
    }

    /**
     * Steers the running agent with an additional user message.
     *
     * @param message the steering message text
     * @throws IllegalStateException if the session is not initialized
     */
    public void steer(String message) {
        requireInitialized();
        Objects.requireNonNull(message, "message");
        agent.steer(new UserMessage(message, System.currentTimeMillis()));
    }

    /**
     * Returns the current model ID, or {@code "unknown"} if no model is set.
     *
     * @throws IllegalStateException if the session is not initialized
     */
    public String getModelId() {
        requireInitialized();
        var model = agent.getState().getModel();
        return model != null ? model.id() : "unknown";
    }

    /**
     * Returns whether the agent is currently streaming a response.
     *
     * @throws IllegalStateException if the session is not initialized
     */
    public boolean isStreaming() {
        requireInitialized();
        return agent.getState().isStreaming();
    }

    /**
     * Changes the model used by the agent. Resolves the model ID via the
     * registry and updates the underlying agent.
     *
     * @param modelId the model identifier to switch to
     * @throws IllegalStateException    if the session is not initialized
     * @throws IllegalArgumentException if the model cannot be resolved
     */
    public void setModel(String modelId) {
        requireInitialized();
        Objects.requireNonNull(modelId, "modelId");
        Model model = resolveModel(modelId);
        agent.setModel(model);
    }

    /**
     * Resets the agent state to start a fresh conversation while keeping the
     * same session configuration (model, tools, system prompt).
     *
     * @throws IllegalStateException if the session is not initialized
     */
    public void newSession() {
        requireInitialized();
        agent.clearMessages();
    }

    /**
     * Returns the loaded prompt templates for this session.
     */
    public List<PromptTemplateEntry> getPromptTemplates() {
        return promptTemplates;
    }

    /**
     * Sets the session manager for persistence. Must be called before initialize().
     */
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Returns the session manager, or null if not set.
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Reloads skills, prompt templates, context files, and rebuilds the system prompt.
     * Called by the /reload command.
     *
     * @param customPrompt the user-supplied custom prompt (may be null)
     */
    public void reload(String customPrompt) {
        requireInitialized();
        Path cwd = Path.of(System.getProperty("user.dir"));

        // Reload skills
        loadSkills(cwd);

        // Reload prompt templates
        promptTemplates = promptTemplateLoader.load(cwd, USER_AGENT_DIR);

        // Reload context files and rebuild system prompt
        List<ContextFile> contextFiles = contextFileLoader.loadProjectContextFiles(cwd, USER_AGENT_DIR);
        String systemPromptOverride = contextFileLoader.loadSystemPrompt(cwd, USER_AGENT_DIR);
        String appendSystemPrompt = contextFileLoader.loadAppendSystemPrompt(cwd, USER_AGENT_DIR);

        List<Skill> visibleSkills = skillRegistry.getVisibleSkills();
        Map<String, String> env = buildEnvironmentMap();

        SystemPromptConfig promptConfig = new SystemPromptConfig(
                tools, visibleSkills, cwd, customPrompt, env,
                contextFiles, systemPromptOverride, appendSystemPrompt
        );
        String systemPrompt = promptBuilder.build(promptConfig);
        agent.setSystemPrompt(systemPrompt);
    }

    /** Overload for backward compatibility. */
    public void reload() {
        reload(null);
    }

    /**
     * Expands a prompt template command like "/templatename arg1 arg2".
     */
    String expandPromptTemplate(String input) {
        if (!input.startsWith("/")) return input;

        int spaceIdx = input.indexOf(' ');
        String name = spaceIdx >= 0 ? input.substring(1, spaceIdx) : input.substring(1);
        String argsStr = spaceIdx >= 0 ? input.substring(spaceIdx + 1) : "";

        for (PromptTemplateEntry template : promptTemplates) {
            if (template.name().equals(name)) {
                List<String> args = parseCommandArgs(argsStr);
                return PromptTemplate.expand(template.content(), args);
            }
        }
        return input;
    }

    static List<String> parseCommandArgs(String argsString) {
        List<String> args = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        Character inQuote = null;

        for (int i = 0; i < argsString.length(); i++) {
            char ch = argsString.charAt(i);
            if (inQuote != null) {
                if (ch == inQuote) {
                    inQuote = null;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                inQuote = ch;
            } else if (ch == ' ' || ch == '\t') {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return args;
    }

    // -- package-private for testing --

    Agent createAgent(CampusClawAiService aiService) {
        return new Agent(aiService);
    }

    /**
     * Resolves a model from a pattern string.
     * Supports:
     * - Exact ID: "glm-5"
     * - Provider/ID: "zai/glm-5"
     * - Fuzzy substring: "sonnet" matches "claude-sonnet-4-20250514"
     * - Thinking suffix: "sonnet:high" (thinking level is stripped, not applied here)
     */
    Model resolveModel(String modelId) {
        // Strip thinking level suffix (e.g., "sonnet:high" → "sonnet")
        int colonIdx = modelId.indexOf(':');
        String pattern = colonIdx >= 0 ? modelId.substring(0, colonIdx) : modelId;

        // Check for provider/id prefix
        Provider targetProvider = null;
        if (pattern.contains("/")) {
            String[] parts = pattern.split("/", 2);
            String providerStr = parts[0].toLowerCase();
            pattern = parts[1];
            for (Provider p : modelRegistry.getProviders()) {
                if (p.value().toLowerCase().contains(providerStr)) {
                    targetProvider = p;
                    break;
                }
            }
        }

        // 1. Exact match
        for (Provider provider : modelRegistry.getProviders()) {
            if (targetProvider != null && provider != targetProvider) continue;
            var model = modelRegistry.getModel(provider, pattern);
            if (model.isPresent()) return model.get();
        }

        // 2. Fuzzy substring match on id and name
        String lowerPattern = pattern.toLowerCase();
        Model bestMatch = null;
        for (Provider provider : modelRegistry.getProviders()) {
            if (targetProvider != null && provider != targetProvider) continue;
            for (Model m : modelRegistry.getModels(provider)) {
                if (m.id().toLowerCase().contains(lowerPattern)
                        || m.name().toLowerCase().contains(lowerPattern)) {
                    if (bestMatch == null || m.id().length() < bestMatch.id().length()) {
                        bestMatch = m; // Prefer shorter ID (more specific match)
                    }
                }
            }
        }
        if (bestMatch != null) return bestMatch;

        throw new IllegalArgumentException("Unknown model: " + modelId
                + ". Use --list-models to see available models.");
    }

    void loadSkills(Path cwd) {
        skillRegistry.clear();

        // User-level skills: ~/.campusclaw/agent/skills/
        List<Skill> userSkills = skillLoader.loadFromDirectory(USER_SKILLS_DIR, "user");
        skillRegistry.registerAll(userSkills);

        // Project-level skills: {cwd}/.campusclaw/skills/
        Path projectSkillsDir = cwd.resolve(PROJECT_SKILLS_SUBDIR);
        List<Skill> projectSkills = skillLoader.loadFromDirectory(projectSkillsDir, "project");
        skillRegistry.registerAll(projectSkills);
    }

    static Map<String, String> buildEnvironmentMap() {
        Map<String, String> env = new HashMap<>();
        env.put("OS_NAME", System.getProperty("os.name", "unknown"));
        env.put("JAVA_VERSION", System.getProperty("java.version", "unknown"));
        return env;
    }

    private void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Session is not initialized. Call initialize() first.");
        }
    }
}
