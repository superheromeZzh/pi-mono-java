package com.huawei.hicampus.mate.matecampusclaw.ai.env;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;

import org.springframework.stereotype.Service;

/**
 * Resolves API keys from environment variables for each provider.
 * Follows the same mapping as the TypeScript env-api-keys.ts.
 */
@Service
public class EnvApiKeyResolver {

    private static final String AUTHENTICATED = "<authenticated>";

    /**
     * Resolves an API key for the given provider from environment variables.
     *
     * @param provider the LLM provider
     * @return the API key if found, or empty
     */
    public Optional<String> resolve(Provider provider) {
        return switch (provider) {
            case ANTHROPIC -> firstEnv("ANTHROPIC_API_KEY", "ANTHROPIC_OAUTH_TOKEN");
            case OPENAI -> firstEnv("OPENAI_API_KEY");
            case GOOGLE -> firstEnv("GOOGLE_API_KEY", "GOOGLE_CLOUD_API_KEY")
                    .or(this::detectGoogleADC);
            case GOOGLE_VERTEX -> firstEnv("GOOGLE_CLOUD_API_KEY")
                    .or(this::detectGoogleADC);
            case AMAZON_BEDROCK -> detectBedrockCredentials();
            case AZURE_OPENAI -> firstEnv("AZURE_OPENAI_API_KEY");
            case MISTRAL -> firstEnv("MISTRAL_API_KEY");
            case OPENAI_CODEX -> firstEnv("OPENAI_API_KEY");
            case ZAI -> firstEnv("ZAI_API_KEY");
            case KIMI_CODING -> firstEnv("KIMI_API_KEY");
            case MINIMAX -> firstEnv("MINIMAX_API_KEY");
            case MINIMAX_CN -> firstEnv("MINIMAX_CN_API_KEY");
            case XAI -> firstEnv("XAI_API_KEY");
            case GROQ -> firstEnv("GROQ_API_KEY");
            case CEREBRAS -> firstEnv("CEREBRAS_API_KEY");
            case OPENROUTER -> firstEnv("OPENROUTER_API_KEY");
            case VERCEL_AI_GATEWAY -> firstEnv("AI_GATEWAY_API_KEY");
            case HUGGINGFACE -> firstEnv("HF_TOKEN");
            case GITHUB_COPILOT -> firstEnv("COPILOT_GITHUB_TOKEN", "GH_TOKEN", "GITHUB_TOKEN");
            case GOOGLE_GEMINI_CLI, GOOGLE_ANTIGRAVITY -> firstEnv("GOOGLE_API_KEY");
            case OPENCODE -> firstEnv("OPENCODE_API_KEY");
            default -> Optional.empty();
        };
    }

    /**
     * Returns the first non-null, non-blank value from the given env var names.
     */
    private Optional<String> firstEnv(String... names) {
        for (String name : names) {
            String val = System.getenv(name);
            if (val != null && !val.isBlank()) {
                return Optional.of(val);
            }
        }
        return Optional.empty();
    }

    /**
     * Detects Google Application Default Credentials.
     * Returns AUTHENTICATED sentinel if gcloud credentials exist.
     */
    private Optional<String> detectGoogleADC() {
        // Check GOOGLE_APPLICATION_CREDENTIALS
        String creds = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (creds != null && !creds.isBlank()) {
            return Optional.of(AUTHENTICATED);
        }
        // Check for gcloud default credentials file
        String home = System.getProperty("user.home");
        if (home != null) {
            Path adcPath = Path.of(home, ".config", "gcloud", "application_default_credentials.json");
            if (Files.exists(adcPath)) {
                return Optional.of(AUTHENTICATED);
            }
        }
        return Optional.empty();
    }

    /**
     * Detects AWS Bedrock credentials from various sources.
     */
    private Optional<String> detectBedrockCredentials() {
        // AWS_PROFILE
        if (envSet("AWS_PROFILE")) return Optional.of(AUTHENTICATED);
        // IAM keys
        if (envSet("AWS_ACCESS_KEY_ID") && envSet("AWS_SECRET_ACCESS_KEY"))
            return Optional.of(AUTHENTICATED);
        // Bearer token
        if (envSet("AWS_BEARER_TOKEN_BEDROCK")) return Optional.of(AUTHENTICATED);
        // Container credentials
        if (envSet("AWS_CONTAINER_CREDENTIALS_FULL_URI") || envSet("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI"))
            return Optional.of(AUTHENTICATED);
        // Web identity (IRSA)
        if (envSet("AWS_WEB_IDENTITY_TOKEN_FILE")) return Optional.of(AUTHENTICATED);
        return Optional.empty();
    }

    private boolean envSet(String name) {
        String val = System.getenv(name);
        return val != null && !val.isBlank();
    }
}
