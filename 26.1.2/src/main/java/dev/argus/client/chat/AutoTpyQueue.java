package dev.argus.client.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Requests waiting for their accept command. Every allowed request is answered: there is no reply timeout,
 * no retry ledger and no duplicate suppression, so a second request from the same player is accepted again.
 * Client thread only.
 */
public final class AutoTpyQueue {
    public enum State { Queued }
    public record Request(String player,boolean toThem,long expires,String command) {}
    public record Entry(String player,State state,long expires,boolean friend) {}
    public record Stats(int live,int sent,int overflow,String latest) {}
    private record Node(Request request,long order,boolean friend) {}
    private final Map<String,Node> nodes=new LinkedHashMap<>();
    private int capacity=1024,overflow,sent;
    private long sequence;
    private String latest="No automatic accepts yet";

    public void configure(int cap) {
        if(cap<1||cap>16384) throw new IllegalArgumentException("Invalid queue configuration");
        capacity=cap;
    }
    /** Queue the request, replacing any still-unsent one from the same player (keeping its place in line). */
    public void offer(Request request,long now) {
        if(request==null||!ChatMessages.validName(request.player())||request.expires()<=now) return;
        String key=AutoTpyPolicy.key(request.player());
        Node old=nodes.get(key);
        if(old==null&&nodes.size()>=capacity) { overflow++;latest="Buffer full: ignored "+request.player();return; }
        nodes.put(key,new Node(request,old==null?++sequence:old.order(),old!=null&&old.friend()));
        latest="Queued "+request.player();
    }
    /** Drop expired requests and any the current rules no longer allow. */
    public void reconcile(AutoTpyPolicy policy,Set<String> friends,long now) {
        expire(now);
        var it=nodes.entrySet().iterator();
        while(it.hasNext()) {
            var entry=it.next(); Node n=entry.getValue();
            boolean friend=friends.contains(entry.getKey());
            if(!policy.allows(n.request().player(),friend,n.request().toThem())) { it.remove();latest=n.request().player()+": not allowed by current filters";continue; }
            if(friend!=n.friend()) entry.setValue(new Node(n.request(),n.order(),friend));
        }
    }
    private void expire(long now) {
        var it=nodes.values().iterator();
        while(it.hasNext()) { Node n=it.next(); if(now>=n.request().expires()) { it.remove();latest=n.request().player()+": request expired"; } }
    }
    /** The next request to accept: friends first, then oldest. It stays queued until {@link #sent} is called. */
    public Optional<Request> next(long now) {
        expire(now);
        return nodes.values().stream()
            .min(Comparator.<Node,Boolean>comparing(n->!n.friend()).thenComparingLong(Node::order))
            .map(Node::request);
    }
    public void sent(Request request) {
        String key=AutoTpyPolicy.key(request.player());
        Node n=nodes.get(key);
        if(n!=null&&n.request()==request) nodes.remove(key);
        sent++;
        latest="Sent "+request.command();
    }
    public void cancel(String player,String reason) {
        Node n=nodes.remove(AutoTpyPolicy.key(player));
        if(n!=null) latest=n.request().player()+": "+reason;
    }
    public void cancel(String player,long now) { cancel(player,"cancelled manually"); }
    public void clear(long now) { if(!nodes.isEmpty()) latest="Queue cleared"; nodes.clear(); }
    public void reset() { nodes.clear();overflow=sent=0;latest="New session"; }
    public List<Entry> entries(long now) {
        expire(now);
        var result=new ArrayList<Entry>();
        for(Node n:nodes.values()) result.add(new Entry(n.request().player(),State.Queued,n.request().expires(),n.friend()));
        return List.copyOf(result);
    }
    public Stats stats(long now) { expire(now); return new Stats(nodes.size(),sent,overflow,latest); }
}
