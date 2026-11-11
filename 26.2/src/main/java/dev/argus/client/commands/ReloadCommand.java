package dev.argus.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.managers.ShaderRegistry;
import dev.argus.client.managers.SoundManager;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.mixin.TextureManagerAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

public final class ReloadCommand extends Command {
   private static final byte[] PNG_SIGNATURE;
   private CompletableFuture reloadFuture = CompletableFuture.completedFuture(null);

   public ReloadCommand() {
      super("reload");
      this.setAliases("rl");
   }

   @Override
   public void exec(LiteralArgumentBuilder commandBuilder) {
      commandBuilder.executes(commandContext -> {
         CompletableFuture previousReload = this.reloadFuture;
         if (!previousReload.isDone()) {
            return 0;
         }

         MutableComponent reloadingMessage = Component.literal("Reloading Argus Client resources...");
         ReloadCommand command = this;
         ChatUtils.printChat(reloadingMessage, ChatUtils.objectSignature(command));
         this.reloadFuture = CompletableFuture.runAsync(this::reloadResources, ClientAccess.executorService);
         this.reloadFuture.whenComplete((ignoredResult, ignoredFailure) -> {
            MutableComponent completionMessage = Component.literal("Reload complete!").withStyle(style -> {
               ChatFormatting format = ChatFormatting.GREEN;
               return style.applyFormat(format);
            });
            ReloadCommand completedCommand = this;
            ChatUtils.printChat(completionMessage, ChatUtils.objectSignature(completedCommand));
         });
         return 1;
      });
   }

   private void reloadResources() {
      ClientAccess.argusclientMinecraft.execute(() -> {
         ShaderRegistry queuedShaderRegistry = ClientContext.shaderRegistry;
         queuedShaderRegistry.reloadAll();
      });
      ConfigurationManager directoryManager = ClientContext.configurationManager;
      directoryManager.prepareDirectories();
      SoundManager soundManager = ClientContext.soundManager;
      soundManager.reload();
      HashSet<Identifier> mountedTextures = new HashSet();
      Minecraft textureClient = ClientAccess.argusclientMinecraft;

      for (Identifier texture : ((TextureManagerAccessor)textureClient.getTextureManager()).getAllTextures().keySet()) {
         String textureNamespace = texture.getNamespace();
         if (textureNamespace.equals("argusclient-mount")) {
            mountedTextures.add(texture);
         }
      }

      for (Identifier mountedTexture : mountedTextures) {
         Path dataDirectory = ConfigurationManager.ROOT_DIRECTORY;
         Path imagePath = dataDirectory.resolve(mountedTexture.getPath());

         try {
            validatePngSignature(imagePath);
         } catch (IOException | RuntimeException invalidImage) {
            String errorDetail = invalidImage.getMessage();
            String errorText = "Invalid Image! " + errorDetail;
            MutableComponent errorMessage = Component.literal(errorText).withStyle(style -> {
               ChatFormatting format = ChatFormatting.RED;
               return style.applyFormat(format);
            });
            ChatUtils.printChat(errorMessage, ChatUtils.createSignature(-999));
            continue;
         }

         ClientAccess.argusclientMinecraft.executeIfPossible(() -> {
            Minecraft releaseClient = ClientAccess.argusclientMinecraft;
            TextureManager textureManager = releaseClient.getTextureManager();
            textureManager.release(mountedTexture);
            Minecraft registrationClient = ClientAccess.argusclientMinecraft;
            textureManager = registrationClient.getTextureManager();
            SimpleTexture replacementTexture = new SimpleTexture(mountedTexture);
            textureManager.registerAndLoad(mountedTexture, replacementTexture);
         });
      }
   }

   private static void validatePngSignature(Path imagePath) throws IOException {
      try (InputStream input = Files.newInputStream(imagePath)) {
         int signatureLength = PNG_SIGNATURE.length;
         byte[] actualSignature = input.readNBytes(signatureLength);
         if (!Arrays.equals(actualSignature, PNG_SIGNATURE)) {
            throw new RuntimeException("File is not a png");
         }
      }
   }

   static {
      byte[] signature = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};
      PNG_SIGNATURE = signature;
   }
}
