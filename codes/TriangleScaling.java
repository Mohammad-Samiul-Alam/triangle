import javax.swing.*;
import java.awt.*;

// x′=x * sx​,  y′=y * sy​

public class TriangleScaling extends JPanel {

    int originX = 320;
    int originY = 240;

    int[] x = {0, -50, 50};
    int[] y = {50, -50, -50};

    // Scaling factors
    double sx = 2;
    double sy = 1.5;

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

        // Scaled Triangle
        g.setColor(Color.RED);

        int[] xs = new int[3];
        int[] ys = new int[3];

        for (int i = 0; i < 3; i++) {

            xs[i] = (int)(x[i] * sx);

            ys[i] = (int)(y[i] * sy);
        }

        drawTriangle(g, xs, ys);
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame("Triangle Scaling");

        TriangleScaling panel = new TriangleScaling();

        panel.setBackground(Color.BLACK);

        frame.add(panel);

        frame.setSize(640, 480);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setVisible(true);
    }
}