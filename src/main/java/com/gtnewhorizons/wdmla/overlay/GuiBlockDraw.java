package com.gtnewhorizons.wdmla.overlay;

import static org.lwjgl.opengl.GL11.*;

import mcp.mobius.waila.overlay.OverlayConfig;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.ForgeHooksClient;

import org.joml.Vector3f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizons.wdmla.util.HotSwapUtil;

/**
 * drawing 3D Block by calling lower level api.
 */
public class GuiBlockDraw {

    public BlockPos renderedBlock;
    private final Vector3f eyePos = new Vector3f(0, 0, -10f);
    private final Vector3f lookAt = new Vector3f(0, 0, 0);
    private final Vector3f worldUp = new Vector3f(0, 1, 0);
    private Vector4i rect = new Vector4i();
    private final RenderBlocks bufferBuilder = new RenderBlocks();

    private static final GuiBlockDraw instance = new GuiBlockDraw();
    public static final float ZOOM = 2.3f;

    public static void drawWorldBlock(int x, int y, int width, int height, int blockX, int blockY, int blockZ,
            float rotationYaw, float rotationPitch) {
        Minecraft mc = Minecraft.getMinecraft();

// Center the camera on the full multi-block shape for doors/beds/double plants.
Block baseBlock = mc.theWorld.getBlock(blockX, blockY, blockZ);
int baseMeta = mc.theWorld.getBlockMetadata(blockX, blockY, blockZ);

int minX = blockX, minY = blockY, minZ = blockZ;
int maxX = blockX, maxY = blockY, maxZ = blockZ;

if (baseBlock instanceof net.minecraft.block.BlockDoublePlant || baseBlock instanceof net.minecraft.block.BlockDoor) {
    if ((baseMeta & 8) == 0) { // lower
        maxY = blockY + 1;
    } else { // upper
        minY = blockY - 1;
    }
} else if (baseBlock instanceof net.minecraft.block.BlockBed) {
    int facing = baseMeta & 3;
    int dx = 0, dz = 0;
    if (facing == 0) dz = 1;
    else if (facing == 1) dx = -1;
    else if (facing == 2) dz = -1;
    else if (facing == 3) dx = 1;

    boolean isHead = (baseMeta & 8) != 0;
    int ox = isHead ? (blockX - dx) : (blockX + dx);
    int oz = isHead ? (blockZ - dz) : (blockZ + dz);

    minX = Math.min(minX, ox);
    maxX = Math.max(maxX, ox);
    minZ = Math.min(minZ, oz);
    maxZ = Math.max(maxZ, oz);
}

// Average of the bounding box of all blocks we will render.
Vector3f center = new Vector3f((minX + maxX) * 0.5f + 0.5f, (minY + maxY) * 0.5f + 0.5f, (minZ + maxZ) * 0.5f + 0.5f);

        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        // compute window size from scaled width & height
        int windowWidth = getScaledX(mc, resolution, width);
        int windowHeight = getScaledY(mc, resolution, height);
        // translate gui coordinates to window's ones (y is inverted)
        int windowX = getScaledX(mc, resolution, x);
        int windowY = mc.displayHeight - getScaledY(mc, resolution, y) - windowHeight;

        instance.renderedBlock = new BlockPos(blockX, blockY, blockZ);
        instance.setCameraLookAt(center, ZOOM, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
        instance.render(windowX, windowY, windowWidth, windowHeight);
    }

    private void render(int x, int y, int width, int height) {
        rect.set(x, y, width, height);
        // setupCamera
        setupCamera();

        // render World
        drawWorld();

        resetCamera();
    }

    public void setCameraLookAt(Vector3f lookAt, double radius, double rotationPitch, double rotationYaw) {
        this.lookAt.set(lookAt);
        eyePos.set((float) Math.cos(rotationPitch), 0, (float) Math.sin(rotationPitch))
                .add(0, (float) (Math.tan(rotationYaw) * eyePos.length()), 0).normalize().mul((float) radius)
                .add(lookAt);
    }

    private static int getScaledX(Minecraft mc, ScaledResolution res, int x) {
        return (int) (x / (res.getScaledWidth() * 1.0) * mc.displayWidth);
    }

    private static int getScaledY(Minecraft mc, ScaledResolution res, int y) {
        return (int) (y / (res.getScaledHeight() * 1.0) * mc.displayHeight);
    }

    public void setupCamera() {
        int x = rect.x;
        int y = rect.y;
        int width = rect.z;
        int height = rect.w;

        Minecraft mc = Minecraft.getMinecraft();
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glPushClientAttrib(GL_ALL_CLIENT_ATTRIB_BITS);
        mc.entityRenderer.disableLightmap(0);
        glDisable(GL_LIGHTING);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);

        // setup viewport and clear GL buffers
        glViewport(x, y, width, height);

        scissorView(x, y, width, height);

        // setup projection matrix to perspective
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();

        float aspectRatio = width / (height * 1.0f);
        HotSwapUtil.gluPerspective(60.0f, aspectRatio, 0.1f, 10000.0f);

        // setup modelview matrix
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        HotSwapUtil
                .gluLookat(eyePos.x, eyePos.y, eyePos.z, lookAt.x, lookAt.y, lookAt.z, worldUp.x, worldUp.y, worldUp.z);
    }

    protected void scissorView(int x, int y, int width, int height) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void resetCamera() {
        // reset viewport
        Minecraft minecraft = Minecraft.getMinecraft();
        glViewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);

        // reset modelview matrix
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        // reset projection matrix
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

        glMatrixMode(GL_MODELVIEW);

        // reset attributes
        glPopClientAttrib();
        glPopAttrib();
    }

    protected void drawWorld() {

        Minecraft mc = Minecraft.getMinecraft();
        float fancyScale = OverlayConfig.fancyBlockScale;
        if (fancyScale <= 0.0f) fancyScale = 1.0f;
        glPushMatrix();
        glTranslatef(lookAt.x, lookAt.y, lookAt.z);
        glScalef(fancyScale, fancyScale, fancyScale);
        glTranslatef(-lookAt.x, -lookAt.y, -lookAt.z);

        glEnable(GL_CULL_FACE);
        glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        mc.entityRenderer.disableLightmap(0);
        mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
        glDisable(GL_LIGHTING);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);

        Tessellator tessellator = Tessellator.instance;
        renderBlocks(tessellator, renderedBlock);

        RenderHelper.enableStandardItemLighting();
        glEnable(GL_LIGHTING);

        // render TESR
        TileEntityRendererDispatcher tesr = TileEntityRendererDispatcher.instance;
        for (int pass = 0; pass < 2; pass++) {
            ForgeHooksClient.setRenderPass(pass);
            int finalPass = pass;

            int x = renderedBlock.x;
            int y = renderedBlock.y;
            int z = renderedBlock.z;
            setDefaultPassRenderState(finalPass);
            TileEntity tile = Minecraft.getMinecraft().theWorld.getTileEntity(x, y, z);
            if (tile != null && tesr.hasSpecialRenderer(tile)) {
                if (tile.shouldRenderInPass(finalPass)) {
                    tesr.renderTileEntityAt(tile, x, y, z, 0);
                }
            }
        }
        ForgeHooksClient.setRenderPass(-1);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glPopMatrix();
        glDepthMask(true);
    }

    public void renderBlocks(Tessellator tessellator, BlockPos blocksToRender) {
        if (blocksToRender == null) return;

        Minecraft mc = Minecraft.getMinecraft();

        tessellator.startDrawingQuads();
        try {
            // Full-bright lightmap for HUD rendering.
            tessellator.setBrightness(0x00F000F0);

            // Use a full-bright IBlockAccess wrapper, but do NOT mutate global GameSettings (prevents dynamic light flicker).
            bufferBuilder.blockAccess = new HudIsolatedFullBrightBlockAccess(mc.theWorld, blocksToRender.x, blocksToRender.y, blocksToRender.z);
            bufferBuilder.enableAO = false;
            bufferBuilder.renderAllFaces = true;

            // Determine which world positions to render (some blocks span multiple blocks).
final java.util.ArrayList<BlockPos> toRender = new java.util.ArrayList<BlockPos>(2);
toRender.add(blocksToRender);

Block baseBlock = mc.theWorld.getBlock(blocksToRender.x, blocksToRender.y, blocksToRender.z);
int baseMeta = mc.theWorld.getBlockMetadata(blocksToRender.x, blocksToRender.y, blocksToRender.z);

if (baseBlock instanceof net.minecraft.block.BlockDoublePlant || baseBlock instanceof net.minecraft.block.BlockDoor) {
    if ((baseMeta & 8) == 0) toRender.add(new BlockPos(blocksToRender.x, blocksToRender.y + 1, blocksToRender.z));
    else toRender.add(new BlockPos(blocksToRender.x, blocksToRender.y - 1, blocksToRender.z));
} else if (baseBlock instanceof net.minecraft.block.BlockBed) {
    int facing = baseMeta & 3;
    int dx = 0, dz = 0;
    if (facing == 0) dz = 1;
    else if (facing == 1) dx = -1;
    else if (facing == 2) dz = -1;
    else if (facing == 3) dx = 1;

    boolean isHead = (baseMeta & 8) != 0;
    int ox = isHead ? (blocksToRender.x - dx) : (blocksToRender.x + dx);
    int oz = isHead ? (blocksToRender.z - dz) : (blocksToRender.z + dz);
    toRender.add(new BlockPos(ox, blocksToRender.y, oz));
}

for (int pass = 0; pass < 2; pass++) {
    for (int i = 0; i < toRender.size(); i++) {
        BlockPos p = toRender.get(i);
        int x = p.x;
        int y = p.y;
        int z = p.z;

        Block block = mc.theWorld.getBlock(x, y, z);
        if (block == null || block == Blocks.air || !block.canRenderInPass(pass)) continue;


        // Ensure the block's bounds match its in-world state (fixes slabs/stairs/etc rendering as full cubes).
        block.setBlockBoundsBasedOnState(bufferBuilder.blockAccess, x, y, z);
        bufferBuilder.setRenderBoundsFromBlock(block);

        // For standard cube rendering, only use the "standard" path if the block actually renders as a normal block.
        // Some TESR-based blocks (e.g. DeepResonance crystals) report renderType == 0 but renderAsNormalBlock() == false.
        // Rendering them as a standard cube makes them appear as a full block in the HUD.
        int rt = block.getRenderType();
        if (rt == 0) {
            // Many vanilla-style blocks (glass, leaves, etc.) return renderAsNormalBlock()==false but still
            // render correctly via the standard block renderer. The main case we want to avoid here is
            // TESR-based blocks that also report renderType==0 (e.g. DeepResonance crystals).
            int meta = mc.theWorld.getBlockMetadata(x, y, z);
            if (block.renderAsNormalBlock() || !block.hasTileEntity(meta)) {
                bufferBuilder.renderStandardBlock(block, x, y, z);
            } else {
                // Skip cube rendering; the TESR (if any) will render the proper model.
                continue;
            }
        } else {
            bufferBuilder.renderBlockByRenderType(block, x, y, z);
        }
    }
}
        } finally {
            tessellator.draw();
            tessellator.setTranslation(0, 0, 0);
        }
    }

private static final class HudIsolatedFullBrightBlockAccess implements net.minecraft.world.IBlockAccess {
    private final net.minecraft.world.IBlockAccess delegate;
    private final java.util.HashSet<Long> include;

    private HudIsolatedFullBrightBlockAccess(net.minecraft.world.IBlockAccess delegate, int cx, int cy, int cz) {
        this.delegate = delegate;
        this.include = new java.util.HashSet<Long>(4);

        // Always include the target block.
        includePos(cx, cy, cz);

        // Include linked halves for multi-block plants/doors/beds so they can render correctly in isolation.
        net.minecraft.block.Block b = delegate.getBlock(cx, cy, cz);
        int meta = delegate.getBlockMetadata(cx, cy, cz);

        if (b instanceof net.minecraft.block.BlockDoublePlant) {
            // Bit 3 (8) indicates TOP half.
            if ((meta & 8) == 0) includePos(cx, cy + 1, cz);
            else includePos(cx, cy - 1, cz);
        } else if (b instanceof net.minecraft.block.BlockDoor) {
            if ((meta & 8) == 0) includePos(cx, cy + 1, cz);
            else includePos(cx, cy - 1, cz);
        } else if (b instanceof net.minecraft.block.BlockBed) {
            // Bed uses head/foot flag (8) and facing in the lower bits.
            int facing = meta & 3;
            int dx = 0, dz = 0;
            if (facing == 0) dz = 1;
            else if (facing == 1) dx = -1;
            else if (facing == 2) dz = -1;
            else if (facing == 3) dx = 1;

            if ((meta & 8) == 0) includePos(cx + dx, cy, cz + dz);
            else includePos(cx - dx, cy, cz - dz);
        }
    }

    private void includePos(int x, int y, int z) {
        include.add((((long)x & 0x3FFFFFFL) << 38) | (((long)z & 0x3FFFFFFL) << 12) | ((long)y & 0xFFFL));
    }

    private boolean isIncluded(int x, int y, int z) {
        long key = (((long)x & 0x3FFFFFFL) << 38) | (((long)z & 0x3FFFFFFL) << 12) | ((long)y & 0xFFFL);
        return include.contains(key);
    }
    @Override
    public net.minecraft.block.Block getBlock(int x, int y, int z) {
        if (!isIncluded(x, y, z)) return net.minecraft.init.Blocks.air;
        return delegate.getBlock(x, y, z);
    }

    @Override
    public net.minecraft.tileentity.TileEntity getTileEntity(int x, int y, int z) {
        if (!isIncluded(x, y, z)) return null;
        return delegate.getTileEntity(x, y, z);
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int p_72802_4_) {
        // Full-bright lightmap coords (same value vanilla uses for maximum brightness)
        return 0x00F000F0;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        if (!isIncluded(x, y, z)) return 0;
        return delegate.getBlockMetadata(x, y, z);
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return getBlock(x, y, z) == net.minecraft.init.Blocks.air;
    }

    @Override
    public net.minecraft.world.biome.BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return delegate.getBiomeGenForCoords(x, z);
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return delegate.extendedLevelsInChunkCache();
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
        if (!isIncluded(x, y, z)) return 0;
        return delegate.isBlockProvidingPowerTo(x, y, z, side);
    }
    @Override
    public boolean isSideSolid(int x, int y, int z,
                              net.minecraftforge.common.util.ForgeDirection side,
                              boolean _default) {
        if (!isIncluded(x, y, z)) return false;
        return delegate.isSideSolid(x, y, z, side, _default);
    }

}


    public static void setDefaultPassRenderState(int pass) {
            glColor4f(1, 1, 1, 1);
            if (pass == 0) { // SOLID
                glEnable(GL_DEPTH_TEST);
                glDisable(GL_BLEND);
                glDepthMask(true);
            } else { // TRANSLUCENT
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                glDepthMask(false);
            }
        }
}
