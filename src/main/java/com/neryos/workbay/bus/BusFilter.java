package com.neryos.workbay.bus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What one link is allowed to carry. SPEC.md §5.
 *
 * <p><b>It matches on the resource's own identity and nothing else</b> — the item, or the fluid.
 * Not components, not tags, not the mod it came from, not a count. Every one of those is a second
 * axis with its own toggle, its own tooltip and its own way of being subtly wrong, and none of them
 * is what a player means by "only iron goes here". XNet ships four such toggles, LaserIO ships five
 * filter <em>items</em>, and both are answering a routing puzzle this mod deliberately is not
 * (SPEC.md §0). Tags are the one that will actually be asked for; OPEN_ISSUES has it.
 *
 * <p><b>Whitelist and blacklist, one flag.</b> "Everything except cobblestone" is the same question
 * asked the other way round and it costs one boolean and one {@code !=}; a mod that answers only
 * the first half sends the player back to a chest full of cobblestone with nothing to do about it.
 *
 * <p>An <b>empty list means no filter at all</b>, in both modes. A deny list with nothing on it
 * denies nothing, which is the only reading that does not make a half-configured filter stop a
 * link dead.
 *
 * <p>The entries are ids, and which registry they name is the <em>link's</em> business: an item
 * link's are items, a fluid link's are fluids. That is why changing a link's resource clears its
 * filter ({@link BusConfig#withResource}) — the same nine ids read as nonsense in the other
 * registry, and silently keeping them is how a link starts refusing everything for no visible
 * reason. Energy has no identity to match on and therefore no filter.
 */
public record BusFilter(List<ResourceLocation> entries, boolean deny) {

    /** One row of ghost slots in the panel, which is the room the links list has. */
    public static final int MAX = 9;

    public static final BusFilter NONE = new BusFilter(List.of(), false);

    public static final Codec<BusFilter> CODEC = RecordCodecBuilder.create(i -> i.group(
        ResourceLocation.CODEC.listOf().optionalFieldOf("Entries", List.of())
            .forGetter(BusFilter::entries),
        Codec.BOOL.optionalFieldOf("Deny", false).forGetter(BusFilter::deny)
    ).apply(i, BusFilter::new));

    public BusFilter {
        entries = List.copyOf(entries);
    }

    /** One entry, allowed. What most links want and what a test says in one line. */
    public static BusFilter only(ResourceLocation id) {
        return new BusFilter(List.of(id), false);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Whether this link may carry that stack. Empty allows everything, whichever mode it is in. */
    public boolean allows(ItemStack stack) {
        return allows(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public boolean allows(FluidStack stack) {
        return allows(BuiltInRegistries.FLUID.getKey(stack.getFluid()));
    }

    private boolean allows(ResourceLocation id) {
        return entries.isEmpty() || entries.contains(id) != deny;
    }

    public Optional<ResourceLocation> at(int slot) {
        return slot >= 0 && slot < entries.size() ? Optional.of(entries.get(slot)) : Optional.empty();
    }

    /**
     * Puts {@code id} in, or takes whatever is at {@code slot} out when it is empty.
     *
     * <p>The list has no holes: an entry removed from the middle closes up, and an id dropped on a
     * slot past the end is appended. A sparse nine-slot array would have to be serialised, synced
     * and compared with holes in it to buy a player nothing but the column their iron sits in.
     */
    public BusFilter with(int slot, Optional<ResourceLocation> id) {
        List<ResourceLocation> now = new ArrayList<>(entries);
        if (id.isEmpty()) {
            if (slot < 0 || slot >= now.size()) {
                return this;
            }
            now.remove(slot);
        } else if (now.contains(id.get())) {
            return this;
        } else if (slot >= 0 && slot < now.size()) {
            now.set(slot, id.get());
        } else if (now.size() < MAX) {
            now.add(id.get());
        } else {
            return this;
        }
        return new BusFilter(now, deny);
    }

    public BusFilter withDeny(boolean nowDeny) {
        return new BusFilter(entries, nowDeny);
    }
}
