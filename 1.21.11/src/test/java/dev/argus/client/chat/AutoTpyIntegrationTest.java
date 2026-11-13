package dev.argus.client.chat;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class AutoTpyIntegrationTest {
    private static final AutoTpyPolicy ANYONE=new AutoTpyPolicy(Set.of(),Set.of(),false,false,true,false);
    private AutoTpyController c() {
        var c=new AutoTpyController(); c.configure(ANYONE,Set.of(),1024,500,false,50,5000); c.enabled(true); return c;
    }
    private AutoTpyQueue.Request req(String name) { return new AutoTpyQueue.Request(name,false,120000,"/tpy "+name); }

    @Test void everyRequestFromTheSamePlayerIsAccepted() {
        var c=c(); var sent=new ArrayList<String>();
        long now=0,tick=0;
        for(int i=0;i<3;i++) {
            c.offer(req("Alice"),now);
            final long n=now,t=tick;
            c.tick(n,t,cmd->{sent.add(cmd);c.commandSent(cmd,n,t);});
            c.message("You accepted Alice's teleport request.",now+10);
            now+=600;tick++;
        }
        assertEquals(List.of("/tpy Alice","/tpy Alice","/tpy Alice"),sent);
    }
    @Test void noReplyNeverCausesARetry() {
        var c=c(); var sent=new ArrayList<String>();
        c.offer(req("Alice"),0); c.tick(0,1,sent::add);
        for(int t=2;t<200;t++) c.tick(t*500L,t,sent::add);
        assertEquals(List.of("/tpy Alice"),sent);
    }
    @Test void pacingSpacesBurstsAndOneSendPerTick() {
        var c=c(); var sent=new ArrayList<String>();
        for(String n:List.of("Alice","Bob")) c.offer(req(n),0);
        c.tick(0,1,sent::add); c.tick(0,1,sent::add); c.tick(400,2,sent::add);
        assertEquals(1,sent.size());
        c.tick(500,3,sent::add); assertEquals(List.of("/tpy Alice","/tpy Bob"),sent);
    }
    @Test void cancellationAndManualAnswerStopQueuedRequests() {
        var c=c();
        c.offer(req("Alice"),0); c.offer(req("Bob"),0);
        c.lifecycle(new ChatTpaRequests.Change(ChatTpaRequests.ChangeKind.Cancelled,new ChatTpaRequests.Request("Alice",false,120000)),1);
        c.commandSent("tpn Bob",1,1);
        c.tick(10000,2,cmd->fail("Cancelled request was sent: "+cmd));
    }
    @Test void rateLimitRequeuesLastAutomaticAccept() {
        var c=c(); var sent=new ArrayList<String>();
        c.offer(req("Alice"),0); c.tick(0,1,cmd->{sent.add(cmd);c.commandSent(cmd,0,1);});
        c.message("Please wait 3 seconds before using this command again.",100);
        c.tick(1000,2,sent::add); assertEquals(1,sent.size());
        c.tick(3200,3,sent::add); assertEquals(List.of("/tpy Alice","/tpy Alice"),sent);
    }
    @Test void localSendFailureStaysQueued() {
        var c=c(); c.offer(req("Alice"),0);
        c.tick(0,1,cmd->{throw new IllegalStateException("closed");});
        var sent=new ArrayList<String>(); c.tick(1000,2,sent::add);
        assertEquals(List.of("/tpy Alice"),sent);
    }
    @Test void filtersApplyBeforeSendAndDisableClears() {
        var c=c(); c.offer(req("Alice"),0);
        c.configure(new AutoTpyPolicy(Set.of("Alice"),Set.of("Alice"),true,true,true,false),Set.of("alice"),1024,500,false,50,5000);
        c.tick(0,1,cmd->fail("Blocked player was sent"));
        c.offer(req("Alice"),1); assertEquals(0,c.queue().stats(1).live());
        c.configure(ANYONE,Set.of(),1024,500,false,50,5000); c.offer(req("Bob"),2);
        c.enabled(false); assertTrue(c.queue().entries(3).isEmpty());
    }
}
