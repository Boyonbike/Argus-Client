package dev.argus.client.chat;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AutoTpyQueueTest {
    private static final AutoTpyPolicy ANYONE=new AutoTpyPolicy(Set.of(),Set.of(),false,false,true,false);
    private AutoTpyQueue queue() { var q=new AutoTpyQueue(); q.configure(1024); return q; }
    private AutoTpyQueue.Request req(String name) { return new AutoTpyQueue.Request(name,false,120000,"/tpy "+name); }

    @Test void sameRequestAfterSendIsQueuedAgain() {
        var q=queue(); var first=req("Alice");
        q.offer(first,0); q.sent(q.next(0).orElseThrow());
        assertEquals(0,q.stats(1).live());
        q.offer(req("Alice"),2);
        assertEquals("/tpy Alice",q.next(2).orElseThrow().command());
    }
    @Test void unsentRepeatReplacesInPlace() {
        var q=queue(); q.offer(req("Alice"),0); q.offer(req("Bob"),0);
        q.offer(new AutoTpyQueue.Request("Alice",false,120000,"/essentials:tpaccept Alice"),1);
        assertEquals(2,q.stats(1).live());
        assertEquals("/essentials:tpaccept Alice",q.next(1).orElseThrow().command());
    }
    @Test void friendsGoFirstThenOldest() {
        var q=queue(); q.offer(req("Alice"),0); q.offer(req("Bob"),0); q.offer(req("Carol"),0);
        q.reconcile(ANYONE,Set.of("carol"),0);
        assertEquals("Carol",q.next(0).orElseThrow().player());
        q.reconcile(ANYONE,Set.of(),0);
        assertEquals("Alice",q.next(0).orElseThrow().player());
    }
    @Test void expiredAndDisallowedRequestsAreDropped() {
        var q=queue(); q.offer(new AutoTpyQueue.Request("Alice",false,100,"/tpy Alice"),0); q.offer(req("Bob"),0);
        assertTrue(q.next(100).map(r->r.player().equals("Bob")).orElse(false));
        q.reconcile(new AutoTpyPolicy(Set.of(),Set.of("bob"),false,false,true,false),Set.of(),101);
        assertTrue(q.next(101).isEmpty());
    }
    @Test void capacityOverflowsWithoutDroppingQueued() {
        var q=queue(); q.configure(2);
        q.offer(req("A1"),0); q.offer(req("A2"),0); q.offer(req("A3"),0);
        assertEquals(2,q.stats(0).live()); assertEquals(1,q.stats(0).overflow());
        q.offer(req("A1"),1); assertEquals(1,q.stats(1).overflow());
    }
    @Test void cancelAndClear() {
        var q=queue(); q.offer(req("Alice"),0); q.offer(req("Bob"),0);
        q.cancel("alice",1); assertEquals("Bob",q.next(1).orElseThrow().player());
        q.clear(2); assertTrue(q.next(2).isEmpty());
    }
}
