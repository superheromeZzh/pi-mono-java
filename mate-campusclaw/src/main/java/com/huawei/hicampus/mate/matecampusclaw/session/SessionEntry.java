package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * A single entry in the session log. Entries form a tree via parentId references.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionEntry(
    @JsonProperty("id") String id,
    @JsonProperty("parentId") @Nullable String parentId,
    @JsonProperty("type") String type,
    @JsonProperty("message") @Nullable Message message,
    @JsonProperty("summary") @Nullable String summary,
    @JsonProperty("branchName") @Nullable String branchName,
    @JsonProperty("timestamp") long timestamp
) {
    public static SessionEntry message(String id, @Nullable String parentId, Message message) {
        return new SessionEntry(id, parentId, "message", message, null, null, System.currentTimeMillis());
    }

    public static SessionEntry compaction(String id, @Nullable String parentId, String summary) {
        return new SessionEntry(id, parentId, "compaction", null, summary, null, System.currentTimeMillis());
    }

    public static SessionEntry branchSummary(String id, @Nullable String parentId, String branchName, String summary) {
        return new SessionEntry(id, parentId, "branch_summary", null, summary, branchName, System.currentTimeMillis());
    }
}
