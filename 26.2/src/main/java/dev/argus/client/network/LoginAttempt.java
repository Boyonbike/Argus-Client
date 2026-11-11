package dev.argus.client.network;

/** Generation guard: a cancelled/previous request cannot mutate a later screen or session. */
public final class LoginAttempt {
    private long generation;
    public synchronized long begin() { return ++generation; }
    public synchronized void cancel() { ++generation; }
    public synchronized boolean accepts(long token) { return generation == token; }
}
