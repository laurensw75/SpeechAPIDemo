package SpeechAPIDemo;

import javax.swing.*;
import java.awt.*;

/**
 * Created by laurensw on 22-12-16.
 */
public class VUMeter extends JComponent {
    private float level;

    protected void paintComponent(Graphics g) {
        g.setColor(Color.green);
        if (level>0.8) {
            g.fillRect(0, (int) (0.25 * getHeight()), (int) (getWidth() * 0.8), (int) (0.5 * getHeight()));
            g.setColor(Color.red);
            g.fillRect((int) (getWidth() * 0.8), (int) (0.25 * getHeight()), (int) (getWidth() * (level-0.8)), (int) (0.5 * getHeight()));
        } else {
            g.fillRect(0, (int) (0.25 * getHeight()), (int) (getWidth() * level), (int) (0.5 * getHeight()));
        }
    }

    public void setlevel(float newlevel) {
        level=newlevel;
        repaint();
    }
}
