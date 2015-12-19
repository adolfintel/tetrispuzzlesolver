/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dosse.tetrisPuzzleSolver.gui;

import com.dosse.tetrisPuzzleSolver.TetrisPuzzleSolver;
import com.dosse.tetrisPuzzleSolver.TetrisPuzzleSolverMT;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.GZIPOutputStream;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.Timer;

/**
 *
 * @author Federico
 */
public class Visualizer extends javax.swing.JFrame {

    private JFrame caller;

    private TetrisPuzzleSolver s;
    private TetrisPuzzleSolverMT smt;

    private Color[] color;

    private Thread solverThread;

    private boolean saving = false, canceled = false;

    private KeyEventDispatcher f5;

    /**
     * Creates new form Visualizer
     */
    public Visualizer(final Object solver, JFrame caller) {
        initComponents();
        int nPieces = 0;
        int width = 0, height = 0;
        if (solver instanceof TetrisPuzzleSolver) {
            s = (TetrisPuzzleSolver) solver;
            nPieces = s.getNumberOfPieces();
            width = s.getWidth();
            height = s.getHeight();
        }
        if (solver instanceof TetrisPuzzleSolverMT) {
            smt = (TetrisPuzzleSolverMT) solver;
            nPieces = smt.getNumberOfPieces();
            width = smt.getWidth();
            height = smt.getHeight();
        }
        this.caller = caller;
        canvas.setSize(width * 32, height * 32);
        setSize(canvas.getWidth() + getInsets().left + getInsets().right, canvas.getHeight() + getInsets().top + getInsets().bottom);
        color = new Color[nPieces + 1];
        color[0] = Color.BLACK;
        for (int i = 0; i < nPieces; i++) {
            color[i + 1] = new Color(Color.HSBtoRGB((float) i / (float) nPieces, i % 2 == 0 ? 1f : 0.5f, 0.75f));
        }
        solverThread = new Thread() {
            @Override
            public void run() {
                setPriority(Thread.MAX_PRIORITY);
                boolean solved = false;
                if (s != null) {
                    solved = s.solve();
                }
                if (smt != null) {
                    solved = smt.solve() != null;
                }
                if (canceled) {
                    return;
                }
                if (solved) {
                    repaint();
                } else {
                    solverThread = null;
                    setVisible(false);
                    JOptionPane.showMessageDialog(null, "Impossible", "", JOptionPane.ERROR_MESSAGE);
                    formWindowClosing(null);
                    dispose();
                }
            }
        };
        solverThread.start();
        new Thread() {
            @Override
            public void run() {
                while (solverThread != null) {
                    while (Visualizer.this.getState() == JFrame.ICONIFIED) {
                        setPriority(Thread.MIN_PRIORITY);
                        try {
                            sleep(100);
                        } catch (InterruptedException ex) {
                        }
                    }
                    if (isFocused()) {
                        setPriority(Thread.MAX_PRIORITY);
                    } else {
                        setPriority(Thread.MIN_PRIORITY);
                    }
                    if (s != null && s.isSolved()) {
                        break;
                    }
                    if (smt != null && smt.isSolved()) {
                        break;
                    }
                    paintDone = false;
                    canvas.repaint();
                    while (solverThread != null && !paintDone) {
                        try {
                            sleep(1);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
        }.start();
        Timer t = new Timer(100, new ActionListener() {
            long lastNIterations = 0;
            long lastT = System.nanoTime();

            @Override
            public void actionPerformed(ActionEvent e) {
                if (solverThread != null) {
                    if (s != null) {
                        if (s.isSolved()) {
                            setTitle("Solved");
                        } else {
                            long nIterations = s.getIterations();
                            long t = System.nanoTime();
                            setTitle("Solving - " + (nIterations - lastNIterations) * (1000000000L / (t - lastT)) + " iterations/s");
                            lastNIterations = nIterations;
                            lastT = t;
                        }
                    }
                    if (smt != null) {
                        if (smt.isSolved()) {
                            setTitle("Solved");
                        } else {
                            long nIterations = smt.getIterations();
                            long t = System.nanoTime();
                            setTitle("Solving - " + smt.getNCores() + " threads - " + (nIterations - lastNIterations) * (1000000000L / (t - lastT)) + " iterations/s");
                            lastNIterations = nIterations;
                            lastT = t;
                        }
                    }
                }
            }
        });
        t.setRepeats(true);
        t.start();
        f5 = new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (saving) {
                    return false;
                }
                if (e.getKeyCode() == KeyEvent.VK_F5 && e.getID() == KeyEvent.KEY_PRESSED) {
                    if (smt != null) {
                        new Thread() {
                            public void run() {
                                try {
                                    saving = true;
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    smt.saveState(baos);
                                    baos.flush();
                                    baos.close();
                                    saving = false;
                                    JFileChooser c = new JFileChooser();
                                    c.setDialogTitle("Save state");
                                    c.showSaveDialog(null);
                                    File x = c.getSelectedFile();
                                    if (x == null) {
                                        return;
                                    }
                                    GZIPOutputStream fos = new GZIPOutputStream(new FileOutputStream(x));
                                    fos.write(baos.toByteArray());
                                    fos.flush();
                                    fos.close();
                                    JOptionPane.showMessageDialog(null, "State saved");
                                } catch (Throwable ex) {
                                    JOptionPane.showMessageDialog(null, "Save failed");
                                }
                            }
                        }.start();

                    }
                }
                return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(f5);
    }
    private boolean paintDone = false;
    private int showingThread = 0;

    private void render(Graphics g) {
        if (saving) {
            g.setColor(new Color(((int) System.nanoTime() / 1000000) | 0xFF000000));
            g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            paintDone = true;
            return;
        }
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        int[][] sol = null;
        int height = 0, width = 0;
        if (s != null) {
            sol = s.getBoard();
            height = s.getHeight();
            width = s.getWidth();
        }
        if (smt != null) {
            int[][][] stat = smt.__status();
            if (stat != null) {
                showingThread %= stat.length;
                sol = stat[showingThread++];
            }
            height = smt.getHeight();
            width = smt.getWidth();
        }
        if (sol != null) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (sol[y][x] >= 0) {
                        g.setColor(color[sol[y][x]]);
                        g.fillRect(x * 32, y * 32, 32, 32);
                    }
                }
            }
        }
        paintDone = true;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        canvas = new javax.swing.JPanel(){
            public void paintComponent(Graphics g){
                render(g);
            }
        };

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        getContentPane().setLayout(null);

        javax.swing.GroupLayout canvasLayout = new javax.swing.GroupLayout(canvas);
        canvas.setLayout(canvasLayout);
        canvasLayout.setHorizontalGroup(
            canvasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 491, Short.MAX_VALUE)
        );
        canvasLayout.setVerticalGroup(
            canvasLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 388, Short.MAX_VALUE)
        );

        getContentPane().add(canvas);
        canvas.setBounds(0, 0, 491, 388);

        pack();
    }// </editor-fold>//GEN-END:initComponents


    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        canceled = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(f5);
        if (solverThread != null && solverThread.isAlive()) {
            if (s != null) {
                solverThread.stop();
                s=null;
            }
            if (smt != null) {
                smt.cancel();
                smt=null;
            }
        }
        solverThread = null;
        if (caller != null) {
            caller.setVisible(true);
        } else {
            System.exit(0);
        }
    }//GEN-LAST:event_formWindowClosing


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel canvas;
    // End of variables declaration//GEN-END:variables
}
