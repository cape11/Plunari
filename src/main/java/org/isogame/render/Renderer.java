package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.entity.PlayerModel;
import org.isogame.input.InputHandler;
import org.isogame.map.Map; // Your game map
import org.isogame.tile.Tile;
import org.isogame.render.Grass;

import java.io.IOException;
import java.util.Random;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.opengl.GL11.*;

public class Renderer {
    // ... (fields: camera, map, player, inputHandler, frameCount, playerTexture, treeTexture, uiFont, tileDetailRandom)
    private final CameraManager camera;
    private final Map map;
    private final PlayerModel player;
    private final InputHandler inputHandler;
    private int frameCount = 0;
    private Texture playerTexture;
    private Texture treeTexture;
    private Font uiFont;
    private Random tileDetailRandom;


    public Renderer(CameraManager camera, Map map, PlayerModel player, InputHandler inputHandler) {
        this.camera = camera;
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler;
        this.tileDetailRandom = new Random();
        loadAssets();
    }

    private void loadAssets() {
        this.playerTexture = Texture.loadTexture("textures/lpc_character.png");
        if (this.playerTexture == null) {
            System.err.println("CRITICAL: Player texture failed to load.");
        }
        this.treeTexture = Texture.loadTexture("textures/fruit-trees.png");
        if (this.treeTexture == null) {
            System.err.println("CRITICAL: Tree texture (fruit-trees.png) failed to load.");
        }
        try {
            this.uiFont = new Font("fonts/PressStart2P-Regular.ttf", 16f);
        } catch (IOException e) {
            System.err.println("CRITICAL: Failed to load UI font 'PressStart2P-Regular.ttf'.");
            e.printStackTrace();
            this.uiFont = null;
        }
    }

    /**
     * Called when the window (framebuffer) is resized.
     * Sets the OpenGL viewport and orthographic projection.
     * @param fbWidth The new framebuffer width in pixels.
     * @param fbHeight The new framebuffer height in pixels.
     */
    public void onResize(int fbWidth, int fbHeight) {
        System.out.println("Renderer.onResize called with: " + fbWidth + "x" + fbHeight);
        if (fbWidth <= 0 || fbHeight <= 0) {
            System.err.println("Renderer.onResize received invalid dimensions: " + fbWidth + "x" + fbHeight + ". Skipping viewport/ortho update.");
            return;
        }
        glViewport(0, 0, fbWidth, fbHeight);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        // IMPORTANT: Use framebuffer dimensions for orthographic projection
        glOrtho(0, fbWidth, fbHeight, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    public void render() {
        frameCount++;
        glMatrixMode(GL_MODELVIEW); // Should already be this from onResize, but good practice
        glLoadIdentity();           // Reset modelview matrix for each frame's drawing

        renderMap();
        renderPlayer();
        renderUI();
    }

    // ... (renderMap, renderTile, renderTree, renderPlayer, renderUI, drawText, cleanup methods should be here, ensure renderTile uses the corrected topDiamond... calculations for grass/tree placement)
    // For brevity, I'm only showing the structure and onResize here.
    // Make sure your renderTile and renderTree use the correct parameters.
    private void renderMap() {
        int mapW = map.getWidth();
        int mapH = map.getHeight();
        for (int sum = 0; sum <= mapW + mapH - 2; sum++) {
            for (int row = 0; row <= sum; row++) {
                int col = sum - row;
                if (map.isValid(row, col)) {
                    Tile tile = map.getTile(row, col);
                    if (tile != null) {
                        boolean isSelected = (row == inputHandler.getSelectedRow() && col == inputHandler.getSelectedCol());
                        renderTile(row, col, tile, isSelected);
                    }
                }
            }
        }
    }

    private void renderTile(int tileR, int tileC, Tile tile, boolean isSelected) {
        int elevation = tile.getElevation();
        Tile.TileType type = tile.getType();
        int[] baseScreenCoords = camera.mapToScreenCoords(tileC, tileR, 0);
        int tileBaseX = baseScreenCoords[0]; int tileBaseY = baseScreenCoords[1];
        int effWidth = camera.getEffectiveTileWidth(); int effHeight = camera.getEffectiveTileHeight();
        int effThickness = camera.getEffectiveTileThickness();
        int margin = Math.max(effWidth, effHeight) * 2;
        if (tileBaseX < -margin || tileBaseX > camera.getScreenWidth() + margin ||
                tileBaseY < -margin - (ALTURA_MAXIMA * effThickness) || tileBaseY > camera.getScreenHeight() + margin + effHeight) return;

        float[] topColor = {1f,0f,1f,1f}, side1Color= {1f,0f,1f,1f}, side2Color= {1f,0f,1f,1f}, baseTopColor= {1f,0f,1f,1f}, baseSide1Color= {1f,0f,1f,1f}, baseSide2Color= {1f,0f,1f,1f};
        boolean isWater = (type == Tile.TileType.WATER);
        if (isSelected) { /* ... selected colors ... */
            topColor = new float[]{1.0f, 0.8f, 0.0f, 0.8f}; side1Color = new float[]{0.9f, 0.7f, 0.0f, 0.8f}; side2Color = new float[]{0.8f, 0.6f, 0.0f, 0.8f};
            baseTopColor = new float[]{0.5f, 0.4f, 0.0f, 0.8f}; baseSide1Color = new float[]{0.4f, 0.3f, 0.0f, 0.8f}; baseSide2Color = new float[]{0.3f, 0.2f, 0.0f, 0.8f};
        } else { switch (type) { /* ... tile type colors ... */
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
        }}
        int effBT=camera.getEffectiveBaseThickness(); int[]bT={tileBaseX+effWidth/2,tileBaseY},bL={tileBaseX,tileBaseY+effHeight/2},bR={tileBaseX+effWidth,tileBaseY+effHeight/2},bB={tileBaseX+effWidth/2,tileBaseY+effHeight};
        int[]bBL={bL[0],bL[1]+effBT},bBR={bR[0],bR[1]+effBT},bBB={bB[0],bB[1]+effBT};
        glColor4f(baseSide1Color[0],baseSide1Color[1],baseSide1Color[2],baseSide1Color[3]);glBegin(GL_QUADS);glVertex2f(bL[0],bL[1]);glVertex2f(bBL[0],bBL[1]);glVertex2f(bBB[0],bBB[1]);glVertex2f(bB[0],bB[1]);glEnd();
        glColor4f(baseSide2Color[0],baseSide2Color[1],baseSide2Color[2],baseSide2Color[3]);glBegin(GL_QUADS);glVertex2f(bR[0],bR[1]);glVertex2f(bBR[0],bBR[1]);glVertex2f(bBB[0],bBB[1]);glVertex2f(bB[0],bB[1]);glEnd();
        glColor4f(baseTopColor[0],baseTopColor[1],baseTopColor[2],baseTopColor[3]);glBegin(GL_QUADS);glVertex2f(bL[0],bL[1]);glVertex2f(bT[0],bT[1]);glVertex2f(bR[0],bR[1]);glVertex2f(bB[0],bB[1]);glEnd();

        float topDiamondCenterX=0,topDiamondCenterY=0,topDiamondIsoWidth=0,topDiamondIsoHeight=0; boolean validSurf=false;
        if(!isWater&&elevation>0){
            int tEO=elevation*effThickness;int tSPY=tileBaseY-tEO;
            int[]fT={tileBaseX+effWidth/2,tSPY},fL={tileBaseX,tSPY+effHeight/2},fR={tileBaseX+effWidth,tSPY+effHeight/2},fB={tileBaseX+effWidth/2,tSPY+effHeight};
            int gSY=tileBaseY;int[]gL={tileBaseX,gSY+effHeight/2},gR={tileBaseX+effWidth,gSY+effHeight/2},gB={tileBaseX+effWidth/2,gSY+effHeight};
            glColor4f(side1Color[0],side1Color[1],side1Color[2],side1Color[3]);glBegin(GL_QUADS);glVertex2f(gL[0],gL[1]);glVertex2f(gB[0],gB[1]);glVertex2f(fB[0],fB[1]);glVertex2f(fL[0],fL[1]);glEnd();
            glColor4f(side2Color[0],side2Color[1],side2Color[2],side2Color[3]);glBegin(GL_QUADS);glVertex2f(gR[0],gR[1]);glVertex2f(gB[0],gB[1]);glVertex2f(fB[0],fB[1]);glVertex2f(fR[0],fR[1]);glEnd();
            glColor4f(topColor[0],topColor[1],topColor[2],topColor[3]);glBegin(GL_QUADS);glVertex2f(fL[0],fL[1]);glVertex2f(fT[0],fT[1]);glVertex2f(fR[0],fR[1]);glVertex2f(fB[0],fB[1]);glEnd();
            topDiamondCenterX=fT[0];topDiamondCenterY=(fT[1]+fB[1])/2.0f;topDiamondIsoWidth=fR[0]-fL[0];topDiamondIsoHeight=fB[1]-fT[1];validSurf=true;
        }else if(!isWater){
            glColor4f(topColor[0],topColor[1],topColor[2],topColor[3]);glBegin(GL_QUADS);glVertex2f(bL[0],bL[1]);glVertex2f(bT[0],bT[1]);glVertex2f(bR[0],bR[1]);glVertex2f(bB[0],bB[1]);glEnd();
            topDiamondCenterX=bT[0];topDiamondCenterY=(bT[1]+bB[1])/2.0f;topDiamondIsoWidth=bR[0]-bL[0];topDiamondIsoHeight=bB[1]-bT[1];validSurf=true;
        }
        if(validSurf){
            if(type==Tile.TileType.GRASS){long seed=(long)tileR*map.getWidth()+tileC;this.tileDetailRandom.setSeed(seed);Grass.renderThickGrassTufts(this.tileDetailRandom,topDiamondCenterX,topDiamondCenterY,topDiamondIsoWidth,topDiamondIsoHeight,15,topColor[0]*0.75f,topColor[1]*0.85f,topColor[2]*0.7f,camera.getZoom());}
            if(tile.getTreeType()!=Tile.TreeVisualType.NONE){renderTree(tile.getTreeType(),topDiamondCenterX,topDiamondCenterY,topDiamondIsoHeight);}
        }
    }
    private void renderTree(Tile.TreeVisualType treeVisualType, float tDSCX, float tDSCY, float tDDIH) { /* ... as defined previously, using actual tree data ... */
        if (treeTexture == null || treeVisualType == Tile.TreeVisualType.NONE) return;
        glEnable(GL_TEXTURE_2D); treeTexture.bind(); glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        float u0=0,v0=0,u1=0,v1=0,tFWSPx=0,tFHSPx=0,aOYPFB=0;
        switch(treeVisualType){
            case APPLE_TREE_FRUITING:tFWSPx=90.0f;tFHSPx=130.0f;
            float appleSX=0.0f,appleSY=0.0f;aOYPFB=10.0f;u0=appleSX/treeTexture.getWidth();v0=appleSY/treeTexture.getHeight();u1=(appleSX+tFWSPx)/treeTexture.getWidth();
            v1=(appleSY+tFHSPx)/treeTexture.getHeight();break;

            case PINE_TREE_SMALL:tFWSPx=90.0f;tFHSPx=180.0f;
            float pineSX=90.0f,pineSY=0.0f;aOYPFB=5.0f;u0=pineSX/treeTexture.getWidth();
            v0=pineSY/treeTexture.getHeight();u1=(pineSX+tFWSPx)/treeTexture.getWidth();v1=(pineSY+tFHSPx)/treeTexture.getHeight();break;

            default:System.err.println("Unhandled TreeVT:"+treeVisualType);glDisable(GL_TEXTURE_2D);return;
        }
        if(tFWSPx<=0||tFHSPx<=0){System.err.println("Invalid frame dims:"+treeVisualType);glDisable(GL_TEXTURE_2D);return;}
        float artS=1.0f,tDW=tFWSPx*camera.getZoom()*artS,tDH=tFHSPx*camera.getZoom()*artS;
        float sAOY=aOYPFB*camera.getZoom()*artS;
        float dX=tDSCX-(tDW/2.0f),dY=tDSCY-tDH+sAOY;
        glBegin(GL_QUADS);glTexCoord2f(u0,v0);glVertex2f(dX,dY);glTexCoord2f(u1,v0);glVertex2f(dX+tDW,dY);glTexCoord2f(u1,v1);glVertex2f(dX+tDW,dY+tDH);glTexCoord2f(u0,v1);glVertex2f(dX,dY+tDH);glEnd();
        glDisable(GL_TEXTURE_2D);
    }
    private void renderPlayer() { /* ... same as before ... */
        if(playerTexture==null){System.err.println("Player tex null");return;}
        int pTR=player.getTileRow(),pTC=player.getTileCol();Tile cT=map.getTile(pTR,pTC);int pBE=(cT!=null)?cT.getElevation():0;
        int[]pSBC=camera.mapToScreenCoords(player.getMapCol(),player.getMapRow(),pBE);float sX=pSBC[0],sY=pSBC[1];
        glEnable(GL_TEXTURE_2D);playerTexture.bind();glColor4f(1.0f,1.0f,1.0f,1.0f);
        int fWPx=PlayerModel.FRAME_WIDTH,fHPx=PlayerModel.FRAME_HEIGHT;int aFC=player.getVisualFrameIndex(),aSR=player.getAnimationRow();
        float fPXOS=aFC*fWPx,fPYOS=aSR*fHPx;float tSWPx=playerTexture.getWidth(),tSHPx=playerTexture.getHeight();
        float u0=fPXOS/tSWPx,v0=fPYOS/tSHPx,u1=(fPXOS+fWPx)/tSWPx,v1=(fPYOS+fHPx)/tSHPx;
        float sDW=fWPx*camera.getZoom(),sDH=fHPx*camera.getZoom();
        float dX=sX-(sDW/2.0f),dY=sY-sDH;
        if(player.isLevitating()){dY-=(int)(Math.sin(player.getLevitateTimer())*8*camera.getZoom());}
        glBegin(GL_QUADS);glTexCoord2f(u0,v0);glVertex2f(dX,dY);glTexCoord2f(u1,v0);glVertex2f(dX+sDW,dY);glTexCoord2f(u1,v1);glVertex2f(dX+sDW,dY+sDH);glTexCoord2f(u0,v1);glVertex2f(dX,dY+sDH);glEnd();
        playerTexture.unbind();glDisable(GL_TEXTURE_2D);
    }
    private void renderUI() { /* ... same as before, using java.util.Map ... */
        glMatrixMode(GL_MODELVIEW);glLoadIdentity();int yP=20,yI=18;
        Tile selT=map.getTile(inputHandler.getSelectedRow(),inputHandler.getSelectedCol());String selI="Selected: ("+inputHandler.getSelectedRow()+", "+inputHandler.getSelectedCol()+")";
        if(selT!=null){selI+=" Elev: "+selT.getElevation()+" Type: "+selT.getType();}
        drawText(10,yP,"Player: ("+player.getTileRow()+", "+player.getTileCol()+") Act: "+player.getCurrentAction()+" Dir: "+player.getCurrentDirection()+" F:"+player.getVisualFrameIndex());yP+=yI;
        drawText(10,yP,selI);yP+=yI;drawText(10,yP,String.format("Camera: (%.1f, %.1f) Zoom: %.2f",camera.getCameraX(),camera.getCameraY(),camera.getZoom()));yP+=yI;
        drawText(10,yP,"Move: Click | Sel: Mouse | Elev Sel +/-: Q/E | Dig: J");yP+=yI;
        drawText(10,yP,"Levitate: F | Center Cam: C | Regen Map: G");yP+=yI;yP+=yI;drawText(10,yP,"Inventory:");yP+=yI;
        java.util.Map<String,Integer> inv=player.getInventory();
        if(inv.isEmpty()){drawText(20,yP,"- Empty -");}else{for(java.util.Map.Entry<String,Integer>e:inv.entrySet()){drawText(20,yP,"- "+e.getKey()+": "+e.getValue());yP+=yI;}}
    }
    private void drawText(int x, int y, String text) { /* ... same as before with uiFont check ... */
        if(uiFont!=null){uiFont.drawText((float)x,(float)y,text);}else{int cW=8,cH=15,tW=text.length()*cW;glColor4f(0.1f,0.1f,0.1f,0.6f);glBegin(GL_QUADS);glVertex2f(x-2,y-2);glVertex2f(x+tW+2,y-2);glVertex2f(x+tW+2,y+cH+2);glVertex2f(x-2,y+cH+2);glEnd();glColor4f(1.0f,1.0f,1.0f,0.8f);glBegin(GL_LINES);glVertex2f(x,y+cH/2.0f);glVertex2f(x+tW,y+cH/2.0f);glEnd();}
    }
    public void cleanup() { /* ... same as before ... */
        System.out.println("Renderer cleanup...");if(playerTexture!=null)playerTexture.delete();if(treeTexture!=null)treeTexture.delete();if(uiFont!=null)uiFont.cleanup();
    }
}