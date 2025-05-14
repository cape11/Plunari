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
            this.zOrder = 1; // Player

            Tile currentTile = gameMap.getTile(player.getTileRow(), player.getTileCol());
            int playerBaseElevation = (currentTile != null) ? currentTile.getElevation() : 0;
            int[] screenCoords = camera.mapToScreenCoords(player.getMapCol(), player.getMapRow(), playerBaseElevation);
            this.screenYSortKey = screenCoords[1];
        }

        // Constructor for Tree (using TreeData)
        public SortableItem(TreeData tree, CameraManager camera, Map gameMap) {
            this.entity = tree;
            this.zOrder = 0; // Tree

            int[] screenCoords = camera.mapToScreenCoords(tree.mapCol, tree.mapRow, tree.elevation);
            this.screenYSortKey = screenCoords[1];
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
        this.playerTexture = Texture.loadTexture("textures/lpc_character.png");
        if (this.playerTexture == null) System.err.println("CRITICAL: Player texture failed to load.");
        this.treeTexture = Texture.loadTexture("textures/fruit-trees.png");
        if (this.treeTexture == null) System.err.println("CRITICAL: Tree texture failed to load.");
        try {
            this.uiFont = new Font("fonts/PressStart2P-Regular.ttf", 16f);
        } catch (IOException e) {
            System.err.println("CRITICAL: Failed to load UI font. " + e.getMessage());
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
                if (item1.screenYSortKey != item2.screenYSortKey) {
                    return Float.compare(item1.screenYSortKey, item2.screenYSortKey);
                }
                return Integer.compare(item1.zOrder, item2.zOrder);
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

    private void renderMapBaseGeometry() {
        int mapW = map.getWidth();
        int mapH = map.getHeight();
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        for (int sum = 0; sum <= mapW + mapH - 2; sum++) {
            for (int r = 0; r <= sum; r++) {
                int col = sum - r;
                if (map.isValid(r, col)) {
                    Tile tile = map.getTile(r, col);
                    if (tile != null) {
                        renderTileGeometry(r, col, tile, (r == inputHandler.getSelectedRow() && col == inputHandler.getSelectedCol()));
                    }
                }
            }
        }
    }

    private void renderTileGeometry(int tileR, int tileC, Tile tile, boolean isSelected) {
        int elevation = tile.getElevation();
        Tile.TileType type = tile.getType();
        int[] baseScreenCoords = camera.mapToScreenCoords(tileC, tileR, 0);
        int tileBaseX = baseScreenCoords[0]; int tileBaseY = baseScreenCoords[1];
        int effWidth = camera.getEffectiveTileWidth(); int effHeight = camera.getEffectiveTileHeight();
        int effThickness = camera.getEffectiveTileThickness();

        int margin = Math.max(effWidth, effHeight) * 2;
        if (tileBaseX < -margin || tileBaseX > camera.getScreenWidth() + margin ||
                tileBaseY < -margin - (ALTURA_MAXIMA * effThickness) || tileBaseY > camera.getScreenHeight() + margin + (effHeight * 2)) {
            return;
        }

        float[] topColor = {1f,0f,1f,1f}, side1Color= {1f,0f,1f,1f}, side2Color= {1f,0f,1f,1f}, baseTopColor= {1f,0f,1f,1f}, baseSide1Color= {1f,0f,1f,1f}, baseSide2Color= {1f,0f,1f,1f};
        boolean isWater = (type == Tile.TileType.WATER);

        if (isSelected) {
            topColor = new float[]{1.0f, 0.8f, 0.0f, 0.8f}; side1Color = new float[]{0.9f, 0.7f, 0.0f, 0.8f}; side2Color = new float[]{0.8f, 0.6f, 0.0f, 0.8f};
            baseTopColor = new float[]{0.5f, 0.4f, 0.0f, 0.8f}; baseSide1Color = new float[]{0.4f, 0.3f, 0.0f, 0.8f}; baseSide2Color = new float[]{0.3f, 0.2f, 0.0f, 0.8f};
        } else {
            switch (type) {
                case WATER:
                    double tS1=0.05,tS2=0.03,sS1=0.4,sS2=0.6,bB=0.3,bA=0.15,gSB=0.3,gSA=0.1;
                    double wF1=(frameCount*tS1+(tileR+tileC)*sS1),wF2=(frameCount*tS2+(tileR*0.7-tileC*0.6)*sS2);
                    double wV=(Math.sin(wF1)+Math.cos(wF2))/2.0; float blV=(float)Math.max(0.1,Math.min(1.0,bB+wV*bA));
                    float grV=(float)Math.max(0.0,Math.min(1.0,blV*(gSB+Math.sin(wF1+tileC*0.5)*gSA)));
                    topColor=new float[]{0.0f,grV,blV,0.85f}; side1Color=topColor; side2Color=topColor;
                    baseTopColor=new float[]{0.05f,0.1f,0.2f,1.0f}; baseSide1Color=new float[]{0.04f,0.08f,0.18f,1.0f}; baseSide2Color=new float[]{0.03f,0.06f,0.16f,1.0f}; break;
                case SAND: topColor=new float[]{0.82f,0.7f,0.55f,1.0f}; side1Color=new float[]{0.75f,0.65f,0.49f,1.0f}; side2Color=new float[]{0.67f,0.59f,0.43f,1.0f}; baseTopColor=new float[]{0.59f,0.51f,0.35f,1.0f}; baseSide1Color=new float[]{0.51f,0.43f,0.27f,1.0f}; baseSide2Color=new float[]{0.43f,0.35f,0.19f,1.0f}; break;
                case GRASS: topColor=new float[]{0.13f,0.55f,0.13f,1.0f}; side1Color=new float[]{0.12f,0.47f,0.12f,1.0f}; side2Color=new float[]{0.10f,0.39f,0.10f,1.0f}; baseTopColor=new float[]{0.31f,0.24f,0.16f,1.0f}; baseSide1Color=new float[]{0.27f,0.20f,0.14f,1.0f}; baseSide2Color=new float[]{0.24f,0.16f,0.12f,1.0f}; break;
                case ROCK: topColor=new float[]{0.5f,0.5f,0.5f,1.0f}; side1Color=new float[]{0.45f,0.45f,0.45f,1.0f}; side2Color=new float[]{0.4f,0.4f,0.4f,1.0f}; baseTopColor=new float[]{0.35f,0.35f,0.35f,1.0f}; baseSide1Color=new float[]{0.3f,0.3f,0.3f,1.0f}; baseSide2Color=new float[]{0.25f,0.25f,0.25f,1.0f}; break;
                case SNOW: topColor=new float[]{0.95f,0.95f,1.0f,1.0f}; side1Color=new float[]{0.9f,0.9f,0.95f,1.0f}; side2Color=new float[]{0.85f,0.85f,0.9f,1.0f}; baseTopColor=new float[]{0.5f,0.5f,0.55f,1.0f}; baseSide1Color=new float[]{0.45f,0.45f,0.5f,1.0f}; baseSide2Color=new float[]{0.4f,0.4f,0.45f,1.0f}; break;
            }
        }
        int effBT=camera.getEffectiveBaseThickness();
        int[]bT={tileBaseX+effWidth/2,tileBaseY},bL={tileBaseX,tileBaseY+effHeight/2},bR={tileBaseX+effWidth,tileBaseY+effHeight/2},bB={tileBaseX+effWidth/2,tileBaseY+effHeight};
        int[]bBL={bL[0],bL[1]+effBT},bBR={bR[0],bR[1]+effBT},bBB={bB[0],bB[1]+effBT};

        glColor4f(baseSide1Color[0],baseSide1Color[1],baseSide1Color[2],baseSide1Color[3]);glBegin(GL_QUADS);glVertex2f(bL[0],bL[1]);glVertex2f(bBL[0],bBL[1]);glVertex2f(bBB[0],bBB[1]);glVertex2f(bB[0],bB[1]);glEnd();
        glColor4f(baseSide2Color[0],baseSide2Color[1],baseSide2Color[2],baseSide2Color[3]);glBegin(GL_QUADS);glVertex2f(bR[0],bR[1]);glVertex2f(bBR[0],bBR[1]);glVertex2f(bBB[0],bBB[1]);glVertex2f(bB[0],bB[1]);glEnd();
        glColor4f(baseTopColor[0],baseTopColor[1],baseTopColor[2],baseTopColor[3]);glBegin(GL_QUADS);glVertex2f(bL[0],bL[1]);glVertex2f(bT[0],bT[1]);glVertex2f(bR[0],bR[1]);glVertex2f(bB[0],bB[1]);glEnd();

        float topDiamondCenterX_render=0,topDiamondCenterY_render=0,topDiamondIsoWidth_render=0,topDiamondIsoHeight_render=0;
        boolean validSurf=false;

        if(!isWater&&elevation>0){
            int tEO=elevation*effThickness;int tSPY=tileBaseY-tEO;
            int[]fT={tileBaseX+effWidth/2,tSPY},fL={tileBaseX,tSPY+effHeight/2},fR={tileBaseX+effWidth,tSPY+effHeight/2},fB={tileBaseX+effWidth/2,tSPY+effHeight};
            int[] gL = {tileBaseX, tileBaseY + effHeight / 2}; // Ground level points for sides
            int[] gR = {tileBaseX + effWidth, tileBaseY + effHeight / 2};
            int[] gB = {tileBaseX + effWidth / 2, tileBaseY + effHeight};
            glColor4f(side1Color[0],side1Color[1],side1Color[2],side1Color[3]);glBegin(GL_QUADS);glVertex2f(gL[0],gL[1]);glVertex2f(gB[0],gB[1]);glVertex2f(fB[0],fB[1]);glVertex2f(fL[0],fL[1]);glEnd();
            glColor4f(side2Color[0],side2Color[1],side2Color[2],side2Color[3]);glBegin(GL_QUADS);glVertex2f(gR[0],gR[1]);glVertex2f(gB[0],gB[1]);glVertex2f(fB[0],fB[1]);glVertex2f(fR[0],fR[1]);glEnd();
            glColor4f(topColor[0],topColor[1],topColor[2],topColor[3]);glBegin(GL_QUADS);glVertex2f(fL[0],fL[1]);glVertex2f(fT[0],fT[1]);glVertex2f(fR[0],fR[1]);glVertex2f(fB[0],fB[1]);glEnd();
            topDiamondCenterX_render=fT[0]; topDiamondCenterY_render=(fT[1]+fB[1])/2.0f; topDiamondIsoWidth_render=fR[0]-fL[0]; topDiamondIsoHeight_render=fB[1]-fT[1]; validSurf=true;
        }else if(!isWater){
            glColor4f(topColor[0],topColor[1],topColor[2],topColor[3]);glBegin(GL_QUADS);glVertex2f(bL[0],bL[1]);glVertex2f(bT[0],bT[1]);glVertex2f(bR[0],bR[1]);glVertex2f(bB[0],bB[1]);glEnd();
            topDiamondCenterX_render=bT[0]; topDiamondCenterY_render=(bT[1]+bB[1])/2.0f; topDiamondIsoWidth_render=bR[0]-bL[0]; topDiamondIsoHeight_render=bB[1]-bT[1]; validSurf=true;
        }

        if(validSurf && type == Tile.TileType.GRASS){
            long seed=(long)tileR*map.getWidth()+tileC;
            this.tileDetailRandom.setSeed(seed);
            Grass.renderThickGrassTufts(this.tileDetailRandom,topDiamondCenterX_render,topDiamondCenterY_render,topDiamondIsoWidth_render,topDiamondIsoHeight_render,15,topColor[0]*0.75f,topColor[1]*0.85f,topColor[2]*0.7f,camera.getZoom());
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

    public void cleanup() {
        if(playerTexture!=null)playerTexture.delete();
        if(treeTexture!=null)treeTexture.delete();
        if(uiFont!=null)uiFont.cleanup();
    }
}