package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import java.util.*;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * In-memory representation of a session as a tree of entries.
 * Supports forking (branching) and navigating between branches.
 */
public class SessionTree {
    private final List<SessionEntry> entries = new ArrayList<>();
    private final Map<String, SessionEntry> entriesById = new LinkedHashMap<>();
    private final Map<String, List<String>> childrenMap = new LinkedHashMap<>();
    private String currentEntryId = null;

    /** Add an entry to the tree. */
    public void addEntry(SessionEntry entry) {
        entries.add(entry);
        entriesById.put(entry.id(), entry);
        if (entry.parentId() != null) {
            childrenMap.computeIfAbsent(entry.parentId(), k -> new ArrayList<>()).add(entry.id());
        }
        currentEntryId = entry.id();
    }

    /** Get the current branch (path from root to current entry). */
    public List<SessionEntry> getCurrentBranch() {
        if (currentEntryId == null) return List.of();
        List<SessionEntry> branch = new ArrayList<>();
        String id = currentEntryId;
        while (id != null) {
            SessionEntry entry = entriesById.get(id);
            if (entry == null) break;
            branch.add(0, entry);
            id = entry.parentId();
        }
        return branch;
    }

    /** Get messages from the current branch. */
    public List<Message> getCurrentMessages() {
        List<Message> messages = new ArrayList<>();
        for (SessionEntry entry : getCurrentBranch()) {
            if ("message".equals(entry.type()) && entry.message() != null) {
                messages.add(entry.message());
            }
        }
        return messages;
    }

    /** Fork at the given entry ID, creating a new branch point. */
    public String fork(String atEntryId) {
        if (!entriesById.containsKey(atEntryId)) {
            throw new IllegalArgumentException("Entry not found: " + atEntryId);
        }
        currentEntryId = atEntryId;
        return atEntryId;
    }

    /** Switch to a different entry (navigating the tree). */
    public void switchTo(String entryId) {
        if (!entriesById.containsKey(entryId)) {
            throw new IllegalArgumentException("Entry not found: " + entryId);
        }
        currentEntryId = entryId;
    }

    /** Get all leaf entries (branch tips). */
    public List<SessionEntry> getLeaves() {
        Set<String> parents = new HashSet<>();
        for (SessionEntry e : entries) {
            if (e.parentId() != null) parents.add(e.parentId());
        }
        List<SessionEntry> leaves = new ArrayList<>();
        for (SessionEntry e : entries) {
            if (!parents.contains(e.id())) {
                leaves.add(e);
            }
        }
        return leaves;
    }

    /** Get children of an entry. */
    public List<SessionEntry> getChildren(String entryId) {
        List<String> childIds = childrenMap.getOrDefault(entryId, List.of());
        List<SessionEntry> children = new ArrayList<>();
        for (String id : childIds) {
            SessionEntry e = entriesById.get(id);
            if (e != null) children.add(e);
        }
        return children;
    }

    /** Get all entries in order. */
    public List<SessionEntry> getAllEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** Get current entry ID. */
    public String getCurrentEntryId() {
        return currentEntryId;
    }

    /** Get entry by ID. */
    public Optional<SessionEntry> getEntry(String id) {
        return Optional.ofNullable(entriesById.get(id));
    }

    /** Generate a unique entry ID. */
    public static String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Get number of entries. */
    public int size() {
        return entries.size();
    }

    /** Check if tree is empty. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
