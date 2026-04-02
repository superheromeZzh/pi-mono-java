package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import java.util.ArrayList;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction.Compactor;

public class CompactCommand implements SlashCommand {

    private final Compactor compactor;

    public CompactCommand(Compactor compactor) {
        this.compactor = compactor;
    }

    @Override
    public String name() {
        return "compact";
    }

    @Override
    public String description() {
        return "Compact session context";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var session = context.session();
        var agent = session.getAgent();
        var model = agent.getState().getModel();
        var messages = session.getHistory();

        if (messages.isEmpty()) {
            context.output().println("No messages to compact.");
            return;
        }

        context.output().println("Compacting context...");
        try {
            var result = compactor.compact(new ArrayList<>(messages), model);
            // Build new message list with summary
            var newMessages = new ArrayList<Message>();
            if (!result.summary().isEmpty()) {
                newMessages.add(new UserMessage(
                        "[Context compaction summary]\n" + result.summary(),
                        System.currentTimeMillis()));
            }
            newMessages.addAll(result.retainedMessages());
            agent.replaceMessages(newMessages);

            int removed = messages.size() - result.retainedMessages().size();
            context.output().println("Compacted " + removed + " messages into summary, "
                    + result.retainedMessages().size() + " recent messages kept.");
        } catch (Exception e) {
            context.output().println("Compaction failed: " + e.getMessage());
        }
    }
}
