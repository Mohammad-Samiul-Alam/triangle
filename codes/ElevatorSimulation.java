// ============================================================
//  Elevator (Lift) Simulation — Single File Version
//  Compile:  javac ElevatorSimulation.java
//  Run:      java ElevatorSimulation
// ============================================================

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// ─────────────────────────────────────────────────────────────
//  ENTRY POINT
// ─────────────────────────────────────────────────────────────
public class ElevatorSimulation {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ElevatorFrame frame = new ElevatorFrame();
            frame.setVisible(true);
        });
    }
}

// ─────────────────────────────────────────────────────────────
//  MODEL  — state machine, SCAN queue, emergency stop
// ─────────────────────────────────────────────────────────────
class ElevatorModel {

    enum State { IDLE, MOVING_UP, MOVING_DOWN, DOOR_OPEN, DOOR_CLOSE }

    // ── Configuration ──────────────────────────────────────────
    static final int TOTAL_FLOORS = 8;
    static final int FLOOR_HEIGHT = 80;   // pixels per floor
    static final int SHAFT_X      = 160;  // left edge of shaft
    static final int SHAFT_WIDTH  = 100;

    static int floorY(int floor) {
        return (TOTAL_FLOORS - floor) * FLOOR_HEIGHT;
    }

    // ── State fields ───────────────────────────────────────────
    private double  currentFloor    = 1.0;
    private State   state           = State.IDLE;
    private double  doorOpen        = 0.0;   // 0=closed, 1=open
    private int     doorHoldCounter = 0;
    private boolean emergencyStopped = false;

    private final Deque<Integer> queue         = new ArrayDeque<>();
    private final Set<Integer>   pendingFloors = new HashSet<>();

    // Animation speeds (per timer tick ≈ 16 ms / 60 fps)
    private static final double MOVE_SPEED      = 0.04;
    private static final double DOOR_SPEED      = 0.05;
    private static final int    DOOR_HOLD_TICKS = 60;   // ~1 second

    // ── Listener interface ─────────────────────────────────────
    interface ElevatorListener {
        void onStateChanged(State newState, int floor);
        void onTick();
    }
    private final java.util.List<ElevatorListener> listeners = new ArrayList<>();
    void addListener(ElevatorListener l) { listeners.add(l); }

    // ── Public API ─────────────────────────────────────────────

    void requestFloor(int floor) {
        if (emergencyStopped) return;
        if (floor < 1 || floor > TOTAL_FLOORS) return;
        if (pendingFloors.contains(floor)) return;
        if (Math.round(currentFloor) == floor && state == State.IDLE) {
            startDoorOpen();
            return;
        }
        pendingFloors.add(floor);
        rebuildQueue();
    }

    void emergencyStop() {
        emergencyStopped = true;
        queue.clear();
        pendingFloors.clear();
        doorOpen        = 0.0;
        doorHoldCounter = 0;
        state = State.IDLE;
        int fl = (int) Math.round(currentFloor);
        listeners.forEach(l -> l.onStateChanged(State.IDLE, fl));
    }

    void resetEmergency() { emergencyStopped = false; }

    boolean isEmergencyStopped() { return emergencyStopped; }

    /** Drives the entire animation — called by Swing Timer every ~16 ms */
    void tick() {
        if (emergencyStopped) return;
        switch (state) {
            case IDLE        -> handleIdle();
            case MOVING_UP   -> handleMovingUp();
            case MOVING_DOWN -> handleMovingDown();
            case DOOR_OPEN   -> handleDoorOpen();
            case DOOR_CLOSE  -> handleDoorClose();
        }
        listeners.forEach(ElevatorListener::onTick);
    }

    // ── Getters ────────────────────────────────────────────────
    double getCurrentFloor()         { return currentFloor; }
    State  getState()                { return state; }
    double getDoorOpen()             { return doorOpen; }
    Set<Integer> getPendingFloors()  { return Collections.unmodifiableSet(pendingFloors); }
    int getCabinPixelY()             { return (int)((TOTAL_FLOORS - currentFloor) * FLOOR_HEIGHT); }

    // ── State-machine handlers ─────────────────────────────────

    private void handleIdle() {
        if (queue.isEmpty()) return;
        int target = queue.peek();
        if      (target > Math.round(currentFloor)) setState(State.MOVING_UP);
        else if (target < Math.round(currentFloor)) setState(State.MOVING_DOWN);
        else {
            queue.poll();
            pendingFloors.remove(target);
            startDoorOpen();
        }
    }

    private void handleMovingUp() {
        currentFloor += MOVE_SPEED;
        int target = queue.isEmpty() ? -1 : queue.peek();
        if (currentFloor >= target) {
            currentFloor = target;
            queue.poll();
            pendingFloors.remove(target);
            startDoorOpen();
        }
    }

    private void handleMovingDown() {
        currentFloor -= MOVE_SPEED;
        int target = queue.isEmpty() ? -1 : queue.peek();
        if (currentFloor <= target) {
            currentFloor = target;
            queue.poll();
            pendingFloors.remove(target);
            startDoorOpen();
        }
    }

    private void handleDoorOpen() {
        if (doorOpen < 1.0) {
            doorOpen = Math.min(1.0, doorOpen + DOOR_SPEED);
        } else {
            if (++doorHoldCounter >= DOOR_HOLD_TICKS) {
                doorHoldCounter = 0;
                setState(State.DOOR_CLOSE);
            }
        }
    }

    private void handleDoorClose() {
        doorOpen = Math.max(0.0, doorOpen - DOOR_SPEED);
        if (doorOpen == 0.0) setState(State.IDLE);
    }

    // ── Helpers ────────────────────────────────────────────────

    private void startDoorOpen() {
        doorOpen        = 0.0;
        doorHoldCounter = 0;
        setState(State.DOOR_OPEN);
    }

    /** SCAN algorithm: serve current direction first, then reverse */
    private void rebuildQueue() {
        if (pendingFloors.isEmpty()) return;
        java.util.List<Integer> above = new ArrayList<>(), below = new ArrayList<>();
        int cur = (int) Math.round(currentFloor);
        for (int f : pendingFloors) {
            if      (f > cur) above.add(f);
            else if (f < cur) below.add(f);
        }
        Collections.sort(above);
        Collections.sort(below, Collections.reverseOrder());
        queue.clear();
        if (state == State.MOVING_UP || state == State.IDLE) {
            above.forEach(queue::add);
            below.forEach(queue::add);
        } else {
            below.forEach(queue::add);
            above.forEach(queue::add);
        }
    }

    private void setState(State s) {
        state = s;
        int fl = (int) Math.round(currentFloor);
        listeners.forEach(l -> l.onStateChanged(s, fl));
    }
}

// ─────────────────────────────────────────────────────────────
//  SHAFT PANEL  — animated graphical shaft + cabin + doors
// ─────────────────────────────────────────────────────────────
class ShaftPanel extends JPanel {

    private static final int SHAFT_X      = ElevatorModel.SHAFT_X;
    private static final int SHAFT_W      = ElevatorModel.SHAFT_WIDTH;
    private static final int FLOOR_H      = ElevatorModel.FLOOR_HEIGHT;
    private static final int TOTAL_FLOORS = ElevatorModel.TOTAL_FLOORS;
    private static final int PANEL_W      = 320;
    private static final int PANEL_H      = TOTAL_FLOORS * FLOOR_H + 60;

    // Colour palette
    private static final Color COL_BG           = new Color(18, 20, 30);
    private static final Color COL_SHAFT_BG     = new Color(28, 32, 48);
    private static final Color COL_SHAFT_EDGE   = new Color(60, 70, 100);
    private static final Color COL_FLOOR_LINE   = new Color(50, 60, 85);
    private static final Color COL_CABIN        = new Color(55, 65, 95);
    private static final Color COL_DOOR         = new Color(120, 140, 200);
    private static final Color COL_DOOR_EDGE    = new Color(160, 180, 230);
    private static final Color COL_INDICATOR    = new Color(255, 210, 40);
    private static final Color COL_ARROW_UP     = new Color(80, 220, 160);
    private static final Color COL_ARROW_DN     = new Color(255, 120, 100);
    private static final Color COL_FLOOR_NUM    = new Color(100, 115, 160);
    private static final Color COL_FLOOR_ACTIVE = new Color(255, 210, 80);
    private static final Color COL_EMERG        = new Color(255, 60, 60);

    private final ElevatorModel model;

    ShaftPanel(ElevatorModel model) {
        this.model = model;
        setPreferredSize(new Dimension(PANEL_W, PANEL_H));
        setBackground(COL_BG);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,     RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int hdr = 55;
        g2.translate(0, hdr);
        drawShaft(g2);
        drawFloorLabels(g2);
        drawCabin(g2);
        g2.translate(0, -hdr);
        drawHeader(g2, hdr);

        // Red overlay flash during emergency
        if (model.isEmergencyStopped()) {
            g2.setColor(new Color(200, 30, 30, 35));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(COL_EMERG);
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.drawString("⛔ EMERGENCY STOP ACTIVE", SHAFT_X - 55, hdr + TOTAL_FLOORS * FLOOR_H / 2);
        }
    }

    // ── Header — digital floor indicator ───────────────────────

    private void drawHeader(Graphics2D g2, int hdr) {
        g2.setColor(new Color(10, 12, 20));
        g2.fillRoundRect(SHAFT_X - 5, 4, SHAFT_W + 10, hdr - 8, 8, 8);
        g2.setColor(model.isEmergencyStopped() ? COL_EMERG : COL_SHAFT_EDGE);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(SHAFT_X - 5, 4, SHAFT_W + 10, hdr - 8, 8, 8);

        int floor = (int) Math.round(model.getCurrentFloor());
        String floorTxt = String.valueOf(floor);
        g2.setFont(new Font("Monospaced", Font.BOLD, 26));
        FontMetrics fm = g2.getFontMetrics();
        int tx = SHAFT_X + (SHAFT_W - fm.stringWidth(floorTxt)) / 2;
        int ty = 4 + (hdr - 8) / 2 + fm.getAscent() / 2 - 4;

        // Glow + number
        g2.setColor(new Color(255, 210, 40, 60));
        g2.drawString(floorTxt, tx - 1, ty - 1);
        g2.setColor(model.isEmergencyStopped() ? COL_EMERG : COL_INDICATOR);
        g2.drawString(floorTxt, tx, ty);

        // Direction arrow
        ElevatorModel.State st = model.getState();
        if (st == ElevatorModel.State.MOVING_UP) {
            g2.setColor(COL_ARROW_UP);
            drawTriangle(g2, SHAFT_X + SHAFT_W - 18, hdr / 2 - 10, 14, true);
        } else if (st == ElevatorModel.State.MOVING_DOWN) {
            g2.setColor(COL_ARROW_DN);
            drawTriangle(g2, SHAFT_X + SHAFT_W - 18, hdr / 2, 14, false);
        }

        // State text
        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2.setColor(new Color(120, 140, 180));
        String stateTxt = model.isEmergencyStopped() ? "EMERGENCY STOP"
                          : st.toString().replace('_', ' ');
        g2.drawString(stateTxt, SHAFT_X + 2, hdr - 4);
    }

    private void drawTriangle(Graphics2D g2, int x, int y, int size, boolean up) {
        int[] xs = {x, x + size / 2, x + size};
        int[] ys = up ? new int[]{y + size, y, y + size} : new int[]{y, y + size, y};
        g2.fillPolygon(xs, ys, 3);
    }

    // ── Shaft ──────────────────────────────────────────────────

    private void drawShaft(Graphics2D g2) {
        int shaftH = TOTAL_FLOORS * FLOOR_H;
        g2.setColor(COL_SHAFT_BG);
        g2.fillRect(SHAFT_X, 0, SHAFT_W, shaftH);

        GradientPaint gp = new GradientPaint(
            SHAFT_X, 0, new Color(35, 40, 65, 80),
            SHAFT_X + SHAFT_W, 0, new Color(20, 25, 45, 40));
        g2.setPaint(gp);
        g2.fillRect(SHAFT_X, 0, SHAFT_W, shaftH);
        g2.setPaint(null);

        g2.setStroke(new BasicStroke(1f));
        for (int f = 1; f <= TOTAL_FLOORS; f++) {
            int y = ElevatorModel.floorY(f);
            g2.setColor(COL_FLOOR_LINE);
            g2.drawLine(SHAFT_X, y + FLOOR_H, SHAFT_X + SHAFT_W, y + FLOOR_H);
            g2.setColor(new Color(45, 55, 80));
            for (int h = 0; h < FLOOR_H; h += 12) {
                g2.drawLine(SHAFT_X,           y + h, SHAFT_X + 6,         y + h + 6);
                g2.drawLine(SHAFT_X + SHAFT_W - 6, y + h, SHAFT_X + SHAFT_W, y + h + 6);
            }
        }
        g2.setColor(COL_SHAFT_EDGE);
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawRect(SHAFT_X, 0, SHAFT_W, shaftH);
    }

    // ── Floor labels ───────────────────────────────────────────

    private void drawFloorLabels(Graphics2D g2) {
        g2.setFont(new Font("Monospaced", Font.BOLD, 13));
        FontMetrics fm = g2.getFontMetrics();
        Set<Integer> pending = model.getPendingFloors();
        int curFloor = (int) Math.round(model.getCurrentFloor());

        for (int floor = 1; floor <= TOTAL_FLOORS; floor++) {
            int y    = ElevatorModel.floorY(floor);
            int midY = y + FLOOR_H / 2 + fm.getAscent() / 2 - 2;
            String lbl = "F" + floor;
            int lx = SHAFT_X - fm.stringWidth(lbl) - 10;

            boolean active = pending.contains(floor) || floor == curFloor;
            if (active) {
                g2.setColor(COL_FLOOR_ACTIVE);
                g2.fillOval(SHAFT_X - 8, midY - fm.getAscent() / 2, 5, 5);
            } else {
                g2.setColor(COL_FLOOR_NUM);
            }
            g2.drawString(lbl, lx, midY);
        }
    }

    // ── Cabin ──────────────────────────────────────────────────

    private void drawCabin(Graphics2D g2) {
        int cabinY = model.getCabinPixelY();
        int cabinX = SHAFT_X + 3;
        int cabinW = SHAFT_W - 6;
        int cabinH = FLOOR_H - 4;

        // Shadow
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillRect(cabinX + 4, cabinY + cabinH, cabinW - 8, 6);

        // Body gradient — red tint during emergency
        Color topCol = model.isEmergencyStopped() ? new Color(130, 40, 40) : new Color(70, 85, 130);
        GradientPaint grad = new GradientPaint(cabinX, cabinY, topCol,
                                               cabinX + cabinW, cabinY + cabinH, COL_CABIN);
        g2.setPaint(grad);
        g2.fillRoundRect(cabinX, cabinY + 2, cabinW, cabinH, 6, 6);
        g2.setPaint(null);

        // Interior glow when doors open
        double d = model.getDoorOpen();
        if (d > 0.0) {
            g2.setColor(new Color(220, 235, 255, (int)(d * 90)));
            g2.fillRoundRect(cabinX + 4, cabinY + 6, cabinW - 8, cabinH - 8, 4, 4);
        }

        // Outline
        g2.setColor(model.isEmergencyStopped() ? COL_EMERG : new Color(130, 150, 210));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(cabinX, cabinY + 2, cabinW, cabinH, 6, 6);

        // Roof line + cable
        g2.setColor(new Color(170, 185, 230, 180));
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(cabinX + 6, cabinY + 6, cabinX + cabinW - 6, cabinY + 6);
        g2.setColor(new Color(100, 115, 160, 180));
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(cabinX + cabinW / 2, 0, cabinX + cabinW / 2, cabinY + 2);

        drawDoors(g2, cabinX, cabinY + 2, cabinW, cabinH, d);
    }

    private void drawDoors(Graphics2D g2, int cx, int cy, int cw, int ch, double open) {
        int doorW = cw / 2 - 2;
        int doorH = ch - 4;
        int doorY = cy + 2;
        int slide = (int)(open * (doorW - 4));
        int lx = cx + 2 - slide;
        int rx = cx + cw / 2 + 2 + slide;

        GradientPaint doorGrad = new GradientPaint(cx, doorY, COL_DOOR,
                                                    cx + cw, doorY, new Color(90, 110, 175));
        g2.setClip(cx + 2, doorY, cw - 4, doorH);
        g2.setPaint(doorGrad);

        if (slide < doorW - 4) {
            g2.fillRect(lx, doorY, doorW, doorH);
            g2.fillRect(rx, doorY, doorW, doorH);

            g2.setColor(COL_DOOR_EDGE);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(lx, doorY, doorW - 1, doorH - 1);
            g2.drawRect(rx, doorY, doorW - 1, doorH - 1);

            if (open < 0.95) {
                g2.setColor(new Color(15, 18, 30));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(cx + cw / 2, doorY, cx + cw / 2, doorY + doorH);
            }

            g2.setColor(new Color(200, 210, 240));
            g2.fillOval(lx + doorW - 8, doorY + doorH / 2 - 3, 5, 5);
            g2.fillOval(rx + 3,         doorY + doorH / 2 - 3, 5, 5);
        }
        g2.setClip(null);
        g2.setPaint(null);
    }
}

// ─────────────────────────────────────────────────────────────
//  CONTROL PANEL  — floor buttons, status, log, emergency stop
// ─────────────────────────────────────────────────────────────
class ControlPanel extends JPanel {

    private static final int TOTAL = ElevatorModel.TOTAL_FLOORS;

    private final ElevatorModel model;
    private final JButton[]     floorButtons = new JButton[TOTAL + 1];
    private final JLabel        statusLabel;
    private final JLabel        doorLabel;
    private final JTextArea     logArea;

    private static final Color BG           = new Color(18, 20, 30);
    private static final Color BTN_IDLE     = new Color(35, 42, 65);
    private static final Color BTN_PENDING  = new Color(200, 160, 30);
    private static final Color BTN_FG_IDLE  = new Color(160, 180, 220);
    private static final Color BTN_FG_PEND  = new Color(20, 18, 10);
    private static final Color ACCENT       = new Color(80, 200, 160);
    private static final Color PANEL_BORDER = new Color(55, 65, 100);

    ControlPanel(ElevatorModel model) {
        this.model = model;
        setBackground(BG);
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("ELEVATOR CONTROL", SwingConstants.CENTER);
        title.setFont(new Font("Monospaced", Font.BOLD, 13));
        title.setForeground(ACCENT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        add(title, BorderLayout.NORTH);

        // ── Status labels (initialised before buildStatusPanel uses them)
        statusLabel = new JLabel("Floor: 1", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Monospaced", Font.BOLD, 15));
        statusLabel.setForeground(BTN_PENDING);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(30, 35, 55));

        doorLabel = new JLabel("Doors: CLOSED", SwingConstants.CENTER);
        doorLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        doorLabel.setForeground(new Color(160, 180, 220));
        doorLabel.setOpaque(true);
        doorLabel.setBackground(new Color(25, 28, 45));

        JPanel centre = new JPanel(new BorderLayout(0, 10));
        centre.setBackground(BG);
        centre.add(buildButtonGrid(),  BorderLayout.NORTH);
        centre.add(buildStatusPanel(), BorderLayout.CENTER);
        add(centre, BorderLayout.CENTER);

        // ── Event log ──
        logArea = new JTextArea(6, 20);
        logArea.setEditable(false);
        logArea.setBackground(new Color(12, 14, 22));
        logArea.setForeground(new Color(130, 160, 130));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(PANEL_BORDER));

        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBackground(BG);
        JLabel logTitle = new JLabel("EVENT LOG");
        logTitle.setFont(new Font("Monospaced", Font.BOLD, 10));
        logTitle.setForeground(new Color(100, 120, 160));
        logTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
        logPanel.add(logTitle, BorderLayout.NORTH);
        logPanel.add(scroll,   BorderLayout.CENTER);
        add(logPanel, BorderLayout.SOUTH);
    }

    // ── Floor button grid ──────────────────────────────────────

    private JPanel buildButtonGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 6, 6));
        grid.setBackground(BG);
        grid.setBorder(titledBorder("FLOOR SELECTION"));

        for (int f = TOTAL; f >= 1; f--) {
            final int floor = f;
            JButton btn = new JButton("F" + floor);
            btn.setFont(new Font("Monospaced", Font.BOLD, 13));
            btn.setFocusPainted(false);
            styleButton(btn, false);
            btn.addActionListener(e -> {
                model.requestFloor(floor);
                log("Request: Floor " + floor);
                refreshButtons();
            });
            floorButtons[floor] = btn;
            grid.add(btn);
        }
        return grid;
    }

    // ── Status + emergency stop panel ─────────────────────────

    private JPanel buildStatusPanel() {
        JPanel p = new JPanel(new GridLayout(3, 1, 4, 4));
        p.setBackground(BG);
        p.setBorder(titledBorder("STATUS"));

        JButton stop = new JButton("⛔  EMERGENCY STOP");
        stop.setFont(new Font("SansSerif", Font.BOLD, 12));
        stop.setBackground(new Color(160, 35, 35));
        stop.setForeground(Color.WHITE);
        stop.setFocusPainted(false);
        stop.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        stop.addActionListener(e -> {
            if (!model.isEmergencyStopped()) {
                model.emergencyStop();
                stop.setText("▶  RESUME OPERATION");
                stop.setBackground(new Color(30, 120, 60));
                log("⛔ EMERGENCY STOP engaged — elevator halted!");
                log("   All requests cleared. Press RESUME to restore.");
                for (int i = 1; i <= TOTAL; i++)
                    if (floorButtons[i] != null) floorButtons[i].setEnabled(false);
            } else {
                model.resetEmergency();
                stop.setText("⛔  EMERGENCY STOP");
                stop.setBackground(new Color(160, 35, 35));
                log("▶  Operation RESUMED — elevator ready.");
                for (int i = 1; i <= TOTAL; i++)
                    if (floorButtons[i] != null) floorButtons[i].setEnabled(true);
                refreshButtons();
            }
        });

        p.add(statusLabel);
        p.add(doorLabel);
        p.add(stop);
        return p;
    }

    // ── Public refresh (called by Swing Timer) ─────────────────

    void refresh() {
        refreshButtons();
        refreshStatus();
    }

    private void refreshButtons() {
        Set<Integer> pending = model.getPendingFloors();
        for (int f = 1; f <= TOTAL; f++) {
            if (floorButtons[f] == null) continue;
            styleButton(floorButtons[f], pending.contains(f));
        }
    }

    private void refreshStatus() {
        int fl = (int) Math.round(model.getCurrentFloor());
        ElevatorModel.State st = model.getState();

        if (model.isEmergencyStopped()) {
            statusLabel.setText("⛔ EMERGENCY STOP — Floor " + fl);
            statusLabel.setForeground(new Color(255, 80, 80));
        } else {
            statusLabel.setText("Floor: " + fl + "  [" + st.toString().replace('_', ' ') + "]");
            statusLabel.setForeground(BTN_PENDING);
        }

        double d = model.getDoorOpen();
        if      (d <= 0.01) doorLabel.setText("Doors: CLOSED");
        else if (d >= 0.99) doorLabel.setText("Doors: OPEN");
        else if (st == ElevatorModel.State.DOOR_OPEN)  doorLabel.setText("Doors: OPENING…");
        else                doorLabel.setText("Doors: CLOSING…");
    }

    void log(String msg) {
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.append("[" + ts + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // ── Style helpers ──────────────────────────────────────────

    private void styleButton(JButton b, boolean lit) {
        b.setBackground(lit ? BTN_PENDING : BTN_IDLE);
        b.setForeground(lit ? BTN_FG_PEND : BTN_FG_IDLE);
        b.setOpaque(true);
        b.setBorderPainted(true);
        b.setBorder(lit
            ? BorderFactory.createLineBorder(BTN_PENDING.brighter(), 2)
            : BorderFactory.createLineBorder(PANEL_BORDER, 1));
    }

    private Border titledBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(PANEL_BORDER), title);
        tb.setTitleFont(new Font("Monospaced", Font.PLAIN, 10));
        tb.setTitleColor(new Color(100, 120, 160));
        return tb;
    }
}

// ─────────────────────────────────────────────────────────────
//  FRAME  — wires model + shaft + control + 60 fps timer
// ─────────────────────────────────────────────────────────────
class ElevatorFrame extends JFrame {

    private static final int FPS = 60;

    ElevatorFrame() {
        super("Elevator Simulation  —  LIFT CONTROL SYSTEM");

        ElevatorModel  model        = new ElevatorModel();
        ShaftPanel     shaftPanel   = new ShaftPanel(model);
        ControlPanel   controlPanel = new ControlPanel(model);

        // ── Layout ──
        JPanel root = new JPanel(new BorderLayout(10, 0));
        root.setBackground(new Color(14, 16, 26));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(shaftPanel,   BorderLayout.CENTER);
        root.add(controlPanel, BorderLayout.EAST);
        setContentPane(root);

        // ── Model → log listener ──
        model.addListener(new ElevatorModel.ElevatorListener() {
            public void onStateChanged(ElevatorModel.State s, int floor) {
                String msg = switch (s) {
                    case MOVING_UP   -> "↑  Moving UP from floor "   + floor;
                    case MOVING_DOWN -> "↓  Moving DOWN from floor " + floor;
                    case DOOR_OPEN   -> "↔  Doors OPENING at floor " + floor;
                    case DOOR_CLOSE  -> "→← Doors CLOSING at floor " + floor;
                    case IDLE        -> "⏸  IDLE at floor "          + floor;
                };
                SwingUtilities.invokeLater(() -> controlPanel.log(msg));
            }
            public void onTick() {}
        });

        // ── 60 fps animation timer ──
        new javax.swing.Timer(1000 / FPS, e -> {
            model.tick();
            shaftPanel.repaint();
            controlPanel.refresh();
        }).start();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        setResizable(true);

        controlPanel.log("System initialised. Elevator at floor 1.");
        controlPanel.log("Click any floor button to call the elevator.");
    }
}
