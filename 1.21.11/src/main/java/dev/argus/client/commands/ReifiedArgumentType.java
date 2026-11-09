package dev.argus.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;

public interface ReifiedArgumentType extends ArgumentType {
   Class getReifiedType();
}
