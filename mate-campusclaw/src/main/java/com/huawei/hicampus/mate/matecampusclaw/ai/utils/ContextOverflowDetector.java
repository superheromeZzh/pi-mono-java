package com.huawei.hicampus.mate.matecampusclaw.ai.utils;

import java.util.List;
import java.util.regex.Pattern;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;

/**
 * Detects context overflow errors from LLM provider responses.
 * Aligned with TypeScript campusclaw utils/overflow.ts.
 *
 * <p>Handles two cases:
 * <ol>
 *   <li>Error-based overflow: provider returns error with recognizable message pattern</li>
 *   <li>Silent overflow: provider accepts the request but usage exceeds context window (z.ai)</li>
 * </ol>
 */
public final class ContextOverflowDetector {

    private ContextOverflowDetector() {}

    private static final List<Pattern> OVERFLOW_PATTERNS = List.of(
        Pattern.compile("prompt is too long", Pattern.CASE_INSENSITIVE),                          // Anthropic
        Pattern.compile("input is too long for requested model", Pattern.CASE_INSENSITIVE),       // Bedrock
        Pattern.compile("exceeds the context window", Pattern.CASE_INSENSITIVE),                  // OpenAI
        Pattern.compile("input token count.*exceeds the maximum", Pattern.CASE_INSENSITIVE),      // Google
        Pattern.compile("maximum prompt length is \\d+", Pattern.CASE_INSENSITIVE),               // xAI
        Pattern.compile("reduce the length of the messages", Pattern.CASE_INSENSITIVE),           // Groq
        Pattern.compile("maximum context length is \\d+ tokens", Pattern.CASE_INSENSITIVE),       // OpenRouter
        Pattern.compile("exceeds the limit of \\d+", Pattern.CASE_INSENSITIVE),                   // Copilot
        Pattern.compile("exceeds the available context size", Pattern.CASE_INSENSITIVE),          // llama.cpp
        Pattern.compile("greater than the context length", Pattern.CASE_INSENSITIVE),             // LM Studio
        Pattern.compile("context window exceeds limit", Pattern.CASE_INSENSITIVE),                // MiniMax
        Pattern.compile("exceeded model token limit", Pattern.CASE_INSENSITIVE),                  // Kimi
        Pattern.compile("too large for model with \\d+ maximum context length", Pattern.CASE_INSENSITIVE), // Mistral
        Pattern.compile("model_context_window_exceeded", Pattern.CASE_INSENSITIVE),               // z.ai
        Pattern.compile("prompt too long; exceeded (?:max )?context length", Pattern.CASE_INSENSITIVE), // Ollama
        Pattern.compile("context[_ ]length[_ ]exceeded", Pattern.CASE_INSENSITIVE),              // Generic
        Pattern.compile("too many tokens", Pattern.CASE_INSENSITIVE),                             // Generic
        Pattern.compile("token limit exceeded", Pattern.CASE_INSENSITIVE)                         // Generic
    );

    private static final Pattern CEREBRAS_PATTERN =
        Pattern.compile("^4(00|13)\\s*(status code)?\\s*\\(no body\\)", Pattern.CASE_INSENSITIVE);

    /**
     * Check if an assistant message represents a context overflow error.
     *
     * @param message       the assistant message to check
     * @param contextWindow optional context window size for silent overflow detection (0 to skip)
     * @return true if the message indicates context overflow
     */
    public static boolean isContextOverflow(AssistantMessage message, int contextWindow) {
        // Case 1: Error message patterns
        if (message.stopReason() == StopReason.ERROR && message.errorMessage() != null) {
            String error = message.errorMessage();
            for (Pattern p : OVERFLOW_PATTERNS) {
                if (p.matcher(error).find()) return true;
            }
            // Cerebras: 400/413 with no body
            if (CEREBRAS_PATTERN.matcher(error).find()) return true;
        }

        // Case 2: Silent overflow (z.ai style)
        if (contextWindow > 0 && message.stopReason() == StopReason.STOP) {
            int inputTokens = message.usage().input() + message.usage().cacheRead();
            if (inputTokens > contextWindow) return true;
        }

        return false;
    }

    /**
     * Check if an assistant message represents a context overflow error.
     * Does not check for silent overflow.
     */
    public static boolean isContextOverflow(AssistantMessage message) {
        return isContextOverflow(message, 0);
    }
}
