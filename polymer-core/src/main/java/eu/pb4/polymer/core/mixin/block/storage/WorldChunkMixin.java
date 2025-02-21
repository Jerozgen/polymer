package eu.pb4.polymer.core.mixin.block.storage;

import com.google.common.collect.ForwardingIterator;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.PolymerBlockPosStorage;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.chunk.BlendingData;
import net.minecraft.world.tick.ChunkTickScheduler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Iterator;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin extends Chunk implements PolymerBlockPosStorage {

    public WorldChunkMixin(ChunkPos pos, UpgradeData upgradeData, HeightLimitView heightLimitView, Registry<Biome> biome, long inhabitedTime, @Nullable ChunkSection[] sectionArrayInitializer, @Nullable BlendingData blendingData) {
        super(pos, upgradeData, heightLimitView, biome, inhabitedTime, sectionArrayInitializer, blendingData);
    }

    @Inject(
            method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/chunk/UpgradeData;Lnet/minecraft/world/tick/ChunkTickScheduler;Lnet/minecraft/world/tick/ChunkTickScheduler;J[Lnet/minecraft/world/chunk/ChunkSection;Lnet/minecraft/world/chunk/WorldChunk$EntityLoader;Lnet/minecraft/world/gen/chunk/BlendingData;)V",
            at = @At("TAIL")
    )
    private void polymer$polymerBlocksInit(World world, ChunkPos pos, UpgradeData upgradeData, ChunkTickScheduler blockTickScheduler, ChunkTickScheduler fluidTickScheduler, long inhabitedTime, ChunkSection[] sectionArrayInitializer, WorldChunk.EntityLoader entityLoader, BlendingData blendingData, CallbackInfo ci) {
        if (world instanceof ServerWorld) {
            this.polymer$generatePolymerBlockSet();
        }
    }


    @Unique
    private void polymer$generatePolymerBlockSet() {
        for (var section : this.getSectionArray()) {
            if (section != null && !section.isEmpty()) {
                var container = section.getBlockStateContainer();
                if (container.hasAny(PolymerImplUtils.POLYMER_STATES::contains)) {
                    var storage = (PolymerBlockPosStorage) section;
                    BlockState state;
                    for (byte x = 0; x < 16; x++) {
                        for (byte z = 0; z < 16; z++) {
                            for (byte y = 0; y < 16; y++) {
                                state = container.get(x, y, z);
                                if (PolymerImplUtils.POLYMER_STATES.contains(state)) {
                                    storage.polymer$setSynced(x, y, z);
                                }
                            }
                        }
                    }
                }
            }
        }

    }


    @Inject(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkSection;setBlockState(IIILnet/minecraft/block/BlockState;)Lnet/minecraft/block/BlockState;", shift = At.Shift.AFTER))
    private void polymer$addToList(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        if (PolymerImplUtils.POLYMER_STATES.contains(state)) {
            this.polymer$setSynced(pos.getX(), pos.getY(), pos.getZ());
        } else {
            this.polymer$removeSynced(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    @Override
    public @Nullable Iterator<BlockPos.Mutable> polymer$iterator() {
        return new ForwardingIterator<>() {
            int current;
            Iterator<BlockPos.Mutable> currentIterator = Collections.emptyIterator();

            @Override
            protected Iterator<BlockPos.Mutable> delegate() {
                if (this.currentIterator == null || !this.currentIterator.hasNext()) {
                    var array = WorldChunkMixin.this.getSectionArray();
                    while (this.current < array.length) {
                        var s = array[this.current++];
                        var si = (PolymerBlockPosStorage) s;
                        if (s != null && si.polymer$hasAny()) {
                            this.currentIterator = si.polymer$iterator(ChunkSectionPos.from(WorldChunkMixin.this.getPos(), s.getYOffset() >> 4));
                            break;
                        }
                    }
                }

                return this.currentIterator;
            }
        };
    }

    @Override
    public void polymer$setSynced(int x, int y, int z) {
        this.polymer_getSectionStorage(y).polymer$setSynced(x, y, z);
    }

    @Override
    public void polymer$removeSynced(int x, int y, int z) {
        this.polymer_getSectionStorage(y).polymer$removeSynced(x, y, z);
    }

    @Override
    public boolean polymer$isSynced(int x, int y, int z) {
        return this.polymer_getSectionStorage(y).polymer$isSynced(x, y, z);
    }

    @Override
    public boolean polymer$hasAny() {
        for (var s : this.getSectionArray()) {
            if (s != null && ((PolymerBlockPosStorage) s).polymer$hasAny()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable ShortSet polymer$getBackendSet() {
        return null;
    }

    @Override
    public @Nullable Iterator<BlockPos.Mutable> polymer$iterator(ChunkSectionPos sectionPos) {
        return null;
    }

    private PolymerBlockPosStorage polymer_getSectionStorage(int y) {
        return (PolymerBlockPosStorage) this.getSection(this.getSectionIndex(y));
    }
}
