package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-file mutation queue that serializes write operations on the same file
 * while allowing concurrent operations on different files.
 */
public class FileMutationQueue {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Executes the given action while holding an exclusive lock for the specified file path.
     * Operations on the same normalized path are serialized; operations on different paths
     * may proceed concurrently.
     *
     * @param filePath the file path to lock on
     * @param action   the action to execute under the lock
     * @param <T>      the return type
     * @return the result of the action
     * @throws Exception if the action throws
     */
    public <T> T withLock(Path filePath, Callable<T> action) throws Exception {
        String key = filePath.toAbsolutePath().normalize().toString();
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.call();
        } finally {
            lock.unlock();
        }
    }
}
