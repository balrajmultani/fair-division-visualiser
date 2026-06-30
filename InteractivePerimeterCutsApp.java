import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class InteractivePerimeterCutsApp extends JFrame {

    interface BestUpdateListener {
        void onNewBest(PerimeterCutsLabeling.SearchResult best, long checked);
    }

    static class SolverBridge {
        PerimeterCutsLabeling.SearchResult best = null;
        long checked = 0;
        boolean stopEarly = false;
        double targetPercentage;
        List<List<PerimeterCutsLabeling.Point>> shapes;
        BestUpdateListener listener;

        SolverBridge(BestUpdateListener listener) {
            this.listener = listener;
        }

        private PerimeterCutsLabeling.SearchResult deepCopyResult(
                List<PerimeterCutsLabeling.CutLine> cuts,
                List<PerimeterCutsLabeling.ShapeResult> results,
                double worstPercentage
        ) {
            List<PerimeterCutsLabeling.CutLine> cutCopy = new ArrayList<>(cuts);
            List<PerimeterCutsLabeling.ShapeResult> resultCopy = new ArrayList<>();
            for (PerimeterCutsLabeling.ShapeResult r : results) {
                resultCopy.add(new PerimeterCutsLabeling.ShapeResult(r.plus, r.minus));
            }
            return new PerimeterCutsLabeling.SearchResult(cutCopy, resultCopy, worstPercentage);
        }

        private void evaluateCombination(List<PerimeterCutsLabeling.CutLine> cuts) {
            checked++;

            List<PerimeterCutsLabeling.ShapeResult> results = new ArrayList<>();
            double worstPercentage = 0.0;

            for (List<PerimeterCutsLabeling.Point> shape : shapes) {
                PerimeterCutsLabeling.ShapeResult r = PerimeterCutsLabeling.evaluateShape(shape, cuts);
                results.add(r);
                worstPercentage = Math.max(worstPercentage, r.percentage);
            }

            if (best == null || worstPercentage < best.worstPercentage) {
                best = deepCopyResult(cuts, results, worstPercentage);
                if (listener != null) {
                    listener.onNewBest(deepCopyResult(cuts, results, worstPercentage), checked);
                }
            }

            if (worstPercentage <= targetPercentage) {
                stopEarly = true;
            }
        }

        private void search(
                List<PerimeterCutsLabeling.CutLine> allLines,
                int start,
                int numLines,
                List<PerimeterCutsLabeling.CutLine> current
        ) {
            if (stopEarly) return;

            if (current.size() == numLines) {
                evaluateCombination(current);
                return;
            }

            for (int i = start; i < allLines.size(); i++) {
                current.add(allLines.get(i));
                search(allLines, i + 1, numLines, current);
                current.remove(current.size() - 1);

                if (stopEarly) return;
            }
        }

        PerimeterCutsLabeling.SearchResult solve(
                List<List<PerimeterCutsLabeling.Point>> shapes,
                int gridSize,
                int numLines,
                double targetPercentage,
                int step
        ) {
            this.shapes = shapes;
            this.targetPercentage = targetPercentage;
            this.best = null;
            this.checked = 0;
            this.stopEarly = false;

            List<PerimeterCutsLabeling.Point> perimeter =
                    PerimeterCutsLabeling.buildPerimeterPoints(gridSize, step);
            List<PerimeterCutsLabeling.CutLine> allLines =
                    PerimeterCutsLabeling.buildAllLines(perimeter);

            search(allLines, 0, numLines, new ArrayList<>());
            return best;
        }
    }

    static class BestUpdate {
        PerimeterCutsLabeling.SearchResult result;
        long checked;

        BestUpdate(PerimeterCutsLabeling.SearchResult result, long checked) {
            this.result = result;
            this.checked = checked;
        }
    }

    private static final int GRID_SIZE = 1000;
    private static final int PANEL_PIXELS = 700;
    private static final int MARGIN = 45;

    private final List<List<PerimeterCutsLabeling.Point>> shapes = new ArrayList<>();
    private final List<PerimeterCutsLabeling.Point> currentShape = new ArrayList<>();
    private PerimeterCutsLabeling.SearchResult currentBest = null;
    private long lastCheckedCount = 0;

    private final DrawPanel drawPanel = new DrawPanel();
    private final JTextField numLinesField = new JTextField("3", 6);
    private final JTextField targetField = new JTextField("5", 6);
    private final JTextField stepField = new JTextField("100", 6);
    private final JTextArea outputArea = new JTextArea(18, 28);

    private final JButton finishShapeButton = new JButton("Finish Shape");
    private final JButton undoPointButton = new JButton("Undo Point");
    private final JButton clearCurrentButton = new JButton("Clear Current");
    private final JButton clearAllButton = new JButton("Clear All");
    private final JButton searchButton = new JButton("Search");
    private final JButton removeLastShapeButton = new JButton("Remove Last Shape");

    public InteractivePerimeterCutsApp() {
        super("Interactive Perimeter Cuts");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel rightPanel = buildControlPanel();

        add(drawPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        log("Click on the grid to add points.");
        log("Press 'Finish Shape' when a polygon is complete.");
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel params = new JPanel(new GridLayout(0, 2, 6, 6));
        params.setBorder(new TitledBorder("Search Settings"));
        params.add(new JLabel("Cut lines:"));
        params.add(numLinesField);
        params.add(new JLabel("Target %:"));
        params.add(targetField);
        params.add(new JLabel("Perimeter step:"));
        params.add(stepField);

        JPanel buttons = new JPanel(new GridLayout(0, 1, 6, 6));
        buttons.setBorder(new TitledBorder("Actions"));
        buttons.add(finishShapeButton);
        buttons.add(undoPointButton);
        buttons.add(clearCurrentButton);
        buttons.add(removeLastShapeButton);
        buttons.add(clearAllButton);
        buttons.add(searchButton);

        finishShapeButton.addActionListener(e -> finishCurrentShape());
        undoPointButton.addActionListener(e -> {
            if (!currentShape.isEmpty()) {
                currentShape.remove(currentShape.size() - 1);
                drawPanel.repaint();
            }
        });
        clearCurrentButton.addActionListener(e -> {
            currentShape.clear();
            drawPanel.repaint();
        });
        removeLastShapeButton.addActionListener(e -> {
            if (!shapes.isEmpty()) {
                shapes.remove(shapes.size() - 1);
                currentBest = null;
                log("Removed last saved shape.");
                drawPanel.repaint();
            }
        });
        clearAllButton.addActionListener(e -> {
            shapes.clear();
            currentShape.clear();
            currentBest = null;
            outputArea.setText("");
            log("Cleared all shapes and results.");
            drawPanel.repaint();
        });
        searchButton.addActionListener(e -> runSearch());

        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setBorder(new TitledBorder("Output"));

        controls.add(params);
        controls.add(Box.createVerticalStrut(10));
        controls.add(buttons);
        controls.add(Box.createVerticalStrut(10));
        controls.add(scrollPane);

        panel.add(controls, BorderLayout.CENTER);
        return panel;
    }

    private void finishCurrentShape() {
        if (currentShape.size() < 3) {
            JOptionPane.showMessageDialog(this, "A shape needs at least 3 points.");
            return;
        }

        shapes.add(new ArrayList<>(currentShape));

        log("Shape " + shapes.size() + " points:");
        for (int i = 0; i < currentShape.size(); i++) {
            PerimeterCutsLabeling.Point p = currentShape.get(i);
            log("Point " + (i + 1) + ": (" + p.x + ", " + p.y + ")");
        }
        log("");

        currentShape.clear();
        currentBest = null;
        log("Saved shape " + shapes.size() + ".");
        drawPanel.repaint();
    }

    private void runSearch() {
        if (!currentShape.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "You have an unfinished current shape. Finish it before searching?",
                    "Unfinished Shape",
                    JOptionPane.YES_NO_CANCEL_OPTION
            );

            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION) {
                finishCurrentShape();
            }
        }

        if (shapes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Draw and save at least one shape first.");
            return;
        }

        final int numLines;
        final double targetPercentage;
        final int step;

        try {
            numLines = Integer.parseInt(numLinesField.getText().trim());
            targetPercentage = Double.parseDouble(targetField.getText().trim());
            step = Integer.parseInt(stepField.getText().trim());

            if (numLines <= 0 || step <= 0) {
                throw new NumberFormatException("Values must be positive.");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter valid positive numeric settings.");
            return;
        }

        searchButton.setEnabled(false);
        finishShapeButton.setEnabled(false);
        undoPointButton.setEnabled(false);
        clearCurrentButton.setEnabled(false);
        clearAllButton.setEnabled(false);
        removeLastShapeButton.setEnabled(false);

        outputArea.setText("");
        currentBest = null;
        drawPanel.repaint();

        log("Starting brute force search...");
        log("Shapes: " + shapes.size());
        log("Cut lines: " + numLines + ", target %: " + targetPercentage + ", step: " + step);
        log("");

        SwingWorker<PerimeterCutsLabeling.SearchResult, BestUpdate> worker = new SwingWorker<>() {
            SolverBridge solver;

            @Override
            protected PerimeterCutsLabeling.SearchResult doInBackground() {
                solver = new SolverBridge((best, checked) -> publish(new BestUpdate(best, checked)));
                PerimeterCutsLabeling.SearchResult result =
                        solver.solve(shapes, GRID_SIZE, numLines, targetPercentage, step);
                lastCheckedCount = solver.checked;
                return result;
            }

            @Override
            protected void process(List<BestUpdate> chunks) {
                for (BestUpdate update : chunks) {
                    currentBest = update.result;

                    log("New best found after checking " + update.checked + " combinations:");
                    for (int i = 0; i < update.result.shapeResults.size(); i++) {
                        PerimeterCutsLabeling.ShapeResult r = update.result.shapeResults.get(i);
                        log("Shape " + (i + 1)
                                + " +:" + r.plus
                                + ", -:" + r.minus
                                + ", Difference:" + r.difference
                                + " (" + String.format("%.2f", r.percentage) + "%)");
                    }
                    log("Worst percentage so far: "
                            + String.format("%.2f", update.result.worstPercentage) + "%");
                    log("");

                    drawPanel.repaint();
                }
            }

            @Override
            protected void done() {
                try {
                    currentBest = get();
                    log("RESULTS:");
                    log("Checked combinations: " + lastCheckedCount);

                    if (currentBest != null) {
                        for (int i = 0; i < currentBest.shapeResults.size(); i++) {
                            PerimeterCutsLabeling.ShapeResult r = currentBest.shapeResults.get(i);
                            log("Shape " + (i + 1)
                                    + " +:" + r.plus
                                    + ", -:" + r.minus
                                    + ", Difference:" + r.difference
                                    + " (" + String.format("%.2f", r.percentage) + "%)");
                        }

                        log("Best cuts:");
                        for (int i = 0; i < currentBest.cuts.size(); i++) {
                            log("Line " + (i + 1) + ": " + currentBest.cuts.get(i));
                        }

                        log("Worst shape percentage: "
                                + String.format("%.2f", currentBest.worstPercentage) + "%");
                    } else {
                        log("No result found.");
                    }

                    drawPanel.repaint();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            InteractivePerimeterCutsApp.this,
                            "Search failed: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                } finally {
                    searchButton.setEnabled(true);
                    finishShapeButton.setEnabled(true);
                    undoPointButton.setEnabled(true);
                    clearCurrentButton.setEnabled(true);
                    clearAllButton.setEnabled(true);
                    removeLastShapeButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void log(String text) {
        outputArea.append(text + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private class DrawPanel extends JPanel {
        private final Color[] shapeColors = {
                new Color(66, 135, 245),
                new Color(245, 130, 49),
                new Color(60, 179, 113),
                new Color(170, 102, 204),
                new Color(0, 153, 153),
                new Color(220, 20, 60)
        };

        DrawPanel() {
            setPreferredSize(new Dimension(PANEL_PIXELS + 2 * MARGIN, PANEL_PIXELS + 2 * MARGIN));
            setBackground(Color.WHITE);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int gx = toGridX(e.getX());
                    int gy = toGridY(e.getY());

                    if (gx < 0 || gx > GRID_SIZE || gy < 0 || gy > GRID_SIZE) return;

                    currentShape.add(new PerimeterCutsLabeling.Point(gx, gy));
                    repaint();
                }
            });
        }

        private int scaleX(int x) {
            return MARGIN + (int) Math.round((x / (double) GRID_SIZE) * PANEL_PIXELS);
        }

        private int scaleY(int y) {
            return MARGIN + PANEL_PIXELS - (int) Math.round((y / (double) GRID_SIZE) * PANEL_PIXELS);
        }

        private int toGridX(int px) {
            double t = (px - MARGIN) / (double) PANEL_PIXELS;
            return (int) Math.round(t * GRID_SIZE);
        }

        private int toGridY(int py) {
            double t = (py - MARGIN) / (double) PANEL_PIXELS;
            return (int) Math.round((1.0 - t) * GRID_SIZE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            drawGrid(g2);
            drawLabeledAreas(g2);
            drawShapes(g2);
            drawCurrentShape(g2);
            drawBestCuts(g2);
        }

        private void drawGrid(Graphics2D g2) {
            g2.setColor(new Color(230, 230, 230));

            for (int i = 0; i <= GRID_SIZE; i += 100) {
                int x = scaleX(i);
                int y = scaleY(i);

                g2.drawLine(x, scaleY(0), x, scaleY(GRID_SIZE));
                g2.drawLine(scaleX(0), y, scaleX(GRID_SIZE), y);
            }

            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(scaleX(0), scaleY(GRID_SIZE), PANEL_PIXELS, PANEL_PIXELS);

            for (int i = 0; i <= GRID_SIZE; i += 100) {
                int x = scaleX(i);
                int y = scaleY(i);

                g2.drawString(String.valueOf(i), x - 10, scaleY(0) + 20);
                g2.drawString(String.valueOf(i), scaleX(0) - 35, y + 5);
            }
        }

        private void drawLabeledAreas(Graphics2D g2) {
            if (currentBest == null || currentBest.cuts == null || shapes.isEmpty()) return;

            int sampleStep = 28;

            g2.setColor(Color.BLACK);
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));

            for (List<PerimeterCutsLabeling.Point> shape : shapes) {
                int[] bbox = PerimeterCutsLabeling.boundingBox(shape);

                for (int y = bbox[2]; y <= bbox[3]; y += sampleStep) {
                    for (int x = bbox[0]; x <= bbox[1]; x += sampleStep) {
                        PerimeterCutsLabeling.Point p = new PerimeterCutsLabeling.Point(x, y);

                        if (!PerimeterCutsLabeling.pointInPolygon(p, shape)) continue;

                        boolean positive = true;
                        for (PerimeterCutsLabeling.CutLine line : currentBest.cuts) {
                            if (line.side(p) > 0) {
                                positive = !positive;
                            }
                        }

                        String label = positive ? "+" : "-";
                        int sx = scaleX(x) - 4;
                        int sy = scaleY(y) + 4;
                        g2.drawString(label, sx, sy);
                    }
                }
            }
        }

        private void drawShapes(Graphics2D g2) {
            for (int s = 0; s < shapes.size(); s++) {
                List<PerimeterCutsLabeling.Point> shape = shapes.get(s);
                Color color = shapeColors[s % shapeColors.length];

                g2.setColor(color);
                g2.setStroke(new BasicStroke(3));

                for (int i = 0; i < shape.size(); i++) {
                    PerimeterCutsLabeling.Point p1 = shape.get(i);
                    PerimeterCutsLabeling.Point p2 = shape.get((i + 1) % shape.size());
                    g2.drawLine(scaleX(p1.x), scaleY(p1.y), scaleX(p2.x), scaleY(p2.y));
                }

                for (PerimeterCutsLabeling.Point p : shape) {
                    g2.fillOval(scaleX(p.x) - 4, scaleY(p.y) - 4, 8, 8);
                }
            }
        }

        private void drawCurrentShape(Graphics2D g2) {
            if (currentShape.isEmpty()) return;

            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(2));

            for (int i = 0; i < currentShape.size(); i++) {
                PerimeterCutsLabeling.Point p = currentShape.get(i);
                g2.fillOval(scaleX(p.x) - 4, scaleY(p.y) - 4, 8, 8);

                if (i > 0) {
                    PerimeterCutsLabeling.Point prev = currentShape.get(i - 1);
                    g2.drawLine(scaleX(prev.x), scaleY(prev.y), scaleX(p.x), scaleY(p.y));
                }
            }
        }

        private void drawBestCuts(Graphics2D g2) {
            if (currentBest == null || currentBest.cuts == null) return;

            Color[] lineColors = {
                    Color.RED,
                    new Color(128, 0, 128),
                    new Color(139, 69, 19),
                    new Color(0, 100, 0),
                    Color.MAGENTA
            };

            for (int i = 0; i < currentBest.cuts.size(); i++) {
                PerimeterCutsLabeling.CutLine line = currentBest.cuts.get(i);
                g2.setColor(lineColors[i % lineColors.length]);
                g2.setStroke(new BasicStroke(2));

                PerimeterCutsLabeling.Point[] clipped =
                        clipLineToGrid(line.a, line.b, 0, GRID_SIZE, 0, GRID_SIZE);

                if (clipped[0] != null && clipped[1] != null) {
                    g2.drawLine(
                            scaleX(clipped[0].x), scaleY(clipped[0].y),
                            scaleX(clipped[1].x), scaleY(clipped[1].y)
                    );
                }
            }
        }

        private PerimeterCutsLabeling.Point[] clipLineToGrid(
                PerimeterCutsLabeling.Point a,
                PerimeterCutsLabeling.Point b,
                int minX,
                int maxX,
                int minY,
                int maxY
        ) {
            List<PerimeterCutsLabeling.Point> intersections = new ArrayList<>();

            double x1 = a.x, y1 = a.y;
            double x2 = b.x, y2 = b.y;

            if (x1 == x2) {
                int x = (int) x1;
                if (x >= minX && x <= maxX) {
                    intersections.add(new PerimeterCutsLabeling.Point(x, minY));
                    intersections.add(new PerimeterCutsLabeling.Point(x, maxY));
                }
            } else {
                double m = (y2 - y1) / (x2 - x1);
                double c = y1 - m * x1;

                double yAtMinX = m * minX + c;
                if (yAtMinX >= minY && yAtMinX <= maxY) {
                    intersections.add(new PerimeterCutsLabeling.Point(minX, (int) Math.round(yAtMinX)));
                }

                double yAtMaxX = m * maxX + c;
                if (yAtMaxX >= minY && yAtMaxX <= maxY) {
                    intersections.add(new PerimeterCutsLabeling.Point(maxX, (int) Math.round(yAtMaxX)));
                }

                if (m != 0) {
                    double xAtMinY = (minY - c) / m;
                    if (xAtMinY >= minX && xAtMinY <= maxX) {
                        intersections.add(new PerimeterCutsLabeling.Point((int) Math.round(xAtMinY), minY));
                    }

                    double xAtMaxY = (maxY - c) / m;
                    if (xAtMaxY >= minX && xAtMaxY <= maxX) {
                        intersections.add(new PerimeterCutsLabeling.Point((int) Math.round(xAtMaxY), maxY));
                    }
                }
            }

            List<PerimeterCutsLabeling.Point> unique = new ArrayList<>();
            for (PerimeterCutsLabeling.Point p : intersections) {
                boolean exists = false;
                for (PerimeterCutsLabeling.Point q : unique) {
                    if (p.x == q.x && p.y == q.y) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) unique.add(p);
            }

            if (unique.size() >= 2) {
                return new PerimeterCutsLabeling.Point[]{unique.get(0), unique.get(1)};
            }

            return new PerimeterCutsLabeling.Point[]{null, null};
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(InteractivePerimeterCutsApp::new);
    }
}