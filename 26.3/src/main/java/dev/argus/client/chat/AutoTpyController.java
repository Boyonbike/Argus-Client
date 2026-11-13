package dev.argus.client.chat;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Client-thread coordinator. Every incoming request that passes the rules is accepted once, paced only by the
 * accept interval. Server replies are never waited on; a rate-limit reply re-queues the last automatic accept.
 */
public final class AutoTpyController {
    private static final Set<String> TP_VERBS=Set.of("tpy","tpaccept","tpyes","tpn","tpdeny","tpno","tpa","tpahere","tpacancel","tpcancel");
    private static final Set<String> ANSWER_VERBS=Set.of("tpy","tpaccept","tpyes","tpn","tpdeny","tpno");
    private final AutoTpyQueue queue=new AutoTpyQueue();
    private final AutoTpyPacer pacer=new AutoTpyPacer();
    private AutoTpyPolicy policy=new AutoTpyPolicy(Set.of(),Set.of(),true,false,true,false);
    private Set<String> friends=Set.of();
    private boolean enabled;
    /** Our own command, so its echo through commandSent isn't mistaken for a manual answer. */
    private String emission;
    /** The most recent automatic accept, re-queued if the server rate-limits it. Cleared by any manual tp command. */
    private AutoTpyQueue.Request lastAuto;
    private long sendEpoch,confirmations;
    public AutoTpyQueue queue() { return queue; }
    public AutoTpyPacer pacer() { return pacer; }
    public boolean enabled() { return enabled; }
    public void enabled(boolean value) { if(enabled!=value) { reset();enabled=value; } }
    public void configure(AutoTpyPolicy policy,Set<String> friends,int capacity,long interval,boolean adaptive,long minimum,long maximum) {
        pacer.configure(interval,adaptive,minimum,maximum);
        queue.configure(capacity);
        this.policy=policy;this.friends=AutoTpyPolicy.keys(friends);
    }
    public void offer(AutoTpyQueue.Request request,long now) {
        if(enabled&&policy.allows(request.player(),friends.contains(AutoTpyPolicy.key(request.player())),request.toThem())) queue.offer(request,now);
    }
    public void tick(long now,long tick,Consumer<String> sender) {
        if(!enabled) return;
        queue.reconcile(policy,friends,now);
        if(!pacer.ready(now,tick)) return;
        var candidate=queue.next(now); if(candidate.isEmpty()) return;
        var request=candidate.get();
        // Mark sent before calling back: PacketEvent.Sent can fire synchronously inside sender.accept.
        emission=normalise(request.command());
        try {
            sender.accept(request.command());
            queue.sent(request);lastAuto=request;sendEpoch++;
        } catch(RuntimeException e) {
            emission=null; // Stays queued; the pacer below backs off before trying again.
        }
        pacer.sent(now,tick);
    }
    public void commandSent(String command,long now,long tick) {
        String normal=normalise(command);
        if(emission!=null&&emission.equals(normal)) { emission=null;return; }
        String[] parts=normal.split("\\s+");
        String verb=parts[0].substring(parts[0].lastIndexOf(':')+1);
        if(!TP_VERBS.contains(verb)) return;
        pacer.sent(now,tick);lastAuto=null;
        if(parts.length==2&&ANSWER_VERBS.contains(verb)) queue.cancel(parts[1],"answered manually");
    }
    public void lifecycle(ChatTpaRequests.Change change,long now) {
        if(!enabled||!change.named()) return;
        if(change.kind()==ChatTpaRequests.ChangeKind.Cancelled) queue.cancel(change.request().player(),"request cancelled");
        else if(change.kind()==ChatTpaRequests.ChangeKind.Expired) queue.cancel(change.request().player(),"request expired");
    }
    public void message(String text,long now) {
        if(!enabled) return;
        var parsed=AutoTpyFeedback.parse(text);if(parsed.isEmpty()) return;
        var observation=parsed.get();
        switch(observation.kind()) {
            case Accepted -> pacer.confirmed(++confirmations,now);
            case RateLimited -> {
                if(lastAuto==null) return;
                pacer.rateLimited(sendEpoch+":"+observation.fingerprint(),observation.waitMs(),now);
                queue.offer(lastAuto,now);lastAuto=null;
            }
            case Cancelled, Expired -> { if(observation.player()!=null) queue.cancel(observation.player(),"server: "+observation.kind().name().toLowerCase(Locale.ROOT)); }
            default -> {}
        }
    }
    private static String normalise(String command) { return command.strip().replaceFirst("^/","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," "); }
    public void reset() { queue.reset();pacer.reset();emission=null;lastAuto=null;sendEpoch=0; }
}
