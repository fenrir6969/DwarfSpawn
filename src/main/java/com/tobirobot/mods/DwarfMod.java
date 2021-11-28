package com.tobirobot.mods;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.event.world.WorldEvent.CreateSpawnPosition;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("dwarfmod")
public class DwarfMod {
    private static final Logger log = LogManager.getLogger();

    public DwarfMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCreateSpawnPosition(CreateSpawnPosition e) {
        log.debug("CreateSpawnPosition isRemote={}", e.getWorld().isClientSide());
        if (!e.getWorld().isClientSide() && e.getWorld() instanceof ServerWorld) {
            ServerWorld world = (ServerWorld)e.getWorld();
            BlockPos spawnPos = this.findSafeCaveAirBlock(world);
            if (spawnPos == null) {
                spawnPos = new BlockPos(0, 256, 1000);
            }

            log.debug("Setting spawn point to {}", spawnPos);
            world.setDefaultSpawnPos(spawnPos, 1.0F);
            e.setCanceled(true);
        }
    }

    private boolean isRemote(World world) {
        // TODO - you need to figure out how to tell if we're remote properly
        // this seemed to have worked once?
        // boolean isRemote = e.getWorld().func_201670_d();
        // Maybe this works?
        return !world.isClientSide();
    }

    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent e) {
        boolean isPlayer = e.getEntity() instanceof PlayerEntity;
        boolean isRemote = isRemote(e.getWorld());
        boolean isServerWorld = e.getWorld() instanceof ServerWorld;
        if (isPlayer) {
            log.debug("EntityJoinWorldEvent isRemote={} isPlayer={} isServerWorld={}", isRemote, isPlayer, isServerWorld);
            if (!isRemote(e.getWorld()) && isServerWorld) {
                PlayerEntity player = (PlayerEntity)e.getEntity();
                ServerWorld world = (ServerWorld)e.getWorld();
                BlockPos spawnPos = world.getSharedSpawnPos();
                log.debug("EntityJoinWorldEvent player.setPos to {}", spawnPos);
                if (isFirstJoin(player)) {
                    player.setPos((double)spawnPos.getX(), (double)spawnPos.getY(), (double)spawnPos.getZ());
                }

            }
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerRespawnEvent e) {
        log.debug("PlayerRespawnEvent");
        PlayerEntity player = e.getPlayer();
        World world = player.level;
        if (world instanceof ServerWorld && !world.isClientSide()) {
            Vector3d bedPos = this.getBedPos(player, world);
            if (bedPos != null) {
                player.setPos(bedPos.x, bedPos.y, bedPos.z);
            } else {
                BlockPos spawnPos = ((ServerWorld)world).getSharedSpawnPos();
                player.setPos((double)spawnPos.getX(), (double)spawnPos.getY(), (double)spawnPos.getZ());
            }

        }
    }

    private BlockPos findSafeCaveAirBlock(IWorld world) {
        for(int x = 0; x < 256; ++x) {
            BlockPos startPos = new BlockPos(x, 0, 1000);
            BlockPos safePos = this.findLowestSafeCaveAirBlock(world, startPos);
            if (safePos != null) {
                return safePos;
            }
        }

        return null;
    }

    private BlockPos findLowestSafeCaveAirBlock(IWorld world, BlockPos startPos) {
        String CAVEAIR = Blocks.CAVE_AIR.toString();
        String AIR = Blocks.AIR.toString();
        String LAVA = Blocks.LAVA.toString();

        for(int y = 0; y < 256; ++y) {
            BlockPos blockPos = new BlockPos(startPos.getX(), y, startPos.getZ());
            Block block = world.getBlockState(blockPos).getBlock();
            log.debug("Spawn check: y={} block={}", y, block);
            if (block.toString().equals(CAVEAIR)) {
                BlockPos floorBlockPos = new BlockPos(startPos.getX(), y - 1, startPos.getZ());
                Block floorBlock = world.getBlockState(floorBlockPos).getBlock();
                if (!floorBlock.toString().equals(LAVA) && !floorBlock.toString().equals(CAVEAIR)) {
                    BlockPos headBlockPos = new BlockPos(startPos.getX(), y + 1, startPos.getZ());
                    Block headBlock = world.getBlockState(headBlockPos).getBlock();
                    if (headBlock.toString().equals(CAVEAIR)) {
                        return blockPos;
                    }
                }
            } else if (block.toString().equals(AIR)) {
                return null;
            }
        }

        return null;
    }

    public static boolean isFirstJoin(PlayerEntity player) {
        String firstjointag = "dwarfspawn.firstJoin.";
        Set<String> tags = player.getTags();
        if (tags.contains(firstjointag)) {
            return false;
        } else {
            player.addTag(firstjointag);
            return true;
        }
    }

    public Vector3d getBedPos(PlayerEntity player, World world) {
        String CAVEAIR = Blocks.CAVE_AIR.toString();
        ServerPlayerEntity serverplayer = (ServerPlayerEntity)player;
        ServerWorld serverworld = (ServerWorld)world;
        BlockPos bedpos = serverplayer.getRespawnPosition();
        if (bedpos == null) {
            return null;
        } else {
            Optional<Vector3d> optionalbed = PlayerEntity.findRespawnPositionAndUseSpawnBlock(serverworld, bedpos, 1.0F, false, false);
            if (!optionalbed.isPresent()) {
                return null;
            } else {
                Vector3d bedlocation = (Vector3d)optionalbed.get();
                BlockPos bl = new BlockPos(bedlocation.x(), bedlocation.y(), bedlocation.z());
                Iterator it = BlockPos.betweenClosedStream(bl.getX()-1, bl.getY()-1, bl.getZ()-1, bl.getX()+1, bl.getY()+1, bl.getZ()+1).iterator();

                while(it.hasNext()) {
                    BlockPos blockPos = (BlockPos)it.next();
                    Block block = world.getBlockState(blockPos).getBlock();
                    if (block.toString().equals(CAVEAIR)) {
                        bedlocation = new Vector3d((double)blockPos.getX() + 0.5D, (double)blockPos.getY(), (double)blockPos.getZ() + 0.5D);
                        break;
                    }
                }

                return new Vector3d(bedlocation.x(), bedlocation.y(), bedlocation.z());
            }
        }
    }
}
