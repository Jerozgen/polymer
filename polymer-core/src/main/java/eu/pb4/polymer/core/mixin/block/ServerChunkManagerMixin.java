package eu.pb4.polymer.core.mixin.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.impl.interfaces.PolymerBlockPosStorage;
import eu.pb4.polymer.core.impl.interfaces.ServerChunkManagerInterface;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin implements ServerChunkManagerInterface {

    @Shadow
    @Final
    public ThreadedAnvilChunkStorage threadedAnvilChunkStorage;

    @Shadow
    @Final
    private ServerWorld world;

    @Shadow
    @Nullable
    public abstract WorldChunk getWorldChunk(int chunkX, int chunkZ);

    @Shadow @Final private ServerLightingProvider lightingProvider;

    @Shadow public abstract ServerLightingProvider getLightingProvider();

    @Unique
    private final Object2LongMap<ChunkSectionPos> polymer$lastUpdates = new Object2LongArrayMap<>();
    @Unique
    private final Object2BooleanMap<ChunkSectionPos> polymer$hadPolymerSource = new Object2BooleanOpenHashMap<>();

    @Inject(method = "tickChunks", at = @At("TAIL"))
    private void polymer$sendChunkUpdates(CallbackInfo ci) {
        this.world.getServer().execute(() -> {
            if (this.polymer$lastUpdates.size() != 0) {
                for (var entry : new ArrayList<>(this.polymer$lastUpdates.object2LongEntrySet())) {
                    var pos = entry.getKey();
                    var time = entry.getLongValue();

                    if (System.currentTimeMillis() - time > 100) {
                        BitSet bitSet = new BitSet();
                        int i = this.lightingProvider.getBottomY();
                        int j = this.lightingProvider.getTopY();
                        int y = pos.getSectionY();
                        if (y >= i && y <= j) {
                            bitSet.set(y - i);
                        }
                        Packet<?> packet = new LightUpdateS2CPacket(pos.toChunkPos(), this.getLightingProvider(), new BitSet(this.world.getTopSectionCoord() + 2), bitSet, true);
                        List<ServerPlayerEntity> players = this.threadedAnvilChunkStorage.getPlayersWatchingChunk(pos.toChunkPos(), false);
                        if (players.size() > 0) {
                            for (ServerPlayerEntity player : players) {
                                player.networkHandler.sendPacket(packet);
                            }
                        }
                        this.polymer$lastUpdates.removeLong(pos);
                    }
                }
            }
        });
    }

    @Inject(method = "onLightUpdate", at = @At("TAIL"))
    private void polymer$scheduleChunkUpdates(LightType type, ChunkSectionPos pos, CallbackInfo ci) {
        if (type == LightType.BLOCK) {
            this.world.getServer().execute(() -> {
                boolean sendUpdate = false;
                boolean safeHasPolymer = false;
                int tooLow = pos.getSectionY() * 16 - 16;
                int tooHigh = pos.getSectionY() * 16 + 32;

                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        var chunk = this.getWorldChunk(pos.getX() + x, pos.getZ() + z);
                        if (chunk != null) {
                            if (!safeHasPolymer) {
                                for (int y = -1; y <= 1; y++) {
                                     if (this.polymer$hadPolymerSource.getBoolean(ChunkSectionPos.from(chunk.getPos(), pos.getSectionY() + y))) {
                                         safeHasPolymer = true;
                                         break;
                                     }
                                }
                            }

                            var iterator = ((PolymerBlockPosStorage) chunk).polymer$iterator();
                            while (iterator.hasNext()){
                                var blockPos = iterator.next();
                                if (blockPos.getY() < tooLow || blockPos.getY() > tooHigh) {
                                    continue;
                                }

                                BlockState blockState = chunk.getBlockState(blockPos);

                                if (PolymerBlockUtils.forceLightUpdates(blockState)) {
                                    sendUpdate = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                boolean update = sendUpdate || PolymerBlockUtils.SEND_LIGHT_UPDATE_PACKET.invoke((c) -> c.test(this.world, pos));

                if (update || safeHasPolymer || this.polymer$hadPolymerSource.getBoolean(pos)) {
                    this.polymer$lastUpdates.put(pos, System.currentTimeMillis());
                    this.polymer$hadPolymerSource.put(pos, update);
                }
            });
        }
    }

    @Override
    public void polymer$setSection(ChunkSectionPos pos, boolean hasPolymer) {
        this.polymer$hadPolymerSource.put(pos, hasPolymer);
    }

    @Override
    public void polymer$removeSection(ChunkSectionPos pos) {
        this.polymer$hadPolymerSource.removeBoolean(pos);
    }
}