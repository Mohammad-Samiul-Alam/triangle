import javax.swing.*;
import java.awt.*;

// Rotation Formula x′=xcosθ−ysinθ, y′=xsinθ+ycosθ


public class TriangleRotation extends JPanel {

    int originX = 320;
    int originY = 240;

    int[] x = {0, -50, 50};
    int[] y = {50, -50, -50};

    // Rotation angle
    double angle = Math.toRadians(45);

    int screenX(int x) {
        return originX + x;
    }

    int screenY(int y) {
        return originY - y;
    }

    void drawTriangle(Graphics g, int[] x, int[] y) {
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3;

            g.drawLine(
                    screenX(x[i]), screenY(y[i]),
                    screenX(x[j]), screenY(y[j])
            );
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(Color.WHITE);
        g.drawLine(0, originY, 640, originY);
        g.drawLine(originX, 0, originX, 480);

        // Original Triangle
        g.setColor(Color.GREEN);
        drawTriangle(g, x, y);

        // Rotated Triangle
        g.setColor(Color.RED);

        int[] xr = new int[3];
        int[] yr = new int[3];

        for (int i = 0; i < 3; i++) {

            xr[i] = (int)(x[i] * Math.cos(angle) - y[i] * Math.sin(angle));

            yr[i] = (int)(x[i] * Math.sin(angle) + y[i] * Math.cos(angle));
        }

        drawTriangle(g, xr, yr);
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame("Triangle Rotation");

        TriangleRotation panel = new TriangleRotation();

        panel.setBackground(Color.BLACK);

        frame.add(panel);

        frame.setSize(640, 480);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setVisible(true);
    }
}