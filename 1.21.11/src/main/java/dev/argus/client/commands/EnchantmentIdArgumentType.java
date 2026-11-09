package dev.argus.client.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.argus.client.ClientAccess;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public class EnchantmentIdArgumentType implements ArgumentType, ClientAccess {
   private static final String UNKNOWN_ENCHANTMENT_PREFIX = "Element not found ";

   public String parse(StringReader reader) throws CommandSyntaxException {
      String enchantmentId = reader.readString();
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      RegistryAccess registryAccess = level.registryAccess();
      ResourceKey enchantmentKey = Registries.ENCHANTMENT;
      Registry enchantments = registryAccess.lookupOrThrow(enchantmentKey);
      Identifier identifier = Identifier.parse(enchantmentId);
      Optional<?> enchantment = enchantments.get(identifier);
      Supplier<CommandSyntaxException> exceptionSupplier = () -> {
         DynamicCommandExceptionType unknownEnchantmentType = new DynamicCommandExceptionType(argument -> {
            String argumentText = String.valueOf(argument);
            String errorMessage = UNKNOWN_ENCHANTMENT_PREFIX.concat(argumentText);
            return Component.nullToEmpty(errorMessage);
         });
         return unknownEnchantmentType.create(enchantmentId);
      };
      enchantment.orElseThrow(exceptionSupplier);
      return enchantmentId;
   }

   public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      RegistryAccess registryAccess = level.registryAccess();
      ResourceKey enchantmentKey = Registries.ENCHANTMENT;
      Registry enchantments = registryAccess.lookupOrThrow(enchantmentKey);
      Set<Identifier> keys = enchantments.keySet();
      Stream<String> names = keys.stream().map(Identifier::toShortLanguageKey);
      return SharedSuggestionProvider.suggest(names, builder);
   }
}
