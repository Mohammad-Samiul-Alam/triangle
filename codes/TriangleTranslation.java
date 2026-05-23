import javax.swing.*;
import java.awt.*;

public class TriangleTranslation extends JPanel {

    // custom origin point (0,0)=(320,240)
    int originX = 320;
    int originY = 240;

    // Original triangle points. Triangle er 3ta vertex define kora hoyeche. Ex: A(0,50), B(-50, -50), C(50, -50)
    int[] x = {0, -50, 50};
    int[] y = {50, -50, -50};

    // Translation values. Formula:  
    // x' = x + tx;  
    // y' = y + ty
    int tx = 100; // (right, left) side
    int ty = 100;  // (top, down) side

    // Convert logical X-coordinate to screen coordinates (screenX = centerX + logicalX)
    int screenX (int x) {
        // origin point er sathe x value add kora hoyeche
        return originX + x;
    }
      // Convert logical Y-coordinate to screen coordinate (screenY = centerY - logicalY)
    int screenY(int y) {
        // Java te Y-axis ulta hoy tai minus kora hoyeche
        return originY - y; 
    }

    // Triangle draw korar jonno method
    void drawTriangle(Graphics g, int[] x, int[] y) {
        // 3 ta line draw korar jonno loop use kora hoyeche
        for (int i = 0; i < 3; i++) {
            // Next point calculate korar jonno
            int j = (i + 1) % 3;
            // dui point er moddhe line draw kora hocche
            g.drawLine(screenX(x[i]), screenY(y[i]), screenX(x[j]), screenY(y[j]));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        // JPanel clear kore abar draw korar jonno super method call
        super.paintComponent(g);

        // Draw axes
        g.setColor(Color.WHITE);
        // Horizontal line draw kore X-axis banano hoyeche
        g.drawLine(0, originY, 640, originY); // X-axis
        // Horizontal line draw kore X-axis banano hoyeche
        g.drawLine(originX, 0, originX, 480); // Y-axis

        // Original triangle (Green)
        g.setColor(Color.GREEN);
        // Original triangle draw kora hocche
        drawTriangle(g, x, y);

        // Translated triangle (Red)
        g.setColor(Color.RED);
        // New translated x-coordinate store korar jonno array
        int[] xt = new int[3];
        // New translated y-coordinate store korar jonno array
        int[] yt = new int[3];

        // Translation apply korar jonno loop
        for (int i = 0; i < 3; i++) {
            // x-coordinate er sathe tx add kora hocche
            xt[i] = x[i] + tx;
            // y-coordinate er sathe ty add kora hocche
            yt[i] = y[i] + ty;
        }

        // Translated triangle draw kora hocche
        drawTriangle(g, xt, yt);
    }

    public static void main(String[] args) {
        // ekti Window create korlam and ei window use korar jonno obj create korlam. title "Triangle Translation" dilam.
        JFrame frame = new JFrame("Triangle Translation");

        // obj create korlam
        TriangleTranslation panel = new TriangleTranslation();

        // Obj er background color set korlam
        panel.setBackground(Color.GRAY);

        // obj, frame er majhe add na korle output kichui show korbe na.
        frame.add(panel);
        // frame er Window size 
        frame.setSize(640, 480);
        // Close button caple program jeno full off hoy.
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Window visible kora hoyeche noyto window dekhane na
        frame.setVisible(true);
    }
}