package com.gtnewhorizons.wdmla.overlay;

import java.util.ArrayList;

import java.util.List;

import net.minecraft.util.AxisAlignedBB;

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

    private MovingObjectPosition target = null;
    private final Minecraft mc = Minecraft.getMinecraft();

    public void fire() {
        EntityLivingBase viewpoint = mc.renderViewEntity;

        if (viewpoint == null) {
            this.target = null;
            return;
        }

        // Block reach distance remains vanilla so blocks are not affected
        final double blockReach = mc.playerController.getBlockReachDistance();

        // Configurable range for entities (mobs). If misconfigured, fall back to block reach.
        int cfgRange = General.mobHudRange;
        double entityReach = cfgRange > 0 ? cfgRange : blockReach;

        if (entityReach < blockReach) {
            entityReach = blockReach;
        }

        // Try to find an entity (mob) first, using the extended reach
        MovingObjectPosition entityTarget = rayTraceEntities(viewpoint, entityReach, 0.0F);

        if (entityTarget != null && entityTarget.entityHit != null && !shouldHidePlayer(entityTarget.entityHit)) {
            this.target = entityTarget;
            return;
        }

        // Fallback to vanilla-style block ray trace using normal reach distance
        this.target = this.rayTrace(viewpoint, blockReach, 0.0F);
    }

    private static boolean shouldHidePlayer(Entity targetEnt) {
        // Check if entity is player with invisibility effect
        if (targetEnt instanceof EntityPlayer thePlayer) {
            boolean shouldHidePlayerSetting = !ConfigHandler.instance().getConfig("vanilla.show_invisible_players");
            return shouldHidePlayerSetting && thePlayer.isInvisible();
        }
        return false;
    }

    public MovingObjectPosition getTarget() {
        return this.target;
    }

    public ItemStack getTargetStack() {
        return this.target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK ? this.getIdentifierStack() : null;
    }

    public Entity getTargetEntity() {
        return this.target.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY ? this.getIdentifierEntity()
                : null;
    }

    
    private MovingObjectPosition rayTraceEntities(EntityLivingBase viewer, double maxDistance, float partialTicks) {
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

        @SuppressWarnings("unchecked")
        java.util.List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(
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

public MovingObjectPosition rayTrace(EntityLivingBase entity, double par1, float par3) {
        Vec3 vec3 = entity.getPosition(par3);
        Vec3 vec31 = entity.getLook(par3);
        Vec3 vec32 = vec3.addVector(vec31.xCoord * par1, vec31.yCoord * par1, vec31.zCoord * par1);

        if (ConfigHandler.instance().getConfig(Configuration.CATEGORY_GENERAL, Constants.CFG_WAILA_LIQUID, true))
            return entity.worldObj.rayTraceBlocks(vec3, vec32, true);
        else return entity.worldObj.rayTraceBlocks(vec3, vec32, false);
    }

    public ItemStack getIdentifierStack() {
        ArrayList<ItemStack> items = this.getIdentifierItems();

        if (items.isEmpty()) return null;

        items.sort((stack0, stack1) -> stack1.getItemDamage() - stack0.getItemDamage());

        return items.get(0);
    }

    public Entity getIdentifierEntity() {
        return this.target.entityHit;
    }

    public ArrayList<ItemStack> getIdentifierItems() {
        ArrayList<ItemStack> items = new ArrayList<>();

        if (this.target == null) return items;

        World world = mc.theWorld;

        int x = this.target.blockX;
        int y = this.target.blockY;
        int z = this.target.blockZ;

        Block mouseoverBlock = world.getBlock(x, y, z);
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (mouseoverBlock == null) return items;

        if (tileEntity == null) {
            try {
                ItemStack block = new ItemStack(mouseoverBlock, 1, world.getBlockMetadata(x, y, z));

                if (block.getItem() != null) items.add(block);

            } catch (Exception ignored) {}
        }

        if (!items.isEmpty()) return items;

        try {
            ItemStack pick = mouseoverBlock.getPickBlock(this.target, world, x, y, z);
            if (pick != null) items.add(pick);
        } catch (Exception ignored) {}

        if (!items.isEmpty()) return items;

        if (mouseoverBlock instanceof IShearable shearable) {
            if (shearable.isShearable(new ItemStack(Items.shears), world, x, y, z)) {
                items.addAll(shearable.onSheared(new ItemStack(Items.shears), world, x, y, z, 0));
            }
        }

        if (items.isEmpty()) items.add(0, new ItemStack(mouseoverBlock, 1, world.getBlockMetadata(x, y, z)));

        return items;
    }

}
