package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.entitiy.PlayerModel; // Ensure correct package
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.opengl.GL11.*;

public class Renderer {
    private final CameraManager camera;
    private final Map map;
    private final PlayerModel player;
    private final InputHandler inputHandler;
    private int frameCount = 0;
    private Texture playerTexture;
    private Texture treeTexture;
    private Font uiFont;
    private Random tileDetailRandom;

    // Helper class/record for items that need sorting
    private static class SortableItem {
        float screenYSortKey;
        int zOrder; // 0 for trees, 1 for player
        Object entity; // Can be PlayerModel or TreeData

        // Constructor for Player
        public SortableItem(PlayerModel player, CameraManager camera, Map gameMap) {
            this.entity = player;
            this.zOrder = 1; // Player (drawn after trees on same "depth")

            Tile currentTile = gameMap.getTile(player.getTileRow(), player.getTileCol());
            int playerBaseElevation = (currentTile != null) ? currentTile.getElevation() : 0;

            // Refined sort key: screenY of base + mapRow for tie-breaking depth
            // You might also add mapCol, or use a combined mapRow + mapCol as primary
            // and screenY of feet as secondary. Let's try screenY + mapRow.
            int[] screenCoords = camera.mapToScreenCoords(player.getMapCol(), player.getMapRow(), playerBaseElevation);

            // A common robust sort key: sum of map coordinates, then elevation, then screenY of feet.
            // The visual "depth" is often related to mapRow + mapCol.
            // Higher elevation things on the same (r,c) sum should be drawn later if they obscure lower things.
            // However, our current sort is ascending Y.

            // Let's try using the raw screenY of the player's feet for now,
            // but we need a better tie-breaker for entities on the same visual line.
            // The issue is more likely tree-vs-tree which have zOrder = 0.

            // Sort key idea: prioritize map depth, then elevation, then final Y.
            // For painter's algorithm (small Y first), we need things further back to have smaller sort keys.
            // mapRow is a good indicator of "further back" for things on the same isometric diagonal.
            // mapCol contributes too. Sum (mapRow + mapCol) is a good depth indicator.
            // Higher Y values are closer to the viewer.

            float visualDepth = (player.getMapRow() + player.getMapCol()) * 1000; // Prioritize map depth
            visualDepth += playerBaseElevation * 10; // Then elevation
            // screenCoords[1] is screenY. For sorting, smaller screenY is further.
            // We need a consistent sort key. Let's use screenY of the base for now and refine if needed.
            this.screenYSortKey = screenCoords[1]; // Y-coordinate of the player's base on the tile
            // We can add a sub-sort for things on the exact same Y.
            // For entities on the same tile, player (zOrder 1) comes after tree (zOrder 0)
        }

        // Constructor for Tree (using TreeData)
        // Constructor for Tree (using TreeData)
        public SortableItem(TreeData tree, CameraManager camera, Map gameMap) {
            this.entity = tree;
            this.zOrder = 0; // Tree

            // The screenCoords for a tree should be its base on its tile.
            int[] screenCoords = camera.mapToScreenCoords(tree.mapCol, tree.mapRow, tree.elevation);
            this.screenYSortKey = screenCoords[1]; // Y-coordinate of the tree's base on the tile

            // To better sort trees against trees, especially when their bases have similar screenY
            // due to elevation differences compensating for map depth, we need a tie-breaker.
            // A common tie-breaker is the sum of map coordinates (row + col).
            // We want items with a smaller (mapRow + mapCol) sum to be drawn first if screenY is equal.
            // However, our current sort comparator handles this by zOrder, which is the same for all trees.

            // Let's modify the screenYSortKey to incorporate map depth for tie-breaking.
            // A simple way: add a small fraction based on map depth.
            // If mapRow + mapCol is smaller, it's further away.
            // We sort by screenYSortKey ascending (smaller Y first).
            // If two tree bases are at the same screenY, the one with smaller (mapRow+mapCol) should be drawn first.
            // This means its effective screenYSortKey should be slightly smaller.
            // this.screenYSortKey = screenCoords[1] - (tree.mapRow + tree.mapCol) * 0.001f; // Subtract to make "further" trees have smaller Y
            // The above is a bit hacky. Let's adjust the comparator.
        }
    }

    // Lightweight class to hold tree data for sorting and rendering
    private static class TreeData {
        Tile.TreeVisualType treeVisualType;
        float mapCol, mapRow;
        int elevation;
        float topDiamondCenterX, topDiamondCenterY_tileTip, topDiamondIsoHeight_tileFace;

        public TreeData(Tile.TreeVisualType type, float tileCol, float tileRow, int tileElev,
                        float topDiamondCenterX, float topDiamondCenterY_tileTip, float topDiamondIsoHeight_tileFace) {
            this.treeVisualType = type;
            this.mapCol = tileCol;
            this.mapRow = tileRow;
            this.elevation = tileElev;
            this.topDiamondCenterX = topDiamondCenterX;
            this.topDiamondCenterY_tileTip = topDiamondCenterY_tileTip;
            this.topDiamondIsoHeight_tileFace = topDiamondIsoHeight_tileFace;
        }
    }

    private List<SortableItem> sortableItems = new ArrayList<>();

    public Renderer(CameraManager camera, Map map, PlayerModel player, InputHandler inputHandler) {
        this.camera = camera;
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler;
        this.tileDetailRandom = new Random();
        loadAssets();
    }

    private void loadAssets() {
        // Ensure these paths are correct relative to your working directory or classpath
        String playerTexturePath = "org/isogame/render/textures/lpc_character.png";
        this.playerTexture = Texture.loadTexture(playerTexturePath);
        if (this.playerTexture == null) System.err.println("CRITICAL: Player texture failed to load from: " + playerTexturePath);


        String treeTexturePath = "org/isogame/render/textures/fruit-trees.png";
        this.treeTexture = Texture.loadTexture(treeTexturePath);
        if (this.treeTexture == null) System.err.println("CRITICAL: Tree texture failed to load from: " + treeTexturePath);

        String fontPath = "org/isogame/render/fonts/PressStart2P-Regular.ttf";
        try {
            this.uiFont = new Font(fontPath, 16f); // Assuming Font constructor takes this path
        } catch (IOException e) {
            System.err.println("CRITICAL: Failed to load UI font from: " + fontPath + ". " + e.getMessage());
            this.uiFont = null;
        }
    }

    public void onResize(int fbWidth, int fbHeight) {
        if (fbWidth <= 0 || fbHeight <= 0) return;
        glViewport(0, 0, fbWidth, fbHeight);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, fbWidth, fbHeight, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    public void render() {
        frameCount++;
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        sortableItems.clear();
        collectSortableItems();

        Collections.sort(sortableItems, new Comparator<SortableItem>() {
            @Override
            public int compare(SortableItem item1, SortableItem item2) {
                // Primary sort: screen Y coordinate (smaller Y is further up/back, drawn first)
                if (Math.abs(item1.screenYSortKey - item2.screenYSortKey) > 0.1f) { // Use a small epsilon for float comparison
                    return Float.compare(item1.screenYSortKey, item2.screenYSortKey);
                }

                // Secondary sort: "depth" in the map. (mapRow + mapCol)
                // Items with a smaller sum of (mapRow + mapCol) are generally "behind"
                // those with a larger sum, if they are on the same visual Y-line.
                // We need to get mapRow and mapCol from the entity.
                float depth1 = 0, depth2 = 0;
                int elev1 = 0, elev2 = 0;

                if (item1.entity instanceof PlayerModel) {
                    PlayerModel p = (PlayerModel) item1.entity;
                    depth1 = p.getMapRow() + p.getMapCol();
                    Tile t = map.getTile(p.getTileRow(), p.getTileCol());
                    if (t != null) elev1 = t.getElevation();
                } else if (item1.entity instanceof TreeData) {
                    TreeData td = (TreeData) item1.entity;
                    depth1 = td.mapRow + td.mapCol;
                    elev1 = td.elevation;
                }

                if (item2.entity instanceof PlayerModel) {
                    PlayerModel p = (PlayerModel) item2.entity;
                    depth2 = p.getMapRow() + p.getMapCol();
                    Tile t = map.getTile(p.getTileRow(), p.getTileCol());
                    if (t != null) elev2 = t.getElevation();
                } else if (item2.entity instanceof TreeData) {
                    TreeData td = (TreeData) item2.entity;
                    depth2 = td.mapRow + td.mapCol;
                    elev2 = td.elevation;
                }

                if (depth1 != depth2) {
                    return Float.compare(depth1, depth2); // Smaller depth sum drawn first
                }

                // Tertiary sort: elevation (for items at same screenY and map depth sum)
                // Higher things might need to be drawn after lower things if they are "on top"
                // Or, if sorting by base, lower elevation things might be further.
                // For now, let's assume if depth and screenY are same, elevation might be a factor.
                // If item1 is at a lower elevation on the same depth line, it should be drawn first.
                if (elev1 != elev2) {
                    return Integer.compare(elev1, elev2); // Lower elevation drawn first
                }

                // Quaternary sort: zOrder (player vs tree on the exact same tile)
                return Integer.compare(item1.zOrder, item2.zOrder); // Tree (0) before Player (1)
            }
        });

        renderMapBaseGeometry();

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        for (SortableItem item : sortableItems) {
            if (item.entity instanceof PlayerModel) {
                drawPlayerInstance((PlayerModel) item.entity);
            } else if (item.entity instanceof TreeData) {
                drawTreeInstance((TreeData) item.entity);
            }
        }
        glDisable(GL_TEXTURE_2D);
        renderUI();
    }

    private void collectSortableItems() {
        if (this.player != null) {
            sortableItems.add(new SortableItem(this.player, this.camera, this.map));
        }

        int mapW = map.getWidth();
        int mapH = map.getHeight();
        for (int r = 0; r < mapH; r++) {
            for (int c = 0; c < mapW; c++) {
                Tile tile = map.getTile(r, c);
                if (tile != null && tile.getTreeType() != Tile.TreeVisualType.NONE && tile.getType() != Tile.TileType.WATER) {
                    int elevation = tile.getElevation();
                    int[] baseScreenCoords = camera.mapToScreenCoords(c, r, 0);
                    int effWidth = camera.getEffectiveTileWidth();
                    int effHeight = camera.getEffectiveTileHeight();
                    int effThickness = camera.getEffectiveTileThickness();

                    float topDiamondDrawCenterX;
                    float topDiamondDrawTipY;
                    float topDiamondDrawIsoHeight_tileFace;

                    if (elevation > 0) {
                        topDiamondDrawTipY = baseScreenCoords[1] - (elevation * effThickness);
                    } else {
                        topDiamondDrawTipY = baseScreenCoords[1];
                    }
                    topDiamondDrawCenterX = baseScreenCoords[0] + effWidth / 2.0f;
                    topDiamondDrawIsoHeight_tileFace = effHeight;

                    TreeData treeData = new TreeData(tile.getTreeType(), (float)c, (float)r, elevation,
                            topDiamondDrawCenterX, topDiamondDrawTipY, topDiamondDrawIsoHeight_tileFace);
                    sortableItems.add(new SortableItem(treeData, this.camera, this.map));
                }
            }
        }
    }

    private void drawPlayerInstance(PlayerModel p) {
        if (playerTexture == null) return;

        Tile currentTile = map.getTile(p.getTileRow(), p.getTileCol());
        int playerBaseElevation = (currentTile != null) ? currentTile.getElevation() : 0;
        int[] playerScreenBaseCoords = camera.mapToScreenCoords(p.getMapCol(), p.getMapRow(), playerBaseElevation);
        float screenX = playerScreenBaseCoords[0];
        float screenY = playerScreenBaseCoords[1];

        playerTexture.bind();
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        int frameWidthPx = PlayerModel.FRAME_WIDTH;
        int frameHeightPx = PlayerModel.FRAME_HEIGHT;
        int animFrameCol = p.getVisualFrameIndex();
        int animFrameRow = p.getAnimationRow();

        float texSrcX = animFrameCol * frameWidthPx;
        float texSrcY = animFrameRow * frameHeightPx;
        float texSheetWidth = playerTexture.getWidth();
        float texSheetHeight = playerTexture.getHeight();

        float u0 = texSrcX / texSheetWidth;
        float v0 = texSrcY / texSheetHeight;
        float u1 = (texSrcX + frameWidthPx) / texSheetWidth;
        float v1 = (texSrcY + frameHeightPx) / texSheetHeight;

        float scaledSpriteWidth = frameWidthPx * camera.getZoom();
        float scaledSpriteHeight = frameHeightPx * camera.getZoom();
        float drawX = screenX - (scaledSpriteWidth / 2.0f);
        float drawY = screenY - scaledSpriteHeight;

        if (p.isLevitating()) {
            drawY -= (int) (Math.sin(p.getLevitateTimer()) * 8 * camera.getZoom());
        }

        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0); glVertex2f(drawX, drawY);
        glTexCoord2f(u1, v0); glVertex2f(drawX + scaledSpriteWidth, drawY);
        glTexCoord2f(u1, v1); glVertex2f(drawX + scaledSpriteWidth, drawY + scaledSpriteHeight);
        glTexCoord2f(u0, v1); glVertex2f(drawX, drawY + scaledSpriteHeight);
        glEnd();
    }

    private void drawTreeInstance(TreeData tree) {
        if (treeTexture == null || tree.treeVisualType == Tile.TreeVisualType.NONE) return;

        treeTexture.bind();
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        float u0=0,v0=0,u1=0,v1=0;
        float treeFrameWidthSpritePx=0, treeFrameHeightSpritePx=0;
        float anchorOffsetYFromVisualBase=0;

        switch(tree.treeVisualType){
            case APPLE_TREE_FRUITING:
                treeFrameWidthSpritePx=90.0f; treeFrameHeightSpritePx=130.0f;
                float appleSX=0.0f,appleSY=0.0f; anchorOffsetYFromVisualBase=10.0f;
                u0=appleSX/treeTexture.getWidth(); v0=appleSY/treeTexture.getHeight();
                u1=(appleSX+treeFrameWidthSpritePx)/treeTexture.getWidth();
                v1=(appleSY+treeFrameHeightSpritePx)/treeTexture.getHeight(); break;
            case PINE_TREE_SMALL:
                treeFrameWidthSpritePx=90.0f; treeFrameHeightSpritePx=180.0f;
                float pineSX=90.0f,pineSY=0.0f; anchorOffsetYFromVisualBase=5.0f;
                u0=pineSX/treeTexture.getWidth(); v0=pineSY/treeTexture.getHeight();
                u1=(pineSX+treeFrameWidthSpritePx)/treeTexture.getWidth();
                v1=(pineSY+treeFrameHeightSpritePx)/treeTexture.getHeight(); break;
            default: return; // Unknown tree type
        }

        if (treeFrameWidthSpritePx <= 0 || treeFrameHeightSpritePx <= 0) return;

        float artScale = 1.0f;
        float treeDrawWidth = treeFrameWidthSpritePx * camera.getZoom() * artScale;
        float treeDrawHeight = treeFrameHeightSpritePx * camera.getZoom() * artScale;
        float screenAnchorOffsetY = anchorOffsetYFromVisualBase * camera.getZoom() * artScale;

        float visualAnchorYOnTileSurface = tree.topDiamondCenterY_tileTip + (tree.topDiamondIsoHeight_tileFace / 2.0f);
        float drawX = tree.topDiamondCenterX - (treeDrawWidth / 2.0f);
        float drawY = visualAnchorYOnTileSurface - treeDrawHeight + screenAnchorOffsetY;

        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0); glVertex2f(drawX, drawY);
        glTexCoord2f(u1, v0); glVertex2f(drawX + treeDrawWidth, drawY);
        glTexCoord2f(u1, v1); glVertex2f(drawX + treeDrawWidth, drawY + treeDrawHeight);
        glTexCoord2f(u0, v1); glVertex2f(drawX, drawY + treeDrawHeight);
        glEnd();
    }


    // Add a flag to toggle debug drawing
    private boolean DEBUG_DRAW_PICKING_DIAMONDS = false; // Set to true to see them

// ... (rest of Renderer.java up to renderMapBaseGeometry)

    private void renderMapBaseGeometry() {
        int mapW = map.getWidth();
        int mapH = map.getHeight();
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        for (int sum = 0; sum <= mapW + mapH - 2; sum++) {
            for (int r_loop = 0; r_loop <= sum; r_loop++) { // Renamed loop variable to avoid confusion
                int c_loop = sum - r_loop;                 // Renamed loop variable
                if (map.isValid(r_loop, c_loop)) {
                    Tile tile = map.getTile(r_loop, c_loop);
                    if (tile != null) {
                        renderTileGeometry(r_loop, c_loop, tile, (r_loop == inputHandler.getSelectedRow() && c_loop == inputHandler.getSelectedCol()));

                        if (DEBUG_DRAW_PICKING_DIAMONDS) {
                            // Pass the correct loop variables c_loop and r_loop
                            if (Math.abs(r_loop - inputHandler.getSelectedRow()) <= 5 && Math.abs(c_loop - inputHandler.getSelectedCol()) <= 5) {
                                drawPickableDiamondOutline(c_loop, r_loop, tile.getElevation());
                            }
                        }
                    }
                }
            }
        }
    }

    // Corrected debug drawing method in Renderer.java
    // In Renderer.java
    private void drawPickableDiamondOutline(int mapCol, int mapRow, int elevation) {
        int effTileWidth = camera.getEffectiveTileWidth();
        int effTileHeight = camera.getEffectiveTileHeight();

        // mapToScreenCoords now assumed to return the CENTER of the diamond's top face
        int[] diamondCenterScreenCoords = camera.mapToScreenCoords((float)mapCol, (float)mapRow, elevation);
        float centerX = diamondCenterScreenCoords[0];
        float centerY = diamondCenterScreenCoords[1];

        float halfW = effTileWidth / 2.0f;
        float halfH = effTileHeight / 2.0f;

        // Calculate diamond corners from the center
        float pointTopX = centerX;
        float pointTopY = centerY - halfH;
        float pointLeftX = centerX - halfW;
        float pointLeftY = centerY;
        float pointRightX = centerX + halfW;
        float pointRightY = centerY;
        float pointBottomX = centerX;
        float pointBottomY = centerY + halfH;

        glDisable(GL_TEXTURE_2D);
        // Draw the PICKING diamond outline (YELLOW)
        glColor4f(1.0f, 1.0f, 0.0f, 0.7f); // Yellow
        glBegin(GL_LINE_LOOP);
        glVertex2f(pointTopX, pointTopY);
        glVertex2f(pointLeftX, pointLeftY);
        glVertex2f(pointBottomX, pointBottomY);
        glVertex2f(pointRightX, pointRightY);
        glEnd();

        // Draw the CYAN debug dot AT THE CENTER (which is now directly from mapToScreenCoords)
        glColor4f(0.0f, 1.0f, 1.0f, 0.8f); // Cyan
        float radius = 3 * camera.getZoom();
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(centerX, centerY); // Center point IS diamondCenterScreenCoords[0], diamondCenterScreenCoords[1]
        for (int i = 0; i <= 16; i++) {
            double angle = Math.PI * 2.0 * i / 16.0;
            glVertex2f(centerX + (float)(Math.cos(angle) * radius),
                    centerY + (float)(Math.sin(angle) * radius));
        }
        glEnd();
    }



    // In Renderer.java

// Make sure frameCount is a member of Renderer and incremented
// import static org.isogame.constants.Constants.*; // For ALTURA_MAXIMA, NIVEL_MAR etc.

    // In Renderer.java

// Ensure these constants from org.isogame.constants.Constants are available
// import static org.isogame.constants.Constants.*;

// Assuming frameCount is a member of Renderer and incremented
// Assuming tileDetailRandom is a member of Renderer and initialized

    private void renderTileGeometry(int tileR, int tileC, Tile tile, boolean isSelected) {
        int elevation = tile.getElevation();
        Tile.TileType type = tile.getType();

        // Get camera's current zoom directly
        float currentZoom = camera.getZoom();

        // Calculate TILE dimensions as FLOATS based on current zoom
        // These come from your Constants.java (e.g., TILE_WIDTH, TILE_HEIGHT)
        float effWidthF = (float)TILE_WIDTH * currentZoom;
        float effHeightF = (float)TILE_HEIGHT * currentZoom;
        float effThicknessF = (float)TILE_THICKNESS * currentZoom; // If you use this for tc_y
        float effBaseThickF = (float)BASE_THICKNESS * currentZoom; // If you use this for vbL_Y etc.


        // --- Get CENTER coordinates for the different planes ---
        // These are already floats if mapToScreenCoords returns float[] or if you cast appropriately
        // Assuming mapToScreenCoords returns int[], so gc_x, gc_y are initially integer pixels
        // For smoother tile placement, mapToScreenCoords should ideally work with and return floats.
        // If mapToScreenCoords uses integer effective widths/heights internally, its results will snap.
        // Let's assume for now mapToScreenCoords returns the best possible int[] center for now.
        int[] groundCenterCoords = camera.mapToScreenCoords(tileC, tileR, 0);
        float gc_x = groundCenterCoords[0]; // Ground Center X
        float gc_y = groundCenterCoords[1]; // Ground Center Y

        // Center of the diamond face at its actual elevation (top face)
        // Use the integer effThickness from camera for consistency with how mapToScreenCoords might calculate elevation offset
        // OR, if mapToScreenCoords is updated to use float thickness, then use effThicknessF here.
        // For now, sticking to your original use of camera.getEffectiveTileThickness() for this tc_y calculation:
        float tc_x = gc_x;
        float tc_y = gc_y - (elevation * camera.getEffectiveTileThickness()); // Using int effThickness for this specific offset calculation

        // Culling (using the actual top face center)
        // Use integer effWidth/Height from camera for culling margin for consistency with current tc_x/tc_y logic
        int effWidthForCull = camera.getEffectiveTileWidth();
        int effHeightForCull = camera.getEffectiveTileHeight();
        int margin = Math.max(effWidthForCull, effHeightForCull) * 3;
        if (tc_x < -margin || tc_x > camera.getScreenWidth() + margin ||
                tc_y < -margin - (ALTURA_MAXIMA * camera.getEffectiveTileThickness()) || tc_y > camera.getScreenHeight() + margin + effHeightForCull) {
            return;
        }

        // --- Color determination (your existing logic) ---
        // This part remains the same
        float[] topColor = {1f,0f,1f,1f}, side1Color= {1f,0f,1f,1f}, side2Color= {1f,0f,1f,1f};
        float[] baseTopColor = {1f,0f,1f,1f}, baseSide1Color= {1f,0f,1f,1f}, baseSide2Color= {1f,0f,1f,1f};
        boolean isWater = (type == Tile.TileType.WATER);

        if (isSelected) {
            topColor = new float[]{1.0f, 0.8f, 0.0f, 0.8f}; side1Color = new float[]{0.9f, 0.7f, 0.0f, 0.8f}; side2Color = new float[]{0.8f, 0.6f, 0.0f, 0.8f};
            baseTopColor = new float[]{0.5f, 0.4f, 0.0f, 0.8f}; baseSide1Color = new float[]{0.4f, 0.3f, 0.0f, 0.8f}; baseSide2Color = new float[]{0.3f, 0.2f, 0.0f, 0.8f};
        } else {
            switch (type) { // Your color logic
                case WATER:
                    double tS1=0.05,tS2=0.03,sS1=0.4,sS2=0.6,bB=0.3,bA=0.15,gSB=0.3,gSA=0.1;
                    double wF1=(frameCount*tS1+(tileR+tileC)*sS1),wF2=(frameCount*tS2+(tileR*0.7-tileC*0.6)*sS2);
                    double wV=(Math.sin(wF1)+Math.cos(wF2))/2.0; float blV=(float)Math.max(0.1,Math.min(1.0,bB+wV*bA));
                    float grV=(float)Math.max(0.0,Math.min(1.0,blV*(gSB+Math.sin(wF1+tileC*0.5)*gSA)));
                    topColor=new float[]{0.0f,grV,blV,0.85f}; side1Color=topColor; side2Color=topColor;
                    baseTopColor=new float[]{0.0f,grV,blV,0.85f};
                    baseSide1Color=new float[]{0.04f,0.08f,0.18f,1.0f}; baseSide2Color=new float[]{0.03f,0.06f,0.16f,1.0f}; break;
                case SAND: topColor=new float[]{0.82f,0.7f,0.55f,1.0f}; side1Color=new float[]{0.75f,0.65f,0.49f,1.0f}; side2Color=new float[]{0.67f,0.59f,0.43f,1.0f}; baseTopColor=new float[]{0.59f,0.51f,0.35f,1.0f}; baseSide1Color=new float[]{0.51f,0.43f,0.27f,1.0f}; baseSide2Color=new float[]{0.43f,0.35f,0.19f,1.0f}; break;
                case GRASS: topColor=new float[]{0.13f,0.55f,0.13f,1.0f}; side1Color=new float[]{0.12f,0.47f,0.12f,1.0f}; side2Color=new float[]{0.10f,0.39f,0.10f,1.0f}; baseTopColor=new float[]{0.31f,0.24f,0.16f,1.0f}; baseSide1Color=new float[]{0.27f,0.20f,0.14f,1.0f}; baseSide2Color=new float[]{0.24f,0.16f,0.12f,1.0f}; break;
                case ROCK: topColor=new float[]{0.5f,0.5f,0.5f,1.0f}; side1Color=new float[]{0.45f,0.45f,0.45f,1.0f}; side2Color=new float[]{0.4f,0.4f,0.4f,1.0f}; baseTopColor=new float[]{0.35f,0.35f,0.35f,1.0f}; baseSide1Color=new float[]{0.3f,0.3f,0.3f,1.0f}; baseSide2Color=new float[]{0.25f,0.25f,0.25f,1.0f}; break;
                case SNOW: topColor=new float[]{0.95f,0.95f,1.0f,1.0f}; side1Color=new float[]{0.9f,0.9f,0.95f,1.0f}; side2Color=new float[]{0.85f,0.85f,0.9f,1.0f}; baseTopColor=new float[]{0.5f,0.5f,0.55f,1.0f}; baseSide1Color=new float[]{0.45f,0.45f,0.5f,1.0f}; baseSide2Color=new float[]{0.4f,0.4f,0.45f,1.0f}; break;
            }
        }

        // --- Bleed amount and half dimensions with bleed (using float dimensions) ---
        float bleedAmount = 0.5f; // Experiment with this value (e.g., 0.5f, 0.75f, 1.0f)
        float halfWidthFBleed = (effWidthF / 2.0f) + bleedAmount;
        float halfHeightFBleed = (effHeightF / 2.0f) + bleedAmount;

        // --- Vertices for Ground-Level Diamond (base diamond, b) ---
        float[] bT = {gc_x, gc_y - halfHeightFBleed};
        float[] bL = {gc_x - halfWidthFBleed, gc_y};
        float[] bR = {gc_x + halfWidthFBleed, gc_y};
        float[] bB = {gc_x, gc_y + halfHeightFBleed};

        // --- Vertices for the Very Bottom of the Base Block ---
        // Use float effBaseThickF for consistent scaling
        float vbL_Y = bL[1] + effBaseThickF;
        float vbR_Y = bR[1] + effBaseThickF;
        float vbB_Y = bB[1] + effBaseThickF;


        // --- Draw Base Block Sides ---
        glColor4f(baseSide1Color[0],baseSide1Color[1],baseSide1Color[2],baseSide1Color[3]);
        glBegin(GL_QUADS);
        glVertex2f(bL[0], bL[1]);   glVertex2f(bB[0], bB[1]);
        glVertex2f(bB[0], vbB_Y);   glVertex2f(bL[0], vbL_Y);
        glEnd();

        glColor4f(baseSide2Color[0],baseSide2Color[1],baseSide2Color[2],baseSide2Color[3]);
        glBegin(GL_QUADS);
        glVertex2f(bB[0], bB[1]);   glVertex2f(bR[0], bR[1]);
        glVertex2f(bR[0], vbR_Y);   glVertex2f(bB[0], vbB_Y);
        glEnd();

        // --- Draw Top of Base Block (Ground-Level Surface) ---
        glColor4f(baseTopColor[0],baseTopColor[1],baseTopColor[2],baseTopColor[3]);
        glBegin(GL_QUADS);
        glVertex2f(bL[0], bL[1]); glVertex2f(bT[0], bT[1]);
        glVertex2f(bR[0], bR[1]); glVertex2f(bB[0], bB[1]);
        glEnd();

        // --- Elevated Top Face and Sides ---
        float topDiamondCenterX_render = gc_x; // For grass/detail placement
        float topDiamondCenterY_render = gc_y;

        if(!isWater && elevation > 0){
            // tc_x, tc_y is the CENTER of the elevated top face
            float[] fT = {tc_x, tc_y - halfHeightFBleed};
            float[] fL = {tc_x - halfWidthFBleed, tc_y};
            float[] fR = {tc_x + halfWidthFBleed, tc_y};
            float[] fB = {tc_x, tc_y + halfHeightFBleed};

            glColor4f(side1Color[0],side1Color[1],side1Color[2],side1Color[3]);
            glBegin(GL_QUADS);
            glVertex2f(bL[0], bL[1]); glVertex2f(bB[0], bB[1]); // Ground diamond points
            glVertex2f(fB[0], fB[1]); glVertex2f(fL[0], fL[1]); // Face diamond points
            glEnd();

            glColor4f(side2Color[0],side2Color[1],side2Color[2],side2Color[3]);
            glBegin(GL_QUADS);
            glVertex2f(bB[0], bB[1]); glVertex2f(bR[0], bR[1]); // Ground diamond points
            glVertex2f(fR[0], fR[1]); glVertex2f(fB[0], fB[1]); // Face diamond points
            glEnd();

            glColor4f(topColor[0],topColor[1],topColor[2],topColor[3]);
            glBegin(GL_QUADS);
            glVertex2f(fL[0], fL[1]); glVertex2f(fT[0], fT[1]);
            glVertex2f(fR[0], fR[1]); glVertex2f(fB[0], fB[1]);
            glEnd();

            topDiamondCenterX_render = tc_x;
            topDiamondCenterY_render = tc_y;
        } else if (!isWater) { // Land at elevation 0
            glColor4f(topColor[0],topColor[1],topColor[2],topColor[3]);
            glBegin(GL_QUADS);
            glVertex2f(bL[0], bL[1]); glVertex2f(bT[0], bT[1]);
            glVertex2f(bR[0], bR[1]); glVertex2f(bB[0], bB[1]);
            glEnd();
        } else { // isWater
            glColor4f(topColor[0],topColor[1],topColor[2],topColor[3]);
            glBegin(GL_QUADS);
            glVertex2f(bL[0], bL[1]); glVertex2f(bT[0], bT[1]);
            glVertex2f(bR[0], bR[1]); glVertex2f(bB[0], bB[1]);
            glEnd();
        }

        // --- Details like Grass ---
        // The grass rendering should use the NON-BLED dimensions for its area
        // and be anchored to the NON-BLED center (gc_x/gc_y or tc_x/tc_y before bleed adjustment)
        // topDiamondCenterX_render and topDiamondCenterY_render are the correct *centers*.
        // For the width/height of the grass area, use the original float dimensions.
        if(!isWater && type == Tile.TileType.GRASS){
            long seed=(long)tileR*map.getWidth()+tileC;
            this.tileDetailRandom.setSeed(seed); // Assuming tileDetailRandom is a Renderer member
            Grass.renderThickGrassTufts(this.tileDetailRandom,
                    topDiamondCenterX_render, // This is the correct logical center
                    topDiamondCenterY_render, // This is the correct logical center
                    TILE_WIDTH * currentZoom,  // Original float width for grass area
                    TILE_HEIGHT * currentZoom, // Original float height for grass area
                    15,
                    (isSelected && type == Tile.TileType.GRASS) ? 0.1f : topColor[0]*0.75f,
                    (isSelected && type == Tile.TileType.GRASS) ? 0.45f : topColor[1]*0.85f,
                    (isSelected && type == Tile.TileType.GRASS) ? 0.1f : topColor[2]*0.7f,
                    currentZoom);
        }
    }

    private void renderUI() {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, camera.getScreenWidth(), camera.getScreenHeight(), 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int yP=20,yI=18;
        Tile selT=map.getTile(inputHandler.getSelectedRow(),inputHandler.getSelectedCol());String selI="Selected: ("+inputHandler.getSelectedRow()+", "+inputHandler.getSelectedCol()+")";
        if(selT!=null){selI+=" Elev: "+selT.getElevation()+" Type: "+selT.getType();}
        drawText(10,yP,"Player: ("+player.getTileRow()+", "+player.getTileCol()+") Act: "+player.getCurrentAction()+" Dir: "+player.getCurrentDirection()+" F:"+player.getVisualFrameIndex());yP+=yI;
        drawText(10,yP,selI);yP+=yI;drawText(10,yP,String.format("Camera: (%.1f, %.1f) Zoom: %.2f",camera.getCameraX(),camera.getCameraY(),camera.getZoom()));yP+=yI;
        drawText(10,yP,"Move: Click | Sel: Mouse | Elev Sel +/-: Q/E | Dig: J");yP+=yI;
        drawText(10,yP,"Levitate: F | Center Cam: C | Regen Map: G");yP+=yI;yP+=yI;drawText(10,yP,"Inventory:");yP+=yI;
        java.util.Map<String,Integer> inv=player.getInventory();
        if(inv.isEmpty()){drawText(20,yP,"- Empty -");}else{for(java.util.Map.Entry<String,Integer>e:inv.entrySet()){drawText(20,yP,"- "+e.getKey()+": "+e.getValue());yP+=yI;}}

        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private void drawText(int x, int y, String text) {
        if(uiFont!=null){
            uiFont.drawText((float)x,(float)y,text);
        } else {
            glDisable(GL_TEXTURE_2D);
            glColor4f(0.1f,0.1f,0.1f,0.6f);glBegin(GL_QUADS);glVertex2f(x-2,y-2);glVertex2f(x-2+text.length()*8+4,y-2);glVertex2f(x-2+text.length()*8+4,y+15+2);glVertex2f(x-2,y+15+2);glEnd();
            glColor4f(1.0f,1.0f,1.0f,0.8f);glBegin(GL_LINES);glVertex2f(x,y+15/2.0f);glVertex2f(x+text.length()*8,y+15/2.0f);glEnd();
        }
    }
    // Add a flag to toggle debug drawin
// ... inside your main render() method, AFTER renderMapBaseGeometry() and AFTER
// the main loop for rendering sortableItems, but BEFORE renderUI():
// Or, more effectively, modify renderMapBaseGeometry to also draw these debug outlines.


    public void cleanup() {
        if(playerTexture!=null)playerTexture.delete();
        if(treeTexture!=null)treeTexture.delete();
        if(uiFont!=null)uiFont.cleanup();
    }
}