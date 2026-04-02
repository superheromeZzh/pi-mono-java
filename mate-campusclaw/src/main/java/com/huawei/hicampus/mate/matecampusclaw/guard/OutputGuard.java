package com.huawei.hicampus.mate.matecampusclaw.codingagent.guard;

import java.io.OutputStream;
import java.io.PrintStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Output guard for RPC mode: intercepts and redirects stdout/stderr
 * to prevent tool output from corrupting the JSONL protocol stream.
 *
 * <p>In RPC mode, stdout is reserved for the JSONL protocol. Any stray
 * output from tools, libraries, or System.out.println() calls would
 * break the protocol. OutputGuard captures these and redirects them
 * to stderr or a log file.
 */
public class OutputGuard implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutputGuard.class);

    private final PrintStream originalStdout;
    private final PrintStream originalStderr;
    private final PrintStream guardedStdout;
    private volatile boolean active;

    /**
     * Creates and activates the output guard.
     * Redirects System.out to a guarded stream that logs unexpected output.
     *
     * @param protocolStream the stream to use for protocol output (usually the real stdout)
     */
    public OutputGuard(PrintStream protocolStream) {
        this.originalStdout = System.out;
        this.originalStderr = System.err;

        // Create a guarded stdout that redirects non-protocol output to stderr
        this.guardedStdout = new PrintStream(new GuardedOutputStream(originalStderr));
        this.active = false;
    }

    /**
     * Activates the guard. After this call, any System.out output from
     * non-protocol code will be redirected to stderr.
     */
    public void activate() {
        if (!active) {
            System.setOut(guardedStdout);
            active = true;
            log.debug("Output guard activated");
        }
    }

    /**
     * Deactivates the guard and restores original stdout.
     */
    public void deactivate() {
        if (active) {
            System.setOut(originalStdout);
            active = false;
            log.debug("Output guard deactivated");
        }
    }

    /**
     * Returns whether the guard is currently active.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns the original stdout for protocol output.
     * Use this stream for JSONL protocol messages even when the guard is active.
     */
    public PrintStream getProtocolStream() {
        return originalStdout;
    }

    @Override
    public void close() {
        deactivate();
    }

    /**
     * OutputStream that redirects writes to stderr with a warning prefix.
     */
    private static class GuardedOutputStream extends OutputStream {
        private final PrintStream target;

        GuardedOutputStream(PrintStream target) {
            this.target = target;
        }

        @Override
        public void write(int b) {
            target.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            target.write(b, off, len);
        }

        @Override
        public void flush() {
            target.flush();
        }
    }
}
