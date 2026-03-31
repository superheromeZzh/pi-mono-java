package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;
import com.campusclaw.codingagent.util.ClipboardUtils;

public class CopyCommand implements SlashCommand {

    @Override
    public String name() {
        return "copy";
    }

    @Override
    public String description() {
        return "Copy last reply to clipboard";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var messages = context.session().getHistory();
        // Find last assistant message
        String lastReply = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage am) {
                var sb = new StringBuilder();
                for (ContentBlock cb : am.content()) {
                    if (cb instanceof TextContent tc) {
                        sb.append(tc.text());
                    }
                }
                lastReply = sb.toString();
                break;
            }
        }

        if (lastReply == null || lastReply.isEmpty()) {
            context.output().println("No assistant reply to copy.");
            return;
        }

        if (ClipboardUtils.copy(lastReply)) {
            context.output().println("Copied to clipboard.");
        } else {
            context.output().println("Failed to copy to clipboard.");
        }
    }
}
