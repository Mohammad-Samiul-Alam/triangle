import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;

public class ElevatorSimulation extends JFrame {

    // ── Layout constants ─────────────────────────────────────────────────────
    private static final int NUM_FLOORS      = 5;
    private static final int ELEVATOR_WIDTH  = 90;
    private static final int ELEVATOR_HEIGHT = 110;
    private static final int DOOR_SPEED      = 6;
    private static final int ELEVATOR_SPEED  = 4;
    private static final int PERSON_WIDTH    = 22;
    private static final int PERSON_HEIGHT   = 38;

    // ── Simulation phase ─────────────────────────────────────────────────────
    // IDLE       → waiting for START
    // DOOR_OPEN  → doors open, waiting for person to enter
    // READY      → person inside, accepting floor requests, elevator moving
    private enum Phase { IDLE, DOOR_OPEN, READY }
    private Phase phase = Phase.IDLE;

    // ── Elevator state ───────────────────────────────────────────────────────
    private int  currentFloor  = 0;
    private int  targetFloor   = 0;
    private int  elevatorY;
    private boolean doorsOpen        = false;
    private int  doorOpenAmount      = 0;
    private ArrayList<Integer> floorQueue = new ArrayList<>();
    private boolean isMoving         = false;
    private boolean emergencyStop    = false;
    private String  statusMessage    = "Press START, then enter the elevator";

    // ── Person animation ─────────────────────────────────────────────────────
    private boolean personEntering   = false;
    private boolean personExiting    = false;
    private int     personX          = 0;
    private int     personTargetX    = 0;
    private boolean personInElevator = false;
    private int     peopleCount      = 0;

    // ── UI ───────────────────────────────────────────────────────────────────
    private ElevatorPanel  elevatorPanel;
    private Timer          animationTimer;
    private JPanel         queueDisplayPanel;
    private JLabel         currentFloorLabel;
    private JLabel         statusLabel;
    private JLabel         peopleLabel;
    private JLabel         phaseLabel;
    private JButton        emergencyBtn;
    private JButton        startStopBtn;
    private JButton        enterBtn;
    private JButton        exitBtn;
    private JButton        closeDoorBtn;
    private JButton[]      floorButtons;

    // ── Dynamic layout helpers ───────────────────────────────────────────────
    private int buildingWidth;
    private int buildingHeight;
    private int floorHeight;

    // ════════════════════════════════════════════════════════════════════════
    public ElevatorSimulation() {
        setTitle("Elevator Simulation System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setUndecorated(false);
        setLayout(new BorderLayout());

        elevatorPanel = new ElevatorPanel();
        add(elevatorPanel, BorderLayout.CENTER);

        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.EAST);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                buildingWidth  = elevatorPanel.getWidth();
                buildingHeight = elevatorPanel.getHeight();
                floorHeight    = buildingHeight / NUM_FLOORS;
                elevatorY      = calculateFloorY(currentFloor);
                elevatorPanel.repaint();
            }
        });

        animationTimer = new Timer(16, e -> {
            if (phase == Phase.IDLE) {
                elevatorPanel.repaint();
                refreshStatusLabels();
                return;
            }
            updateElevator();
            updatePerson();
            elevatorPanel.repaint();
            refreshStatusLabels();
        });
        animationTimer.start();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Control panel
    // ════════════════════════════════════════════════════════════════════════
    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(240, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 12, 14, 12));
        panel.setBackground(new Color(28, 32, 42));

        // ── Title ────────────────────────────────────────────────────────
        JLabel title = new JLabel("ELEVATOR CTRL");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("Monospaced", Font.BOLD, 17));
        title.setForeground(new Color(0, 220, 180));
        panel.add(title);
        panel.add(Box.createVerticalStrut(4));

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0, 220, 180));
        sep.setMaximumSize(new Dimension(220, 2));
        panel.add(sep);
        panel.add(Box.createVerticalStrut(10));

        // ── Phase indicator ───────────────────────────────────────────────
        phaseLabel = new JLabel("● STEP 1: Press START");
        phaseLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        phaseLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        phaseLabel.setForeground(new Color(255, 200, 60));
        panel.add(phaseLabel);
        panel.add(Box.createVerticalStrut(8));

        // ── START button ──────────────────────────────────────────────────
        startStopBtn = new JButton("▶  START ELEVATOR");
        startStopBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        startStopBtn.setMaximumSize(new Dimension(216, 46));
        startStopBtn.setFont(new Font("Monospaced", Font.BOLD, 13));
        startStopBtn.setBackground(new Color(30, 160, 80));
        startStopBtn.setForeground(Color.WHITE);
        startStopBtn.setFocusPainted(false);
        startStopBtn.setBorder(BorderFactory.createLineBorder(new Color(80, 220, 140), 2));
        startStopBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startStopBtn.addActionListener(e -> handleStart());
        panel.add(startStopBtn);

        panel.add(Box.createVerticalStrut(10));
        panel.add(makeDivider());
        panel.add(Box.createVerticalStrut(10));

        // ── Person controls ───────────────────────────────────────────────
        JLabel personHeader = makeSmallHeader("STEP 2 – PERSON CONTROL");
        panel.add(personHeader);
        panel.add(Box.createVerticalStrut(6));

        enterBtn = makeActionButton("▶  Enter Elevator", new Color(34, 180, 120));
        enterBtn.addActionListener(e -> enterElevator());
        panel.add(enterBtn);
        panel.add(Box.createVerticalStrut(6));

        exitBtn = makeActionButton("◀  Exit Elevator", new Color(200, 70, 70));
        exitBtn.addActionListener(e -> exitElevator());
        panel.add(exitBtn);
        panel.add(Box.createVerticalStrut(6));

        closeDoorBtn = makeActionButton("🚪  Close Door & Go", new Color(60, 80, 180));
        closeDoorBtn.addActionListener(e -> closeDoorAndGo());
        panel.add(closeDoorBtn);

        panel.add(Box.createVerticalStrut(10));
        panel.add(makeDivider());
        panel.add(Box.createVerticalStrut(10));

        // ── Floor buttons ─────────────────────────────────────────────────
        JLabel floorLabel = makeSmallHeader("STEP 3 – FLOOR REQUESTS");
        panel.add(floorLabel);
        panel.add(Box.createVerticalStrut(6));

        floorButtons = new JButton[NUM_FLOORS];
        for (int i = NUM_FLOORS - 1; i >= 0; i--) {
            final int floor = i;
            JButton btn = makeFloorButton("Floor " + i);
            btn.addActionListener(e -> requestFloor(floor));
            floorButtons[i] = btn;
            panel.add(btn);
            panel.add(Box.createVerticalStrut(6));
        }

        panel.add(Box.createVerticalStrut(10));
        panel.add(makeDivider());
        panel.add(Box.createVerticalStrut(10));

        // ── Emergency Stop ────────────────────────────────────────────────
        emergencyBtn = new JButton("⛔  EMERGENCY STOP");
        emergencyBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        emergencyBtn.setMaximumSize(new Dimension(216, 46));
        emergencyBtn.setFont(new Font("Monospaced", Font.BOLD, 13));
        emergencyBtn.setBackground(new Color(220, 30, 30));
        emergencyBtn.setForeground(Color.WHITE);
        emergencyBtn.setFocusPainted(false);
        emergencyBtn.setBorder(BorderFactory.createLineBorder(new Color(255, 80, 80), 2));
        emergencyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        emergencyBtn.addActionListener(e -> toggleEmergencyStop());
        panel.add(emergencyBtn);

        panel.add(Box.createVerticalStrut(10));
        panel.add(makeDivider());
        panel.add(Box.createVerticalStrut(10));

        // ── Status ────────────────────────────────────────────────────────
        JLabel statusHeader = makeSmallHeader("STATUS");
        panel.add(statusHeader);
        panel.add(Box.createVerticalStrut(6));

        currentFloorLabel = makeStatusLabel("Floor: 0");
        statusLabel       = makeStatusLabel("Status: Idle");
        peopleLabel       = makeStatusLabel("Occupants: 0");

        panel.add(currentFloorLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(peopleLabel);

        panel.add(Box.createVerticalStrut(10));
        panel.add(makeDivider());
        panel.add(Box.createVerticalStrut(10));

        // ── Queue ─────────────────────────────────────────────────────────
        JLabel queueHeader = makeSmallHeader("FLOOR QUEUE");
        panel.add(queueHeader);
        panel.add(Box.createVerticalStrut(4));

        queueDisplayPanel = new JPanel();
        queueDisplayPanel.setLayout(new BoxLayout(queueDisplayPanel, BoxLayout.Y_AXIS));
        queueDisplayPanel.setBackground(new Color(36, 40, 54));
        queueDisplayPanel.setMaximumSize(new Dimension(216, 300));
        queueDisplayPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(queueDisplayPanel);

        updateButtonStates();
        return panel;
    }

    // ── Enable/disable buttons based on current phase ────────────────────────
    private void updateButtonStates() {
        boolean idle  = (phase == Phase.IDLE);
        boolean door  = (phase == Phase.DOOR_OPEN);
        boolean ready = (phase == Phase.READY);

        // START btn
        startStopBtn.setEnabled(!emergencyStop);
        if (idle) {
            startStopBtn.setText("▶  START ELEVATOR");
            startStopBtn.setBackground(new Color(30, 160, 80));
            startStopBtn.setBorder(BorderFactory.createLineBorder(new Color(80, 220, 140), 2));
        } else {
            startStopBtn.setText("⏹  STOP / RESET");
            startStopBtn.setBackground(new Color(150, 50, 20));
            startStopBtn.setBorder(BorderFactory.createLineBorder(new Color(255, 100, 60), 2));
        }

        // Enter: DOOR_OPEN phase (first entry) OR READY phase with doors open and person outside (re-entry)
        boolean canEnterNow = !personInElevator && !personEntering && !personExiting && !emergencyStop
                && doorsOpen && doorOpenAmount >= ELEVATOR_WIDTH / 2
                && (phase == Phase.DOOR_OPEN || phase == Phase.READY);
        enterBtn.setEnabled(canEnterNow);

        // Exit: READY phase, person inside, doors fully open, not moving
        exitBtn.setEnabled(ready && personInElevator && doorsOpen && doorOpenAmount >= ELEVATOR_WIDTH / 2 && !isMoving && !personEntering && !personExiting && !emergencyStop);

        // Close Door & Go: person inside, doors open, queue non-empty, not moving
        closeDoorBtn.setEnabled(ready && personInElevator && doorsOpen
                && doorOpenAmount >= ELEVATOR_WIDTH / 2
                && !floorQueue.isEmpty() && !isMoving
                && !personEntering && !personExiting && !emergencyStop);

        // Floor buttons: IDLE pre-queue, or READY with person inside and not moving
        for (int i = 0; i < floorButtons.length; i++) {
            boolean canQueue = (idle || (ready && personInElevator && !isMoving)) && !emergencyStop;
            floorButtons[i].setEnabled(canQueue);
            // Highlight buttons that are already queued
            if (floorQueue.contains(i)) {
                floorButtons[i].setBackground(new Color(180, 120, 0));
                floorButtons[i].setBorder(BorderFactory.createLineBorder(new Color(255, 200, 60), 2));
            } else {
                floorButtons[i].setBackground(new Color(50, 80, 140));
                floorButtons[i].setBorder(BorderFactory.createLineBorder(new Color(80, 120, 200), 1));
            }
        }

        // Phase label
        if (emergencyStop) {
            phaseLabel.setText("⛔ EMERGENCY STOP");
            phaseLabel.setForeground(new Color(255, 80, 80));
        } else if (idle) {
            if (floorQueue.isEmpty()) {
                phaseLabel.setText("● STEP 1: Press START");
            } else {
                phaseLabel.setText("● " + floorQueue.size() + " floor(s) queued – START!");
            }
            phaseLabel.setForeground(new Color(255, 200, 60));
        } else if (door) {
            phaseLabel.setText("● STEP 2: Enter the elevator");
            phaseLabel.setForeground(new Color(80, 200, 255));
        } else {
            if (!personInElevator && !personEntering && !personExiting) {
                phaseLabel.setText("● Enter elevator or reset");
                phaseLabel.setForeground(new Color(180, 220, 255));
            } else if (floorQueue.isEmpty() && !isMoving) {
                phaseLabel.setText("● Select floors to request");
                phaseLabel.setForeground(new Color(80, 255, 160));
            } else if (!isMoving) {
                phaseLabel.setText("● Press Close Door & Go!");
                phaseLabel.setForeground(new Color(120, 160, 255));
            } else {
                phaseLabel.setText("● Travelling to floors…");
                phaseLabel.setForeground(new Color(80, 200, 255));
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Helper widget builders
    // ════════════════════════════════════════════════════════════════════════
    private JLabel makeSmallHeader(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 11));
        lbl.setForeground(new Color(160, 170, 200));
        return lbl;
    }

    private JLabel makeStatusLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
        lbl.setForeground(new Color(220, 230, 255));
        return lbl;
    }

    private JButton makeFloorButton(String text) {
        JButton btn = new JButton(text);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(216, 40));
        btn.setFont(new Font("Monospaced", Font.BOLD, 13));
        btn.setBackground(new Color(50, 80, 140));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(new Color(80, 120, 200), 1));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton makeActionButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(216, 38));
        btn.setFont(new Font("Monospaced", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JSeparator makeDivider() {
        JSeparator s = new JSeparator();
        s.setForeground(new Color(60, 68, 90));
        s.setMaximumSize(new Dimension(216, 1));
        return s;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Status refresh
    // ════════════════════════════════════════════════════════════════════════
    private void refreshStatusLabels() {
        currentFloorLabel.setText("Floor: " + currentFloor);
        statusLabel.setText("Status: " + statusMessage);
        peopleLabel.setText("Occupants: " + peopleCount);
        updateQueueDisplay();
        updateButtonStates();
    }

    private void updateQueueDisplay() {
        queueDisplayPanel.removeAll();
        queueDisplayPanel.add(Box.createVerticalStrut(4));

        if (floorQueue.isEmpty()) {
            JLabel empty = new JLabel("  No pending requests");
            empty.setFont(new Font("Monospaced", Font.ITALIC, 11));
            empty.setForeground(new Color(120, 130, 160));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            queueDisplayPanel.add(empty);
        } else {
            for (int i = 0; i < floorQueue.size(); i++) {
                JLabel item = new JLabel("  " + (i + 1) + ". Floor " + floorQueue.get(i));
                item.setFont(new Font("Monospaced", Font.PLAIN, 12));
                item.setForeground(new Color(0, 220, 180));
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                queueDisplayPanel.add(item);
            }
        }

        queueDisplayPanel.add(Box.createVerticalStrut(4));
        queueDisplayPanel.revalidate();
        queueDisplayPanel.repaint();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  START / RESET
    // ════════════════════════════════════════════════════════════════════════
    private void handleStart() {
        if (emergencyStop) {
            JOptionPane.showMessageDialog(this,
                "Release Emergency Stop first!", "Cannot Start", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (phase == Phase.IDLE) {
            // STEP 1 → open doors so person can board
            phase = Phase.DOOR_OPEN;
            if (!floorQueue.isEmpty()) {
                statusMessage = "Doors opening – " + floorQueue.size() + " floor(s) pre-queued. Enter the elevator!";
            } else {
                statusMessage = "Doors opening – please enter!";
            }
            openDoors();
        } else {
            // STOP / RESET from any running phase
            int confirm = JOptionPane.showConfirmDialog(this,
                "Reset the simulation?\nAll queued floors will be cleared.",
                "Confirm Reset", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            phase          = Phase.IDLE;
            isMoving       = false;
            personEntering = false;
            personExiting  = false;
            personInElevator = false;
            peopleCount    = 0;
            doorsOpen      = false;
            doorOpenAmount = 0;
            floorQueue.clear();
            statusMessage  = "Press START, then enter the elevator";
            elevatorPanel.repaint();
            refreshStatusLabels();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Core logic
    // ════════════════════════════════════════════════════════════════════════
    private void requestFloor(int floor) {
        // Allow pre-queuing in IDLE phase, or normal operation in READY phase
        if (emergencyStop) return;
        if (phase == Phase.IDLE) {
            // Pre-queue mode: toggle floor in/out of queue before lift starts
            if (floorQueue.contains(floor)) {
                floorQueue.remove(Integer.valueOf(floor));
                statusMessage = "Floor " + floor + " removed from pre-queue";
            } else {
                floorQueue.add(floor);
                statusMessage = "Floor " + floor + " pre-queued – press START to begin";
            }
            return;
        }
        if (phase != Phase.READY || !personInElevator) return;

        // Already heading there
        if (floor == targetFloor && isMoving) {
            statusMessage = "Already heading to Floor " + floor;
            return;
        }
        // Already there with doors open
        if (floor == currentFloor && !isMoving && doorsOpen) {
            statusMessage = "Already at Floor " + floor;
            return;
        }

        if (!floorQueue.contains(floor)) {
            floorQueue.add(floor);
            statusMessage = "Floor " + floor + " queued – select more or press Close Door & Go";
        }
    }

    private void enterElevator() {
        // Allow entry in DOOR_OPEN phase (first time) OR READY phase (re-entry after exit)
        boolean canEnter = (phase == Phase.DOOR_OPEN || phase == Phase.READY) && !emergencyStop;
        if (!canEnter) return;
        if (!doorsOpen || doorOpenAmount < ELEVATOR_WIDTH / 2) {
            JOptionPane.showMessageDialog(this, "Doors are not fully open yet!", "Wait", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (personInElevator || personEntering || personExiting) return;

        personEntering = true;
        int liveWidth  = elevatorPanel.getWidth();
        int elevatorX  = (liveWidth - ELEVATOR_WIDTH) / 2;
        // Person walks in from the right side (where they just exited to)
        personX       = elevatorX + ELEVATOR_WIDTH + 70;
        personTargetX = elevatorX + ELEVATOR_WIDTH / 2 - PERSON_WIDTH / 2;
    }

    private void exitElevator() {
        if (phase != Phase.READY || !personInElevator || emergencyStop) return;
        if (!doorsOpen || doorOpenAmount < ELEVATOR_WIDTH / 2) {
            JOptionPane.showMessageDialog(this, "Doors are closed! Wait for doors to open.", "Cannot Exit", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (isMoving || personEntering || personExiting) return;

        // Exit at the current floor immediately — animate person walking out
        personExiting  = true;
        int liveWidth  = elevatorPanel.getWidth();
        int elevatorX  = (liveWidth - ELEVATOR_WIDTH) / 2;
        personX        = elevatorX + ELEVATOR_WIDTH / 2 - PERSON_WIDTH / 2;
        personTargetX  = elevatorX + ELEVATOR_WIDTH + 70;
        statusMessage  = "Person exiting at Floor " + currentFloor + "…";
    }

    private void closeDoorAndGo() {
        if (phase != Phase.READY || !personInElevator || emergencyStop) return;
        if (!doorsOpen || floorQueue.isEmpty() || isMoving) return;

        statusMessage = "Closing door – heading to " + floorQueue.size() + " floor(s)…";
        closeDoors();
    }

    private void toggleEmergencyStop() {
        emergencyStop = !emergencyStop;

        if (emergencyStop) {
            isMoving       = false;
            personEntering = false;
            personExiting  = false;
            doorsOpen      = false;
            floorQueue.clear();
            doorOpenAmount = 0;
            statusMessage  = "⛔ EMERGENCY STOP ACTIVE";

            emergencyBtn.setText("✔  RELEASE EMERGENCY");
            emergencyBtn.setBackground(new Color(140, 20, 20));
            emergencyBtn.setBorder(BorderFactory.createLineBorder(new Color(255, 200, 0), 3));

            JOptionPane.showMessageDialog(this,
                "EMERGENCY STOP ACTIVATED!\n\nElevator halted. All requests cleared.\nPress 'Release Emergency' to resume.",
                "EMERGENCY STOP", JOptionPane.ERROR_MESSAGE);
        } else {
            emergencyBtn.setText("⛔  EMERGENCY STOP");
            emergencyBtn.setBackground(new Color(220, 30, 30));
            emergencyBtn.setBorder(BorderFactory.createLineBorder(new Color(255, 80, 80), 2));

            if (phase != Phase.IDLE) {
                statusMessage = "Emergency released – doors opening";
                openDoors();
            } else {
                statusMessage = "Emergency released – press START";
            }
        }
        updateButtonStates();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Door helpers
    // ════════════════════════════════════════════════════════════════════════
    private void openDoors() {
        if (!emergencyStop) {
            doorsOpen     = true;
            statusMessage = "Opening Doors…";
        }
    }

    private void closeDoors() {
        doorsOpen     = false;
        statusMessage = "Closing Doors…";
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Animation update (called every 16 ms)
    // ════════════════════════════════════════════════════════════════════════
    private void updateElevator() {
        if (buildingHeight == 0) return;

        // Emergency: force doors shut, freeze everything
        if (emergencyStop) {
            if (doorOpenAmount > 0) doorOpenAmount = Math.max(0, doorOpenAmount - DOOR_SPEED);
            return;
        }

        // Door opening animation
        if (doorsOpen && doorOpenAmount < ELEVATOR_WIDTH / 2) {
            doorOpenAmount += DOOR_SPEED;
            if (doorOpenAmount >= ELEVATOR_WIDTH / 2) {
                doorOpenAmount = ELEVATOR_WIDTH / 2;
                statusMessage  = "Doors Open – Floor " + currentFloor;
            }
            return;
        }

        // Door closing animation
        if (!doorsOpen && doorOpenAmount > 0) {
            doorOpenAmount -= DOOR_SPEED;
            if (doorOpenAmount <= 0) {
                doorOpenAmount = 0;
                if (!floorQueue.isEmpty()) {
                    targetFloor   = floorQueue.remove(0);
                    isMoving      = true;
                    statusMessage = "Heading to Floor " + targetFloor;
                } else {
                    statusMessage = "Doors Closed – Idle";
                }
            }
            return;
        }

        // Elevator movement
        if (isMoving) {
            int targetY = calculateFloorY(targetFloor);
            if (Math.abs(elevatorY - targetY) <= ELEVATOR_SPEED) {
                elevatorY = targetY;
                arrivedAtFloor();
            } else if (elevatorY < targetY) {
                elevatorY += ELEVATOR_SPEED;
                statusMessage = "Moving Down ▼  to F" + targetFloor;
            } else {
                elevatorY -= ELEVATOR_SPEED;
                statusMessage = "Moving Up ▲  to F" + targetFloor;
            }
        }
    }

    private void arrivedAtFloor() {
        currentFloor  = targetFloor;
        isMoving      = false;
        statusMessage = "Arrived at Floor " + currentFloor;

        // Open doors after short pause
        Timer openT = new Timer(350, e -> {
            openDoors();
            ((Timer) e.getSource()).stop();
        });
        openT.setRepeats(false);
        openT.start();

        // If more floors queued, auto-close after 2 s so lift continues
        if (!floorQueue.isEmpty()) {
            Timer closeT = new Timer(2000, e -> {
                if (!emergencyStop && !personEntering && !personExiting && !floorQueue.isEmpty()) {
                    closeDoors();
                }
                ((Timer) e.getSource()).stop();
            });
            closeT.setRepeats(false);
            closeT.start();
        }
    }

    // ── Person movement ──────────────────────────────────────────────────────
    private void updatePerson() {
        if (personEntering) {
            // Move toward target: target may be to the left (re-entry) or right (first entry)
            if (personX < personTargetX) personX += 3;
            else                         personX -= 3;

            if (Math.abs(personX - personTargetX) <= 3) {
                personX          = personTargetX;
                personEntering   = false;
                personInElevator = true;
                peopleCount++;
                // Ensure we are in READY phase
                phase = Phase.READY;
                if (!floorQueue.isEmpty()) {
                    statusMessage = "Person entered – heading to " + floorQueue.size() + " queued floor(s)!";
                    closeDoors();
                } else {
                    statusMessage = "Person entered – now request floors!";
                }
            }
        } else if (personExiting) {
            personX += 3;
            if (personX >= personTargetX) {
                personX          = 0;
                personExiting    = false;
                personInElevator = false;
                peopleCount      = Math.max(0, peopleCount - 1);
                // Stay in READY phase with doors open — person can walk back in
                statusMessage = "Person exited at Floor " + currentFloor + " – re-enter or request floors";
            }
        }
    }

    // ── Floor Y coordinate ───────────────────────────────────────────────────
    private int calculateFloorY(int floor) {
        if (buildingHeight == 0) buildingHeight = elevatorPanel.getHeight();
        int fh = buildingHeight / NUM_FLOORS;
        return buildingHeight - ((floor + 1) * fh) + (fh - ELEVATOR_HEIGHT) / 2;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Drawing panel
    // ════════════════════════════════════════════════════════════════════════
    class ElevatorPanel extends JPanel {
        ElevatorPanel() { setBackground(new Color(18, 22, 32)); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            buildingWidth  = getWidth();
            buildingHeight = getHeight();
            floorHeight    = buildingHeight / NUM_FLOORS;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,     RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            drawBackground(g2);
            drawBuilding(g2);
            drawShaft(g2);
            drawElevator(g2);
            drawFloorIndicators(g2);
            drawPerson(g2);
            drawEmergencyOverlay(g2);

            if (phase == Phase.IDLE) drawIdleOverlay(g2);
        }

        // ── Idle overlay ─────────────────────────────────────────────────────
        private void drawIdleOverlay(Graphics2D g) {
            // Only show full dim when nothing is queued; once floors are selected, show light hint
            if (floorQueue.isEmpty()) {
                g.setColor(new Color(0, 0, 0, 120));
                g.fillRect(0, 0, buildingWidth, buildingHeight);
            }

            g.setFont(new Font("Monospaced", Font.BOLD, 22));
            String msg = floorQueue.isEmpty()
                ? "Select floors below, then press ▶ START"
                : floorQueue.size() + " floor(s) queued  →  press ▶ START";
            FontMetrics fm = g.getFontMetrics();
            int tx = (buildingWidth - fm.stringWidth(msg)) / 2;
            int ty = buildingHeight / 2;

            g.setColor(new Color(0, 0, 0, 180));
            g.fillRoundRect(tx - 14, ty - 26, fm.stringWidth(msg) + 28, 40, 10, 10);
            g.setColor(floorQueue.isEmpty() ? new Color(0, 230, 180) : new Color(255, 210, 60));
            g.drawString(msg, tx, ty);
        }

        // ── Background ────────────────────────────────────────────────────
        private void drawBackground(Graphics2D g) {
            GradientPaint bg = new GradientPaint(
                0, 0, new Color(18, 22, 32),
                buildingWidth, buildingHeight, new Color(28, 35, 55));
            g.setPaint(bg);
            g.fillRect(0, 0, buildingWidth, buildingHeight);
        }

        // ── Building walls and floors ─────────────────────────────────────
        private void drawBuilding(Graphics2D g) {
            int left  = buildingWidth / 8;
            int right = buildingWidth - buildingWidth / 8;
            int bw    = right - left;

            g.setColor(new Color(32, 38, 55));
            g.fillRect(left, 0, bw, buildingHeight);

            for (int i = 1; i < NUM_FLOORS; i++) {
                int y = i * floorHeight;
                g.setColor(new Color(60, 70, 100));
                g.setStroke(new BasicStroke(1.5f));
                g.drawLine(left, y, right, y);

                // Label the section BELOW this line: line i divides floor (NUM_FLOORS-i) above
                // and floor (NUM_FLOORS-1-i) below
                g.setColor(new Color(120, 140, 180));
                g.setFont(new Font("Monospaced", Font.BOLD, 14));
                int labelFloor = NUM_FLOORS - 1 - i;  // floor number for section below line i
                g.drawString("F" + labelFloor, left + 8, y + floorHeight / 2 + 6);
            }
            // Top section (above line i=1) = floor NUM_FLOORS-1
            g.setColor(new Color(120, 140, 180));
            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            g.drawString("F" + (NUM_FLOORS - 1), left + 8, floorHeight / 2 + 6);

            g.setColor(new Color(0, 180, 150));
            g.setStroke(new BasicStroke(2.5f));
            g.drawRect(left, 0, bw, buildingHeight - 1);
        }

        // ── Shaft ─────────────────────────────────────────────────────────
        private void drawShaft(Graphics2D g) {
            int shaftX = (buildingWidth - ELEVATOR_WIDTH) / 2;
            int shaftW = ELEVATOR_WIDTH + 20;

            g.setColor(new Color(22, 26, 40));
            g.fillRect(shaftX - 10, 0, shaftW, buildingHeight);

            g.setColor(new Color(50, 60, 90));
            g.setStroke(new BasicStroke(3));
            g.drawLine(shaftX - 6, 0, shaftX - 6, buildingHeight);
            g.drawLine(shaftX + ELEVATOR_WIDTH + 6, 0, shaftX + ELEVATOR_WIDTH + 6, buildingHeight);

            for (int i = 0; i < NUM_FLOORS; i++) {
                int floorY = buildingHeight - (i + 1) * floorHeight + floorHeight - 6;
                g.setColor(new Color(80, 55, 30));
                g.fillRoundRect(shaftX - 90, floorY, 80, 6, 3, 3);
                g.fillRoundRect(shaftX + ELEVATOR_WIDTH + 10, floorY, 80, 6, 3, 3);
            }
        }

        // ── Elevator cabin ────────────────────────────────────────────────
        private void drawElevator(Graphics2D g) {
            int ex = (buildingWidth - ELEVATOR_WIDTH) / 2;

            g.setColor(new Color(80, 100, 130));
            g.setStroke(new BasicStroke(4));
            g.drawLine(ex + ELEVATOR_WIDTH / 2, 0, ex + ELEVATOR_WIDTH / 2, elevatorY);

            g.setColor(new Color(0, 0, 0, 80));
            g.fillRoundRect(ex + 4, elevatorY + 4, ELEVATOR_WIDTH, ELEVATOR_HEIGHT, 10, 10);

            Color cabinColor = emergencyStop ? new Color(140, 30, 30) : new Color(50, 100, 170);
            GradientPaint cabinGrad = new GradientPaint(
                ex, elevatorY, cabinColor.brighter(),
                ex + ELEVATOR_WIDTH, elevatorY + ELEVATOR_HEIGHT, cabinColor.darker());
            g.setPaint(cabinGrad);
            g.fillRoundRect(ex, elevatorY, ELEVATOR_WIDTH, ELEVATOR_HEIGHT, 10, 10);

            g.setColor(emergencyStop ? new Color(255, 80, 80) : new Color(0, 200, 170));
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(ex, elevatorY, ELEVATOR_WIDTH, ELEVATOR_HEIGHT, 10, 10);

            int halfW   = ELEVATOR_WIDTH / 2;
            int doorW   = halfW - doorOpenAmount;
            int doorTop = elevatorY + 6;
            int doorH   = ELEVATOR_HEIGHT - 12;

            if (doorW > 0) {
                int ldx = ex + doorOpenAmount;
                GradientPaint lg = new GradientPaint(ldx, 0, new Color(200, 210, 230), ldx + doorW, 0, new Color(160, 170, 190));
                g.setPaint(lg);
                g.fillRect(ldx, doorTop, doorW, doorH);
                g.setColor(new Color(100, 110, 140));
                g.setStroke(new BasicStroke(1f));
                g.drawRect(ldx, doorTop, doorW, doorH);
                g.setColor(new Color(80, 80, 100));
                g.fillRoundRect(ldx + doorW - 9, doorTop + doorH / 2 - 14, 5, 28, 3, 3);

                int rdx = ex + halfW;
                GradientPaint rg = new GradientPaint(rdx, 0, new Color(160, 170, 190), rdx + doorW, 0, new Color(200, 210, 230));
                g.setPaint(rg);
                g.fillRect(rdx, doorTop, doorW, doorH);
                g.setColor(new Color(100, 110, 140));
                g.setStroke(new BasicStroke(1f));
                g.drawRect(rdx, doorTop, doorW, doorH);
                g.setColor(new Color(80, 80, 100));
                g.fillRoundRect(rdx + 4, doorTop + doorH / 2 - 14, 5, 28, 3, 3);
            }

            if (doorOpenAmount > 18) {
                g.setColor(new Color(0, 230, 190));
                g.setFont(new Font("Monospaced", Font.BOLD, 28));
                String fl = String.valueOf(currentFloor);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(fl, ex + (ELEVATOR_WIDTH - fm.stringWidth(fl)) / 2, elevatorY + 38);
            }
        }

        // ── Floor indicators ──────────────────────────────────────────────
        private void drawFloorIndicators(Graphics2D g) {
            int left = buildingWidth / 8;
            int indX = left + 40;

            for (int i = 0; i < NUM_FLOORS; i++) {
                int iy = buildingHeight - ((i + 1) * floorHeight) + floorHeight / 2 - 11;

                g.setColor(new Color(30, 40, 60));
                g.fillOval(indX, iy, 22, 22);

                Color dotColor;
                if (emergencyStop && i == currentFloor)      dotColor = new Color(255, 60, 60);
                else if (i == currentFloor)                  dotColor = new Color(0, 230, 130);
                else if (i == targetFloor && isMoving)       dotColor = new Color(255, 160, 0);
                else if (floorQueue.contains(i))             dotColor = new Color(230, 180, 0);
                else                                         dotColor = new Color(45, 55, 80);

                g.setColor(dotColor);
                g.fillOval(indX + 3, iy + 3, 16, 16);
                g.setColor(new Color(80, 100, 140));
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(indX, iy, 22, 22);
            }
        }

        // ── Person ────────────────────────────────────────────────────────
        private void drawPerson(Graphics2D g) {
            if (!personInElevator && !personEntering && !personExiting) return;

            int py = elevatorY + ELEVATOR_HEIGHT - PERSON_HEIGHT - 8;
            int cx = personX + PERSON_WIDTH / 2;

            g.setColor(new Color(255, 218, 170));
            g.fillOval(cx - 7, py, 14, 14);
            g.setColor(new Color(80, 50, 20));
            g.setStroke(new BasicStroke(1f));
            g.drawOval(cx - 7, py, 14, 14);

            g.setColor(new Color(40, 100, 200));
            g.fillRoundRect(cx - 6, py + 14, 12, 16, 4, 4);

            g.setColor(new Color(30, 30, 50));
            g.fillRoundRect(cx - 7, py + 29, 5, 9, 2, 2);
            g.fillRoundRect(cx + 2, py + 29, 5, 9, 2, 2);

            g.setColor(new Color(255, 218, 170));
            g.fillRoundRect(cx - 11, py + 15, 4, 10, 2, 2);
            g.fillRoundRect(cx + 7, py + 15, 4, 10, 2, 2);
        }

        // ── Emergency overlay ─────────────────────────────────────────────
        private void drawEmergencyOverlay(Graphics2D g) {
            if (!emergencyStop) return;

            long t    = System.currentTimeMillis();
            int pulse = (int)(128 + 127 * Math.sin(t / 300.0));
            g.setColor(new Color(220, pulse / 4, 0, 120));
            g.setStroke(new BasicStroke(8));
            g.drawRect(4, 4, buildingWidth - 8, buildingHeight - 8);

            g.setFont(new Font("Monospaced", Font.BOLD, 22));
            String msg = "⛔  EMERGENCY STOP ACTIVE";
            FontMetrics fm = g.getFontMetrics();
            int tx = (buildingWidth - fm.stringWidth(msg)) / 2;
            g.setColor(new Color(0, 0, 0, 160));
            g.drawString(msg, tx + 2, 40);
            g.setColor(new Color(255, 60, 60));
            g.drawString(msg, tx, 38);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ElevatorSimulation sim = new ElevatorSimulation();
            sim.setLocationRelativeTo(null);
            sim.setVisible(true);
        });
    }
}
