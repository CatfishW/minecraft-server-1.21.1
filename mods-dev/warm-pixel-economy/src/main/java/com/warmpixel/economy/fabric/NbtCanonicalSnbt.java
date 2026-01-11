package com.warmpixel.economy.fabric;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class NbtCanonicalSnbt {
    private NbtCanonicalSnbt() {
    }

    public static String toSnbt(Tag tag) {
        StringBuilder builder = new StringBuilder();
        appendTag(builder, tag);
        return builder.toString();
    }

    public static CompoundTag parseCompound(String snbt) {
        try {
            Tag tag = TagParser.parseTag(snbt);
            if (tag instanceof CompoundTag compound) {
                return compound;
            }
        } catch (Exception ignored) {
        }
        return new CompoundTag();
    }

    private static void appendTag(StringBuilder builder, Tag tag) {
        if (tag instanceof CompoundTag compound) {
            appendCompound(builder, compound);
        } else if (tag instanceof ListTag listTag) {
            appendList(builder, listTag);
        } else if (tag instanceof ByteTag byteTag) {
            builder.append(byteTag.getAsByte()).append('b');
        } else if (tag instanceof ShortTag shortTag) {
            builder.append(shortTag.getAsShort()).append('s');
        } else if (tag instanceof IntTag intTag) {
            builder.append(intTag.getAsInt());
        } else if (tag instanceof LongTag longTag) {
            builder.append(longTag.getAsLong()).append('L');
        } else if (tag instanceof FloatTag floatTag) {
            builder.append(floatTag.getAsFloat()).append('f');
        } else if (tag instanceof DoubleTag doubleTag) {
            builder.append(doubleTag.getAsDouble()).append('d');
        } else if (tag instanceof StringTag stringTag) {
            builder.append('"').append(escape(stringTag.getAsString())).append('"');
        } else if (tag instanceof ByteArrayTag byteArrayTag) {
            builder.append("[B;");
            byte[] bytes = byteArrayTag.getAsByteArray();
            for (int i = 0; i < bytes.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(bytes[i]).append('b');
            }
            builder.append(']');
        } else if (tag instanceof IntArrayTag intArrayTag) {
            builder.append("[I;");
            int[] ints = intArrayTag.getAsIntArray();
            for (int i = 0; i < ints.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(ints[i]);
            }
            builder.append(']');
        } else if (tag instanceof LongArrayTag longArrayTag) {
            builder.append("[L;");
            long[] longs = longArrayTag.getAsLongArray();
            for (int i = 0; i < longs.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(longs[i]).append('L');
            }
            builder.append(']');
        } else {
            builder.append('"').append(escape(tag.getAsString())).append('"');
        }
    }

    private static void appendCompound(StringBuilder builder, CompoundTag compound) {
        builder.append('{');
        Set<String> keys = compound.getAllKeys();
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        boolean first = true;
        for (String key : sorted) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(key)).append('"').append(':');
            Tag child = compound.get(key);
            appendTag(builder, child);
        }
        builder.append('}');
    }

    private static void appendList(StringBuilder builder, ListTag listTag) {
        builder.append('[');
        for (int i = 0; i < listTag.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            appendTag(builder, listTag.get(i));
        }
        builder.append(']');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
