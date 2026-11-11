package dev.argus.client.managers;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.events.bus.EventBus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class TaskScheduler implements ClientAccess {
   private final TaskScheduler.ClientThreadTaskQueue rotationTasks = new TaskScheduler.ClientThreadTaskQueue();
   private final TaskScheduler.ClientThreadTaskQueue runTickTasks = new TaskScheduler.ClientThreadTaskQueue();

   public TaskScheduler() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   public void scheduleRotationTask(Runnable task, int delay) {
      this.rotationTasks.enqueue(task, delay);
   }

   public void scheduleRunTickTask(Runnable task) {
      this.runTickTasks.enqueue(task, 1);
   }

   @EventHandler(priority = -100)
   private void onRotationEvent(RotationRequestEvent event) {
      this.rotationTasks.run();
   }

   @EventHandler(priority = -100)
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      this.runTickTasks.run();
   }

   public void scheduleScreenChange(Screen screen) {
      this.scheduleRunTickTask(() -> ClientAccess.argusclientMinecraft.setScreen(screen));
   }

   private static final class ClientThreadTaskQueue {
      private final List<TaskScheduler.CountdownTask> activeTasks = Collections.synchronizedList(new ArrayList<>());
      private final Queue<TaskScheduler.CountdownTask> pendingTasks = new ArrayDeque<>();

      private void run() {
         synchronized (this.activeTasks) {
            while (!this.pendingTasks.isEmpty()) {
               this.activeTasks.add(this.pendingTasks.poll());
            }

            this.activeTasks.removeIf(TaskScheduler.CountdownTask::runIfReady);
         }
      }

      private void enqueue(TaskScheduler.CountdownTask task) {
         synchronized (this.activeTasks) {
            this.pendingTasks.add(task);
         }
      }

      private void enqueue(Runnable task, int delay) {
         Minecraft minecraft = Minecraft.getInstance();
         TaskScheduler.CountdownTask countdownTask = new TaskScheduler.CountdownTask(task, delay);
         if (minecraft.isSameThread()) {
            this.enqueue(countdownTask);
         } else {
            minecraft.executeIfPossible(() -> this.enqueue(countdownTask));
         }
      }
   }

   private static final class CountdownTask {
      private final Runnable task;
      private int remainingPasses;

      private CountdownTask(Runnable task, int remainingPasses) {
         this.task = task;
         this.remainingPasses = remainingPasses;
      }

      private boolean runIfReady() {
         if (this.remainingPasses-- < 0) {
            this.task.run();
            return true;
         } else {
            return false;
         }
      }
   }
}
