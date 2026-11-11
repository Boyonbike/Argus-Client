package dev.argus.client.config.importing;
/** Client-thread generation token: obsolete background reads never publish a preview. */
public final class ImportPreviewState {
    private long generation;
    private boolean closed;
    public long begin(){closed=false;return ++generation;}
    public void invalidate(){generation++;}
    public void close(){closed=true;generation++;}
    public boolean accepts(long token){return !closed && token==generation;}
}
