package com.gtnewhorizons.wdmla.overlay;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

import com.gtnewhorizons.wdmla.config.General;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.common.config.Configuration;

import mcp.mobius.waila.api.impl.ConfigHandler;
import mcp.mobius.waila.utils.Constants;

/**
 * RayTracing class from Waila.<br>
 * Important Note: WailaStack and WailaEntity are no longer considered when retrieving results!(Because they are way too
 * outdated)
 */
public class RayTracing {

    private static RayTracing _instance;

    private RayTracing() {}

    public static RayTracing instance() {
        if (_instance == null) _instance = new RayTracing();
        return _instance;
    }

    private MovingObjectPosition target;
    private Minecraft mc = Minecraft.getMinecraft();

    public MovingObjectPosition getTarget() {
        return this.target;
    }

    public Entity getTargetEntity() {
        return this.target != null ? this.target.entityHit : null;
    }

    public ItemStack getTargetStack() {
        return getIdentifierStack();
    }

    public void fire() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            target = null;
            return;
        }

        // Block reach distance remains vanilla for blocks
        EntityLivingBase viewpoint = mc.renderViewEntity;
        double blockReach = mc.playerController.getBlockReachDistance();

        // Configurable range for entities (mobs). If misconfigured, fall back to block reach.
        int cfgRange = General.mobHudRange;
        double entityReach = cfgRange > 0 ? cfgRange : blockReach;

        if (entityReach < blockReach) {
            entityReach = blockReach;
        }

        // Perform both block and entity ray traces, then decide what to show
        MovingObjectPosition blockTarget = this.rayTrace(viewpoint, blockReach, 0.0F);
        MovingObjectPosition entityTarget = rayTraceEntities(viewpoint, entityReach, 0.0F);

        // Filter out invisible players if configured so
        if (entityTarget != null && entityTarget.entityHit != null && shouldHidePlayer(entityTarget.entityHit)) {
            entityTarget = null;
        }

        World world = viewpoint.worldObj;

        // Decide priority using distance along the ray:
        // - If there is an entity hit and no solid block hit, show the entity.
        // - If there is a block hit and no entity, show the block.
        // - If both exist, compare distances along the view ray, with special handling for HUD-transparent blocks
        //   (glass, liquids, leaves, etc.)
        if (entityTarget != null && entityTarget.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            if (blockTarget == null || blockTarget.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                this.target = entityTarget;
                return;
            }

            // We have both an entity and a block along the ray
            Block block = world.getBlock(blockTarget.blockX, blockTarget.blockY, blockTarget.blockZ);

            // If the block is HUD-transparent (glass, liquids, leaves...), always prefer the entity
            if (isHudTransparentBlock(block, world, blockTarget.blockX, blockTarget.blockY, blockTarget.blockZ)) {
                this.target = entityTarget;
                return;
            }

            // For solid blocks, choose whichever is closer along the view ray.
            // Entity distance
            double entityDistSq;
            if (entityTarget.hitVec != null) {
                entityDistSq = viewpoint.getDistanceSq(
                        entityTarget.hitVec.xCoord,
                        entityTarget.hitVec.yCoord,
                        entityTarget.hitVec.zCoord
                );
            } else if (entityTarget.entityHit != null) {
                entityDistSq = viewpoint.getDistanceSqToEntity(entityTarget.entityHit);
            } else {
                entityDistSq = Double.MAX_VALUE;
            }

            // Block distance
            double blockDistSq;
            if (blockTarget.hitVec != null) {
                blockDistSq = viewpoint.getDistanceSq(
                        blockTarget.hitVec.xCoord,
                        blockTarget.hitVec.yCoord,
                        blockTarget.hitVec.zCoord
                );
            } else {
                // Fallback: center of the block
                double cx = blockTarget.blockX + 0.5D;
                double cy = blockTarget.blockY + 0.5D;
                double cz = blockTarget.blockZ + 0.5D;
                blockDistSq = viewpoint.getDistanceSq(cx, cy, cz);
            }

            // Small epsilon to avoid weird float equality issues
            if (entityDistSq <= blockDistSq + 1.0E-4D) {
                // Entity is in front of the block -> show entity
                this.target = entityTarget;
            } else {
                // Block is closer -> show block
                this.target = blockTarget;
            }
            return;
        }

        // No entity hit; fall back to whatever block (if any) we have
        this.target = blockTarget;
    }

    private static boolean shouldHidePlayer(Entity targetEnt) {
        // Check if entity is player with invisibility effect
        if (targetEnt instanceof EntityPlayer) {
            EntityPlayer thePlayer = (EntityPlayer) targetEnt;
            boolean shouldHidePlayerSetting = !ConfigHandler.instance().getConfig("vanilla.show_invisible_players");
            return shouldHidePlayerSetting && thePlayer.isInvisible();
        }
        return false;
    }

    public MovingObjectPosition rayTrace(EntityLivingBase entity, double par1, float par3) {
        Vec3 vec3 = entity.getPosition(par3);
        Vec3 vec31 = entity.getLook(par3);
        Vec3 vec32 = vec3.addVector(vec31.xCoord * par1, vec31.yCoord * par1, vec31.zCoord * par1);

        if (ConfigHandler.instance().getConfig(Configuration.CATEGORY_GENERAL, Constants.CFG_WAILA_LIQUID, true)) {
            return entity.worldObj.rayTraceBlocks(vec3, vec32, true);
        } else {
            return entity.worldObj.rayTraceBlocks(vec3, vec32);
        }
    }

    MovingObjectPosition rayTraceEntities(EntityLivingBase viewer, double maxDistance, float partialTicks) {
        if (viewer == null || viewer.worldObj == null) {
            return null;
        }

        World world = viewer.worldObj;

        Vec3 eyePos = viewer.getPosition(partialTicks);
        Vec3 lookVec = viewer.getLook(partialTicks);
        Vec3 reachVec = eyePos.addVector(
                lookVec.xCoord * maxDistance,
                lookVec.yCoord * maxDistance,
                lookVec.zCoord * maxDistance
        );

        Entity closestEntity = null;
        Vec3 hitVec = null;
        double closestDistance = maxDistance;

        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(
                viewer,
                viewer.boundingBox.addCoord(
                        lookVec.xCoord * maxDistance,
                        lookVec.yCoord * maxDistance,
                        lookVec.zCoord * maxDistance
                ).expand(1.0D, 1.0D, 1.0D)
        );

        for (Entity candidate : entities) {
            if (!candidate.canBeCollidedWith()) {
                continue;
            }
            if (shouldHidePlayer(candidate)) {
                continue;
            }

            float border = candidate.getCollisionBorderSize();
            AxisAlignedBB aabb = candidate.boundingBox.expand(border, border, border);
            MovingObjectPosition intercept = aabb.calculateIntercept(eyePos, reachVec);

            // If there's any *solid* block between us and this entity along the look vector,
            // consider the entity occluded and skip it. Transparent blocks (glass, liquids, etc.)
            // are ignored so mobs can still be targeted through them.
            if (intercept != null && isPathBlockedBySolid(world, eyePos, intercept.hitVec)) {
                continue;
            }

            if (aabb.isVecInside(eyePos)) {
                if (0.0D < closestDistance) {
                    closestEntity = candidate;
                    hitVec = (intercept == null) ? eyePos : intercept.hitVec;
                    closestDistance = 0.0D;
                }
            } else if (intercept != null) {
                double distanceToHit = eyePos.distanceTo(intercept.hitVec);

                if (distanceToHit < closestDistance || closestDistance == 0.0D) {
                    closestEntity = candidate;
                    hitVec = intercept.hitVec;
                    closestDistance = distanceToHit;
                }
            }
        }

        if (closestEntity != null) {
            MovingObjectPosition result = new MovingObjectPosition(closestEntity);
            result.hitVec = hitVec;
            return result;
        }

        return null;
    }

    public MovingObjectPosition getMouseOver() {
        return target;
    }

    public ArrayList<ItemStack> getIdentifierItems() {
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        MovingObjectPosition position = this.target;

        if (position == null) return items;

        if (position.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            int x = position.blockX;
            int y = position.blockY;
            int z = position.blockZ;
            World world = mc.theWorld;
            Block mouseoverBlock = world.getBlock(x, y, z);

            if (mouseoverBlock == null || mouseoverBlock.isAir(world, x, y, z)) return items;

            if (mouseoverBlock instanceof IShearable) {
                items.addAll(
                        ((IShearable) mouseoverBlock)
                                .onSheared(new ItemStack(Items.shears), world, x, y, z, 0));
            }

            if (items.isEmpty()) items.add(0, new ItemStack(mouseoverBlock, 1, world.getBlockMetadata(x, y, z)));

            return items;
        }

        if (position.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && position.entityHit instanceof EntityLivingBase) {
            EntityLivingBase entity = (EntityLivingBase) position.entityHit;
            ArrayList<ItemStack> tmp = new ArrayList<ItemStack>();

            tmp.add(entity.getHeldItem());
            for (int i = 0; i < 4; i++) {
                tmp.add(entity.getEquipmentInSlot(i));
            }
            for (ItemStack stack : tmp) {
                if (stack != null) items.add(stack);
            }

            TileEntity te = entity.worldObj.getTileEntity(
                    MathHelper.floor_double(entity.posX),
                    MathHelper.floor_double(entity.posY),
                    MathHelper.floor_double(entity.posZ));
            if (te != null) {
                try {
                    ItemStack pick = te.getBlockType().getPickBlock(
                            RayTracing.instance().getTarget(), te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord);
                    if (pick != null) {
                        items.add(pick);
                    }
                } catch (Throwable ignored) {}
            }
        }

        return items;
    }

    public ItemStack getIdentifierStack() {
        ArrayList<ItemStack> items = this.getIdentifierItems();

        if (items.isEmpty()) return null;

        items.sort((stack0, stack1) -> stack1.getItemDamage() - stack0.getItemDamage());

        return items.get(0);
    }

    public Entity getIdentifierEntity() {
        return this.target != null ? this.target.entityHit : null;
    }


    private boolean isPathBlockedBySolid(World world, Vec3 start, Vec3 end) {
        if (world == null || start == null || end == null) {
            return false;
        }

        Vec3 currentStart = start;

        boolean stopOnLiquid = ConfigHandler.instance().getConfig(
                Configuration.CATEGORY_GENERAL,
                Constants.CFG_WAILA_LIQUID,
                true
        );

        for (int i = 0; i < 8; i++) {
            MovingObjectPosition blockHit = world.rayTraceBlocks(currentStart, end, stopOnLiquid);

            if (blockHit == null || blockHit.hitVec == null) {
                return false;
            }

            Block hitBlock = world.getBlock(blockHit.blockX, blockHit.blockY, blockHit.blockZ);
            if (!isHudTransparentBlock(hitBlock, world, blockHit.blockX, blockHit.blockY, blockHit.blockZ)) {
                return true;
            }

            double remainingSq = blockHit.hitVec.squareDistanceTo(end);
            if (remainingSq < 1.0E-4D) {
                return false;
            }

            double dirX = end.xCoord - blockHit.hitVec.xCoord;
            double dirY = end.yCoord - blockHit.hitVec.yCoord;
            double dirZ = end.zCoord - blockHit.hitVec.zCoord;
            double length = MathHelper.sqrt_double(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (length < 1.0E-6D) {
                return false;
            }

            dirX /= length;
            dirY /= length;
            dirZ /= length;

            currentStart = blockHit.hitVec.addVector(dirX * 1.0E-3D, dirY * 1.0E-3D, dirZ * 1.0E-3D);
        }

        // If we somehow hit ~6 transparent blocks in a row, just bail out and say blocked rather than spam more ray traces.
        return true;
    }

    /**
     * Determines whether a block should be treated as transparent for HUD raytracing purposes.
     * This allows entities to be targeted through glass, liquids, leaves, etc., while still
     * blocking entities that are actually behind solid walls.
     */
    private boolean isHudTransparentBlock(Block block, World world, int x, int y, int z) {
        if (block == null) {
            return false;
        }

        try {
            if (block.getMaterial() != null && block.getMaterial().isLiquid()) {
                return true;
            }

            // Non-opaque blocks (glass, panes, leaves, fences, etc.) are considered see-through
            if (!block.isOpaqueCube()) {
                return true;
            }

        } catch (Throwable ignored) {}

        return false;
    }
}
