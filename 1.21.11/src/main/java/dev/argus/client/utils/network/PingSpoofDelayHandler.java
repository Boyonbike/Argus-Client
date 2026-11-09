package dev.argus.client.utils.network;

import dev.argus.client.modules.exploit.PingSpoofModule;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class PingSpoofDelayHandler extends ChannelDuplexHandler {
   public static final String NAME = "argusclient_delayed_handler";
   private final PingSpoofModule module;
   private final BooleanSupplier inPlay;
   private ChannelHandlerContext context;
   private PingSpoofDelayHandler.DelayQueue outbound;
   private PingSpoofDelayHandler.DelayQueue inbound;

   public PingSpoofDelayHandler(PingSpoofModule module, BooleanSupplier inPlay) {
      this.module = module;
      this.inPlay = inPlay;
   }

   public void handlerAdded(ChannelHandlerContext context) {
      this.context = context;
      this.outbound = new PingSpoofDelayHandler.DelayQueue(this::writeQueued, context::flush);
      this.inbound = new PingSpoofDelayHandler.DelayQueue(this::readQueued, context::fireChannelReadComplete);
   }

   public void handlerRemoved(ChannelHandlerContext context) {
      this.outbound.drain();
      this.inbound.drain();
   }

   public void channelInactive(ChannelHandlerContext context) throws Exception {
      this.inbound.drain();
      this.outbound.discard();
      super.channelInactive(context);
   }

   public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
      if (!(message instanceof ByteBuf)) {
         context.write(message, promise);
      } else {
         long delayMillis = this.getDelayMillis();
         if (delayMillis <= 0L && this.outbound.isEmpty()) {
            context.write(message, promise);
         } else {
            this.outbound.enqueue(message, promise, delayMillis);
         }
      }
   }

   public void flush(ChannelHandlerContext context) {
      if (this.outbound.isEmpty()) {
         context.flush();
      }
   }

   private void writeQueued(PingSpoofDelayHandler.DelayedMessage queued) {
      if (this.context.channel().isActive()) {
         this.context.write(queued.message(), queued.promise());
      } else {
         ReferenceCountUtil.release(queued.message());
         if (queued.promise() != null) {
            queued.promise().tryFailure(new ClosedChannelException());
         }
      }
   }

   public void channelRead(ChannelHandlerContext context, Object message) {
      if (!(message instanceof ByteBuf)) {
         context.fireChannelRead(message);
      } else {
         long delayMillis = this.getDelayMillis();
         if (delayMillis <= 0L && this.inbound.isEmpty()) {
            context.fireChannelRead(message);
         } else {
            this.inbound.enqueue(message, null, delayMillis);
         }
      }
   }

   public void channelReadComplete(ChannelHandlerContext context) {
      if (this.inbound.isEmpty()) {
         context.fireChannelReadComplete();
      }
   }

   private void readQueued(PingSpoofDelayHandler.DelayedMessage queued) {
      if (this.context.channel().isActive()) {
         this.context.fireChannelRead(queued.message());
      } else {
         ReferenceCountUtil.release(queued.message());
      }
   }

   private long getDelayMillis() {
      return this.module.isToggled() && this.module.isFullMode() && this.inPlay.getAsBoolean() ? this.module.sampleDelayMillis() / 2 : 0L;
   }

   private final class DelayQueue {
      private final ArrayDeque<PingSpoofDelayHandler.DelayedMessage> messages = new ArrayDeque<>();
      private final Consumer<PingSpoofDelayHandler.DelayedMessage> consumer;
      private final Runnable onBatchComplete;
      private ScheduledFuture<?> scheduled;
      private long lastDeadline = System.nanoTime();

      private DelayQueue(Consumer<PingSpoofDelayHandler.DelayedMessage> consumer, Runnable onBatchComplete) {
         this.consumer = consumer;
         this.onBatchComplete = onBatchComplete;
      }

      private boolean isEmpty() {
         return this.messages.isEmpty();
      }

      private void enqueue(Object message, ChannelPromise promise, long delayMillis) {
         long now = System.nanoTime();
         long deadline = now + TimeUnit.MILLISECONDS.toNanos(delayMillis);
         if (deadline - this.lastDeadline < 0L) {
            deadline = this.lastDeadline;
         }

         this.lastDeadline = deadline;
         this.messages.add(new PingSpoofDelayHandler.DelayedMessage(message, promise, deadline));
         if (this.scheduled == null) {
            this.schedule(deadline - now);
         }
      }

      private void schedule(long delayNanos) {
         this.scheduled = PingSpoofDelayHandler.this.context.executor().schedule(this::releaseExpired, Math.max(delayNanos, 0L), TimeUnit.NANOSECONDS);
      }

      private void releaseExpired() {
         this.scheduled = null;

         boolean released;
         PingSpoofDelayHandler.DelayedMessage queued;
         for (released = false; (queued = this.messages.peek()) != null && queued.deadline() - System.nanoTime() <= 0L; released = true) {
            this.messages.poll();
            this.consumer.accept(queued);
         }

         if (released) {
            this.onBatchComplete.run();
         }

         if ((queued = this.messages.peek()) != null) {
            this.schedule(queued.deadline() - System.nanoTime());
         }
      }

      private void drain() {
         this.cancelScheduled();

         boolean released;
         PingSpoofDelayHandler.DelayedMessage queued;
         for (released = false; (queued = this.messages.poll()) != null; released = true) {
            this.consumer.accept(queued);
         }

         if (released && PingSpoofDelayHandler.this.context.channel().isActive()) {
            this.onBatchComplete.run();
         }
      }

      private void discard() {
         this.cancelScheduled();

         PingSpoofDelayHandler.DelayedMessage queued;
         while ((queued = this.messages.poll()) != null) {
            ReferenceCountUtil.release(queued.message());
            if (queued.promise() != null) {
               queued.promise().tryFailure(new ClosedChannelException());
            }
         }
      }

      private void cancelScheduled() {
         if (this.scheduled != null) {
            this.scheduled.cancel(false);
            this.scheduled = null;
         }
      }
   }

   private record DelayedMessage(Object message, ChannelPromise promise, long deadline) {
   }
}
