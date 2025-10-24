package io.github.tristoris.duoyunblocks.components;

import com.mojang.serialization.Codec;
import io.github.tristoris.duoyunblocks.DuoyunBlocks;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.function.UnaryOperator;

public final class ModDataComponents {
    public static final ComponentType<Integer> LUCK =
            register("luck", builder -> builder.codec(Codec.INT));

    public static void registerModDataComponents() {

    }

    private static <T>ComponentType<T> register(String name, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(DuoyunBlocks.MOD_ID, name),
                builderOperator.apply(ComponentType.builder()).build());
    }

    private static Identifier id(String path) {
        return Identifier.of(DuoyunBlocks.MOD_ID, path);
    }

    private ModDataComponents() {}
}