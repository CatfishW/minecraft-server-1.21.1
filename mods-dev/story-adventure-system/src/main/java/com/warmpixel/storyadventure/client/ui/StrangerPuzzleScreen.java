package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Enhanced Stranger Things themed puzzle interaction screen.
 * Puzzles: CODE_LOCK, FINGERPRINT, PIPE_CONNECT, SNAKE, HEX_SEARCH, MEMORY_SEQUENCE, SIGNAL_TUNING.
 * Features rich animations, neon effects, and robust logic.
 */
public class StrangerPuzzleScreen extends StrangerScreen {
    
    private final String puzzleType;
    private final String title;
    private final String subtitle;
    private final List<String> hints;
    private final int maxAttempts;
    private int currentAttempts = 0;
    private final boolean[] keysDown = new boolean[512];

    private static final int PANEL_PADDING = 20;
    private static final int HEADER_GAP = 12;
    private static final int LINE_HEIGHT = 13;
    
    // Layout variables
    private int panelX, panelY, panelWidth, panelHeight;
    private int contentTop, contentBottom, leftColumnWidth, rightColumnX, rightColumnWidth;

    // Common State
    private final Random random = new Random();
    private long lastActionTime = 0;
    private float glitchIntensity = 0f;

    // Puzzle-specific states
    private StringBuilder codeInput = new StringBuilder();
    private int maxCodeLength = 4;
    private static final int CODE_BUTTON_SIZE = 32;
    private static final int CODE_BUTTON_GAP = 6;
    private int displayX, displayY, displayWidth, keypadStartX, keypadStartY, gridWidth, gridHeight;

    private final List<FingerprintFragment> fragments = new ArrayList<>();
    private final boolean[] selectedFragments = new boolean[8];
    private int correctFragmentsMask = 0;

    private PipeGrid pipeGrid;
    private SnakeGame snakeGame;
    private HexSearch hexSearch;
    private MemorySequence memorySequence;
    private SignalTuning signalTuning;

    public StrangerPuzzleScreen(String puzzleType, String title, String subtitle, List<String> hints, int maxAttempts, int codeLength) {
        super(Component.literal("解谜协议"));
        this.puzzleType = puzzleType;
        this.title = title;
        this.subtitle = subtitle;
        this.hints = hints == null ? List.of() : hints;
        this.maxAttempts = maxAttempts;
        if (codeLength > 0) this.maxCodeLength = codeLength;
    }
    
    @Override
    protected int getWindowWidth() {
        return Math.min(560, width - 20);
    }

    @Override
    protected int getWindowHeight() {
        return Math.min(420, height - 20);
    }

    @Override
    protected void init() {
        super.init();
        computeCommonLayout();
        
        switch (puzzleType) {
            case "CODE_LOCK" -> { computeCodeLockLayout(); initCodeLockButtons(); }
            case "FINGERPRINT" -> initFingerprint();
            case "PIPE_CONNECT" -> initPipeConnect();
            case "SNAKE" -> initSnake();
            case "HEX_SEARCH" -> initHexSearch();
            case "MEMORY_SEQUENCE" -> initMemorySequence();
            case "SIGNAL_TUNING" -> initSignalTuning();
        }
    }

    private void computeCommonLayout() {
        panelWidth = guiWidth;
        panelHeight = guiHeight;
        panelX = guiLeft;
        panelY = guiTop;
        int headerBlock = (subtitle == null || subtitle.isEmpty()) ? 24 : 40;
        contentTop = panelY + PANEL_PADDING + headerBlock + HEADER_GAP;
        contentBottom = panelY + panelHeight - PANEL_PADDING - 18;
        leftColumnWidth = 180;
        rightColumnX = panelX + PANEL_PADDING + leftColumnWidth + 20;
        rightColumnWidth = panelX + panelWidth - PANEL_PADDING - rightColumnX;
    }

    private void computeCodeLockLayout() {
        gridWidth = 3 * CODE_BUTTON_SIZE + 2 * CODE_BUTTON_GAP;
        gridHeight = 4 * CODE_BUTTON_SIZE + 3 * CODE_BUTTON_GAP;
        displayWidth = Math.min(220, rightColumnWidth);
        displayX = rightColumnX + (rightColumnWidth - displayWidth) / 2;
        displayY = contentTop + 10;
        keypadStartX = rightColumnX + (rightColumnWidth - gridWidth) / 2;
        keypadStartY = displayY + 58;
    }

    private void initCodeLockButtons() {
        for (int i = 0; i < 9; i++) {
            int num = i + 1;
            int x = keypadStartX + (i % 3) * (CODE_BUTTON_SIZE + CODE_BUTTON_GAP);
            int y = keypadStartY + (i / 3) * (CODE_BUTTON_SIZE + CODE_BUTTON_GAP);
            addStrangerButton(x, y, CODE_BUTTON_SIZE, CODE_BUTTON_SIZE, Component.literal(String.valueOf(num)), () -> appendDigit(num));
        }
        int by = keypadStartY + 3 * (CODE_BUTTON_SIZE + CODE_BUTTON_GAP);
        addStrangerButton(keypadStartX, by, CODE_BUTTON_SIZE, CODE_BUTTON_SIZE, Component.literal("×"), () -> codeInput.setLength(0)).setGlowPulse(false);
        addStrangerButton(keypadStartX + (CODE_BUTTON_SIZE + CODE_BUTTON_GAP), by, CODE_BUTTON_SIZE, CODE_BUTTON_SIZE, Component.literal("0"), () -> appendDigit(0));
        addStrangerButton(keypadStartX + 2 * (CODE_BUTTON_SIZE + CODE_BUTTON_GAP), by, CODE_BUTTON_SIZE, CODE_BUTTON_SIZE, Component.literal("✓"), this::submitCode).setGlowPulse(true);
    }

    private void initFingerprint() {
        fragments.clear();
        int size = 42, gap = 12, cols = 4;
        int sx = rightColumnX + (rightColumnWidth - (cols*size + (cols-1)*gap)) / 2;
        int sy = contentTop + 60;
        for (int i = 0; i < 8; i++) fragments.add(new FingerprintFragment(i, sx + (i%cols)*(size+gap), sy + (i/cols)*(size+gap), size));
        correctFragmentsMask = 0;
        List<Integer> p = new ArrayList<>(); for(int i=0; i<8; i++) p.add(i); java.util.Collections.shuffle(p);
        for(int i=0; i<4; i++) correctFragmentsMask |= (1 << p.get(i));
        addStrangerButton(sx, sy + (8/cols + 1)*(size+gap), rightColumnWidth - (sx - rightColumnX)*2, 24, Component.literal("确认匹配 [VERIFY]"), this::submitFingerprint).setGlowPulse(true);
    }

    private void initPipeConnect() {
        pipeGrid = new PipeGrid(5, 5, rightColumnX, contentTop + 20, rightColumnWidth, contentBottom - contentTop - 80);
        addStrangerButton(rightColumnX + 20, contentBottom - 30, rightColumnWidth - 40, 24, Component.literal("能量重组 [RECONNECT]"), this::submitPipe).setGlowPulse(true);
    }

    private void initSnake() {
        snakeGame = new SnakeGame(rightColumnX, contentTop + 10, rightColumnWidth, contentBottom - contentTop - 20);
    }

    private void initHexSearch() {
        hexSearch = new HexSearch(rightColumnX, contentTop + 10, rightColumnWidth, contentBottom - contentTop - 20);
    }

    private void initMemorySequence() {
        memorySequence = new MemorySequence(rightColumnX, contentTop + 20, rightColumnWidth, contentBottom - contentTop - 40);
    }

    private void initSignalTuning() {
        signalTuning = new SignalTuning(rightColumnX, contentTop + 20, rightColumnWidth, contentBottom - contentTop - 60);
    }

    private void appendDigit(int d) { if (codeInput.length() < maxCodeLength) { codeInput.append(d); triggerGlitch(0.1f); } }
    private void submitCode() { sendResult(codeInput.toString()); codeInput.setLength(0); }
    private void submitFingerprint() {
        int m = 0; for(int i=0; i<8; i++) if(selectedFragments[i]) m |= (1 << i);
        if (m == correctFragmentsMask) sendResult("fingerprint_matched"); else { triggerGlitch(0.5f); checkAttempts(); }
    }
    private void submitPipe() { if (pipeGrid.isSolved()) sendResult("pipe_solved"); else { triggerGlitch(0.3f); checkAttempts(); } }

    private void checkAttempts() {
        sendResult("incorrect");
    }

    private void sendResult(String res) {
        currentAttempts++;
        triggerGlitch(0.2f);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.warmpixel.storyadventure.network.PuzzleInputPayload(res));
    }

    private void triggerGlitch(float amount) { glitchIntensity = Math.min(1.0f, glitchIntensity + amount); lastActionTime = System.currentTimeMillis(); }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        updateAnimations();
        renderPuzzlePanel(g);
        renderPuzzleHeader(g);
        
        // Update attempts from server state via some sync? 
        // For now, let's keep it simple, but we should probably not increment it locally if we want perfect sync.
        // Actually, the server doesn't sync currentAttempts back every time.
        // Let's assume the server will close it when done.
        
        g.pose().pushPose();
        if (glitchIntensity > 0.05f) g.pose().translate((random.nextFloat() - 0.5f) * 4 * glitchIntensity, (random.nextFloat() - 0.5f) * 4 * glitchIntensity, 0);

        switch (puzzleType) {
            case "CODE_LOCK" -> renderCodeDisplay(g);
            case "FINGERPRINT" -> renderFingerprint(g);
            case "PIPE_CONNECT" -> pipeGrid.render(g, mx, my);
            case "SNAKE" -> { snakeGame.update(); if(snakeGame.won) sendResult("snake_won"); else if(snakeGame.lost) { checkAttempts(); snakeGame.reset(); } snakeGame.render(g); }
            case "HEX_SEARCH" -> hexSearch.render(g, mx, my);
            case "MEMORY_SEQUENCE" -> { if(memorySequence.won) sendResult("memory_solved"); else if(memorySequence.lost) { checkAttempts(); memorySequence.restartLevel(); } memorySequence.render(g, mx, my); }
            case "SIGNAL_TUNING" -> { if(signalTuning.isMatched()) sendResult("signal_tuned"); signalTuning.render(g, mx, my); }
        }
        g.pose().popPose();

        renderHints(g);
        
        String att = String.format("安全干扰: %d/%d (拦截次数)", currentAttempts, maxAttempts);
        int col = currentAttempts >= maxAttempts - 1 ? 0xFFFF4444 : COLOR_TEXT_DIM;
        g.drawString(font, att, panelX + panelWidth - font.width(att) - 20, panelY + panelHeight - 20, col);

        // Scanline effect
        int scanY = (int)((System.currentTimeMillis() / 15) % (panelHeight - 40)) + 20;
        g.fill(panelX + 5, panelY + scanY, panelX + panelWidth - 5, panelY + scanY + 1, 0x1500FFFF);
    }

    private void updateAnimations() {
        glitchIntensity = Math.max(0, glitchIntensity - 0.02f);
    }

    private void renderPuzzlePanel(GuiGraphics g) {
        // We use the base class window background and frame.
        // Just draw the inner accent line and column divider.
        int accCol = (int)(Mth.sin(System.currentTimeMillis()/400.0f)*30 + 50) << 24 | (COLOR_NEON_RED & 0xFFFFFF);
        renderRectOutline(g, panelX + 2, panelY + 2, panelWidth - 4, panelHeight - 4, accCol);
        g.fill(rightColumnX - 10, contentTop, rightColumnX - 9, contentBottom, 0x30FFFFFF);
    }

    private void renderPuzzleHeader(GuiGraphics g) {
        String h = title.isEmpty() ? getPuzzleTitle() : title;
        g.drawString(font, h, panelX + 20, panelY + 15, COLOR_TEXT_TITLE);
        g.fill(panelX + 20, panelY + 26, panelX + 20 + font.width(h), panelY + 27, COLOR_NEON_RED);
        if(!subtitle.isEmpty()) g.drawString(font, subtitle, panelX+20, panelY+32, COLOR_TEXT_DIM);
    }

    private void renderCodeDisplay(GuiGraphics g) {
        g.fill(displayX, displayY, displayX + displayWidth, displayY + 48, 0xFF020406);
        renderRectOutline(g, displayX, displayY, displayWidth, 48, COLOR_NEON_RED);
        for (int i = 0; i < maxCodeLength; i++) {
            int dx = displayX + (displayWidth - (maxCodeLength*28))/2 + i*28;
            String s = i < codeInput.length() ? String.valueOf(codeInput.charAt(i)) : "_";
            g.drawCenteredString(font, s, dx + 10, displayY + 20, COLOR_NEON_RED);
        }
    }

    private void renderFingerprint(GuiGraphics g) {
        int rx = rightColumnX, ry = contentTop;
        g.drawString(font, "比对目标 [TARGET SIGNAL]:", rx, ry, COLOR_TEXT_DIM);
        g.drawString(font, "HEX CODE: 0x" + Integer.toHexString(correctFragmentsMask).toUpperCase(), rx, ry + 12, COLOR_NEON_PINK);
        for(FingerprintFragment f : fragments) f.render(g, selectedFragments[f.id]);
    }

    private void renderHints(GuiGraphics g) {
        if(hints.isEmpty()) return;
        int y = contentTop + 5;
        g.drawString(font, "数据库检索:", panelX+20, y, COLOR_TEXT_BODY);
        g.fill(panelX+20, y+11, panelX+80, y+12, COLOR_NEON_PINK);
        int ly = y + 20;
        for(String h : hints) { g.drawString(font, "> " + h, panelX+20, ly, COLOR_TEXT_DIM); ly += 14; if(ly > contentBottom-10) break; }
    }

    private String getPuzzleTitle() {
        return switch(puzzleType) {
            case "FINGERPRINT" -> "生物特征识别"; case "PIPE_CONNECT" -> "节点重组程序"; case "SNAKE" -> "数据旁路注入";
            case "HEX_SEARCH" -> "十六进制缓冲区检索"; case "MEMORY_SEQUENCE" -> "神经链路同步";
            case "SIGNAL_TUNING" -> "波形拦截频率"; default -> "安全协议解密";
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (puzzleType.equals("FINGERPRINT")) { for(FingerprintFragment f : fragments) if(f.isHovered(mx, my)) { selectedFragments[f.id] = !selectedFragments[f.id]; triggerGlitch(0.05f); return true; } }
        else if (puzzleType.equals("PIPE_CONNECT")) { if(pipeGrid.mouseClicked(mx, my)) { triggerGlitch(0.05f); return true; } }
        else if (puzzleType.equals("MEMORY_SEQUENCE")) { if(memorySequence.mouseClicked(mx, my)) { triggerGlitch(0.1f); return true; } }
        else if (puzzleType.equals("SIGNAL_TUNING")) { if(signalTuning.mouseClicked(mx, my)) return true; }
        else if (puzzleType.equals("HEX_SEARCH")) { if(hexSearch.mouseClicked(mx, my)) return true; }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k >= 0 && k < 512) keysDown[k] = true;
        if (k == 256) return false;
        if (puzzleType.equals("CODE_LOCK")) {
            if(k >= 48 && k <= 57) { appendDigit(k - 48); return true; }
            if(k == 259 && codeInput.length() > 0) { codeInput.deleteCharAt(codeInput.length()-1); return true; }
            if(k == 257) { submitCode(); return true; }
        } else if (puzzleType.equals("SNAKE")) {
            if(k == 265) snakeGame.setDir(0, -1); else if(k == 264) snakeGame.setDir(0, 1);
            else if(k == 263) snakeGame.setDir(-1, 0); else if(k == 262) snakeGame.setDir(1, 0);
        }
        return super.keyPressed(k, s, m);
    }

    @Override
    public boolean keyReleased(int k, int s, int m) {
        if (k >= 0 && k < 512) keysDown[k] = false;
        return super.keyReleased(k, s, m);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    // Helper: Fingerprint
    private class FingerprintFragment {
        int id, x, y, size;
        FingerprintFragment(int i, int x, int y, int s) { id=i; this.x=x; this.y=y; size=s; }
        void render(GuiGraphics g, boolean sel) {
            int c = sel ? COLOR_NEON_RED : COLOR_BORDER;
            renderRectOutline(g, x, y, size, size, c);
            if(sel) g.fill(x+2, y+2, x+size-2, y+size-2, 0x403BB6A6);
            for(int i=0; i<4; i++) { int iy = y+6+i*8; g.fill(x+4, iy, x+size-4, iy+1, c & 0x66FFFFFF); }
            g.drawCenteredString(font, "#"+(id+1), x+size/2, y+size/2-4, sel ? 0xFFFFFFFF : COLOR_TEXT_DIM);
        }
        boolean isHovered(double mx, double my) { return mx>=x && mx<x+size && my>=y && my<y+size; }
    }

    // Helper: Pipe Grid
    private class PipeGrid {
        int r, c, x, y, cs; int[][] types, rots;
        PipeGrid(int r, int c, int x, int y, int w, int h) {
            this.r=r; this.c=c; this.x=x; this.y=y; cs = Math.min(w/c, (h-20)/r);
            types=new int[r][c]; rots=new int[r][c];
            for(int i=0; i<r; i++) for(int j=0; j<c; j++) { types[i][j]=random.nextInt(2)+1; rots[i][j]=random.nextInt(4); }
        }
        void render(GuiGraphics g, int mx, int my) {
            for(int i=0; i<r; i++) for(int j=0; j<c; j++) {
                int cx=x+j*cs, cy=y+i*cs; boolean term = (i==0&&j==0)||(i==r-1&&j==c-1);
                renderRectOutline(g, cx, cy, cs, cs, term ? COLOR_NEON_RED : 0x20FFFFFF);
                String s = types[i][j]==1 ? (rots[i][j]%2==0?"│":"─") : (rots[i][j]==0?"└":rots[i][j]==1?"┌":rots[i][j]==2?"┐":"┘");
                g.drawCenteredString(font, s, cx+cs/2, cy+cs/2-4, term ? COLOR_NEON_RED : COLOR_TEXT_BODY);
            }
        }
        boolean mouseClicked(double mx, double my) {
            int col=(int)((mx-x)/cs), row=(int)((my-y)/cs);
            if(col>=0 && col<c && row>=0 && row<r) { rots[row][col]=(rots[row][col]+1)%4; return true; } return false;
        }
        boolean isSolved() {
            boolean[][] v = new boolean[r][c]; java.util.Queue<int[]> q = new java.util.LinkedList<>(); q.add(new int[]{0,0}); v[0][0]=true;
            while(!q.isEmpty()){
                int[] curr=q.poll(); int cr=curr[0], cc=curr[1]; if(cr==r-1&&cc==c-1) return true;
                int[][] neighbors = {{-1,0,0,1},{1,0,1,0},{0,-1,2,3},{0,1,3,2}};
                for(int[] n : neighbors){
                    int nr=cr+n[0], nc=cc+n[1];
                    if(nr>=0 && nr<r && nc>=0 && nc < c && !v[nr][nc]){
                        boolean[] p1 = getO(cr,cc), p2 = getO(nr,nc); 
                        if(p1[n[2]] && p2[n[3]]) { v[nr][nc]=true; q.add(new int[]{nr,nc}); }
                    }
                }
            } return false;
        }
        boolean[] getO(int r, int c) {
            boolean[] o = new boolean[4]; int t=types[r][c], rt=rots[r][c];
            if(t==1) { if(rt%2==0) o[0]=o[1]=true; else o[2]=o[3]=true; }
            else { if(rt==0) o[0]=o[3]=true; else if(rt==1) o[1]=o[3]=true; else if(rt==2) o[1]=o[2]=true; else o[0]=o[2]=true; }
            return o;
        }
    }

    // Helper: Snake
    private class SnakeGame {
        List<int[]> body = new ArrayList<>(); int dx=1, dy=0, fx, fy, x, y, w, h, gsz=10; long lt=0; boolean won=false, lost=false;
        SnakeGame(int x, int y, int w, int h) { this.x=x; this.y=y; this.w=w; this.h=h; reset(); }
        void reset() { body.clear(); body.add(new int[]{5,5}); spawnF(); dx=1; dy=0; lost=false; won=false; }
        void spawnF() { fx=random.nextInt(Math.max(1, w/gsz)); fy=random.nextInt(Math.max(1, h/gsz)); }
        void update() {
            if(System.currentTimeMillis()-lt<120) return; lt=System.currentTimeMillis();
            int[] head = body.get(0); int nx=head[0]+dx, ny=head[1]+dy;
            if(nx<0||nx>=w/gsz||ny<0||ny>=h/gsz) { lost=true; return; }
            for(int[] b : body) if(b[0]==nx && b[1]==ny) { lost=true; return; }
            body.add(0, new int[]{nx,ny}); if(nx==fx && ny==fy) { if(body.size()>8) won=true; else spawnF(); } else body.remove(body.size()-1);
        }
        void setDir(int x, int y) { if(dx+x!=0 || dy+y!=0) { dx=x; dy=y; } }
        void render(GuiGraphics g) {
            g.fill(x, y, x+w, y+h, 0xFF050709); renderRectOutline(g, x, y, w, h, COLOR_BORDER);
            for(int[] p : body) g.fill(x+p[0]*gsz, y+p[1]*gsz, x+p[0]*gsz+8, y+p[1]*gsz+8, COLOR_NEON_RED);
            g.fill(x+fx*gsz, y+fy*gsz, x+fx*gsz+8, y+fy*gsz+8, (int)(Mth.sin(System.currentTimeMillis()/200.0f)*100+155)<<24 | (COLOR_NEON_PINK&0xFFFFFF));
            g.drawString(font, "长度: " + body.size() + "/9", x+5, y+5, COLOR_TEXT_DIM);
        }
    }

    // Helper: Hex Search
    private class HexSearch {
        String[][] grid = new String[10][10]; String target; int x, y, w, h, cw, ch, tr, tc;
        HexSearch(int x, int y, int w, int h) {
            this.x=x; this.y=y; this.w=w; this.h=h; cw=w/10; ch=h/10;
            for(int i=0; i<10; i++) for(int j=0; j<10; j++) grid[i][j]=Integer.toHexString(random.nextInt(256)).toUpperCase();
            tr=random.nextInt(10); tc=random.nextInt(10); target = grid[tr][tc];
        }
        void render(GuiGraphics g, int mx, int my) {
            g.drawString(font, "寻找特征串: " + target, x, y - 10, COLOR_NEON_PINK);
            for(int i=0; i<10; i++) for(int j=0; j<10; j++) {
                int cx=x+j*cw, cy=y+i*ch; boolean hov = mx>=cx&&mx<cx+cw&&my>=cy&&my<cy+ch;
                g.drawString(font, grid[i][j], cx, cy, hov ? 0xFFFFFFFF : COLOR_TEXT_DIM);
            }
        }
        boolean mouseClicked(double mx, double my) {
            int col=(int)((mx-x)/cw), row=(int)((my-y)/ch);
            if(row==tr && col==tc) { sendResult("hex_found"); return true; }
            return false;
        }
    }

    // Helper: Memory Sequence (Simon Says)
    private class MemorySequence {
        int x, y, w, h, size=40, gap=10; List<Integer> sequence = new ArrayList<>(), input = new ArrayList<>();
        int playingIdx = -1; long lastStep = 0; boolean won=false, lost=false;
        MemorySequence(int x, int y, int w, int h) { this.x=x; this.y=y; this.w=w; this.h=h; nextLevel(); }
        void nextLevel() { sequence.add(random.nextInt(4)); input.clear(); playingIdx = 0; lastStep = System.currentTimeMillis(); if(sequence.size()>5) won=true; }
        void restartLevel() { input.clear(); playingIdx = 0; lastStep = System.currentTimeMillis(); lost=false; }
        void render(GuiGraphics g, int mx, int my) {
            if (playingIdx != -1 && System.currentTimeMillis()-lastStep > 600) { playingIdx++; lastStep=System.currentTimeMillis(); if(playingIdx>=sequence.size()) playingIdx=-1; }
            for(int i=0; i<4; i++) {
                int bx=x+(w-size*2-gap)/2+(i%2)*(size+gap), by=y+(h-size*2-gap)/2+(i/2)*(size+gap);
                boolean lit = playingIdx != -1 && sequence.get(playingIdx) == i;
                int c = lit ? (i==0?0xFF00FF00 : i==1?0xFFFF0000 : i==2?0xFF0000FF : 0xFFFFFF00) : 0x40FFFFFF;
                g.fill(bx, by, bx+size, by+size, c); renderRectOutline(g, bx, by, size, size, COLOR_BORDER);
            }
            g.drawCenteredString(font, "同步进度: " + (sequence.size()-1) + "/5", x+w/2, y, COLOR_TEXT_DIM);
        }
        boolean mouseClicked(double mx, double my) {
            if(playingIdx != -1) return false;
            for(int i=0; i<4; i++) {
                int bx=x+(w-size*2-gap*2)/2+(i%2)*(size+gap*2), by=y+(h-size*2-gap*2)/2+(i/2)*(size+gap*2);
                if(mx>=bx && mx<bx+size && my>=by && my<by+size) {
                    input.add(i); triggerGlitch(0.1f);
                    if(sequence.get(input.size()-1) != i) { lost=true; return true; }
                    if(input.size() == sequence.size()) nextLevel(); return true;
                }
            } return false;
        }
    }

    // Helper: Signal Tuning
    private class SignalTuning {
        int x, y, w, h; float freq=1f, amp=1f, tfreq, tamp;
        SignalTuning(int x, int y, int w, int h) { this.x=x; this.y=y; this.w=w; this.h=h; tfreq=0.5f+random.nextFloat()*2.5f; tamp=0.5f+random.nextFloat()*1.5f; }
        void render(GuiGraphics g, int mx, int my) {
            g.fill(x, y, x+w, y+h, 0xFF05080E); renderRectOutline(g, x, y, w, h, COLOR_BORDER);
            // Grid lines
            for(int i=1; i<4; i++) { g.fill(x, y+h*i/4, x+w, y+h*i/4+1, 0x15FFFFFF); g.fill(x+w*i/4, y, x+w*i/4+1, y+h, 0x15FFFFFF); }
            
            // Draw Waves
            for(int i=0; i<w; i++) {
                float y1 = y+h/2 + Mth.sin(i*0.1f*tfreq)*20*tamp;
                float y2 = y+h/2 + Mth.sin(i*0.1f*freq)*20*amp;
                g.fill(x+i, (int)y1, x+i+1, (int)y1+2, 0x6000FFFF); g.fill(x+i, (int)y2, x+i+1, (int)y2+2, COLOR_NEON_RED);
            }
            g.drawString(font, "频道频率: " + String.format("%.2f", freq), x+5, y+5, COLOR_TEXT_DIM);
            g.drawString(font, "信号强度: " + String.format("%.2f", amp), x+5, y+17, COLOR_TEXT_DIM);
            
            g.drawString(font, "频率调制 [W/S]", x+w-font.width("频率调制 [W/S]")-5, y+h-25, COLOR_TEXT_DIM);
            g.drawString(font, "振幅调制 [A/D]", x+w-font.width("振幅调制 [A/D]")-5, y+h-12, COLOR_TEXT_DIM);
            
            if(keysDown[87]) freq+=0.015f; // W
            if(keysDown[83]) freq-=0.015f; // S
            if(keysDown[68]) amp+=0.015f;  // D
            if(keysDown[65]) amp-=0.015f;  // A
            
            freq = Mth.clamp(freq, 0.1f, 4.5f); amp = Mth.clamp(amp, 0.1f, 3.5f);
        }
        boolean mouseClicked(double mx, double my) { return false; }
        boolean isMatched() { return Math.abs(freq-tfreq)<0.12f && Math.abs(amp-tamp)<0.12f; }
    }
}
