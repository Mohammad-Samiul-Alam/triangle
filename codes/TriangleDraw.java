import javax.swing.*;
import java.awt.*;

public class TriangleDraw extends JPanel {

    int originX = 320;
    int originY = 240;

    int[] x = {0, -50, 50};
    int[] y = {50, -50, -50};

    int screenX(int x) {
        return originX + x;
    }
    // upor dikhe kombe nicher dikhe barbe
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

        // X-axis
        g.drawLine(0, originY, 640, originY);

        // Y-axis
        g.drawLine(originX, 0, originX, 480);

        // Original Triangle
        g.setColor(Color.GREEN);
        drawTriangle(g, x, y);
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame("Triangle");
        TriangleDraw panel = new TriangleDraw();

        panel.setBackground(Color.BLACK);
        frame.add(panel);
        frame.setSize(640, 480);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
