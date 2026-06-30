import java.util.*;

public class PerimeterCutsLabeling {

    static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }
    }

    static class CutLine {
        enum Type { VERTICAL, SLOPE_INTERCEPT }
        Type type;
        double k, m, c;
        Point a, b;

        CutLine(Point a, Point b) {
            this.a = a;
            this.b = b;

            if (a.x == b.x) {
                type = Type.VERTICAL;
                k = a.x;
            } else {
                type = Type.SLOPE_INTERCEPT;
                m = (double)(b.y - a.y) / (b.x - a.x);
                c = a.y - m * a.x;
            }
        }

        int side(Point p) {
            final double EPS = 1e-9;
            if (type == Type.VERTICAL) {
                if (p.x > k + EPS) return 1;
                if (p.x < k - EPS) return -1;
                return 0;
            } else {
                double lineY = m * p.x + c;
                if (p.y > lineY + EPS) return 1;
                if (p.y < lineY - EPS) return -1;
                return 0;
            }
        }

        @Override
        public String toString() {
            return a + " -> " + b;
        }
    }

    static class ShapeResult {
        int plus;
        int minus;
        int difference;
        double percentage;

        ShapeResult(int plus, int minus) {
            this.plus = plus;
            this.minus = minus;
            this.difference = Math.abs(plus - minus);
            int total = plus + minus;
            this.percentage = (total == 0) ? 0.0 : (difference * 100.0 / total);
        }
    }

    static class SearchResult {
        List<CutLine> cuts;
        List<ShapeResult> shapeResults;
        double worstPercentage;

        SearchResult(List<CutLine> cuts, List<ShapeResult> shapeResults, double worstPercentage) {
            this.cuts = cuts;
            this.shapeResults = shapeResults;
            this.worstPercentage = worstPercentage;
        }
    }

    static SearchResult best = null;
    static long checked = 0;
    static boolean stopEarly = false;
    static double targetPercentageGlobal;
    static List<List<Point>> shapesGlobal;

    static boolean pointInPolygon(Point p, List<Point> vertices) {
        int n = vertices.size();
        boolean inside = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point vi = vertices.get(i);
            Point vj = vertices.get(j);

            if (onSegment(vi, vj, p)) return false;

            if (((vi.y > p.y) != (vj.y > p.y)) &&
                    (p.x < (vj.x - vi.x) * (p.y - vi.y) / (double)(vj.y - vi.y) + vi.x)) {
                inside = !inside;
            }
        }
        return inside;
    }

    static boolean onSegment(Point a, Point b, Point p) {
        return (p.x - a.x) * (b.y - a.y) == (p.y - a.y) * (b.x - a.x) &&
                p.x >= Math.min(a.x, b.x) && p.x <= Math.max(a.x, b.x) &&
                p.y >= Math.min(a.y, b.y) && p.y <= Math.max(a.y, b.y);
    }

    static int[] boundingBox(List<Point> vertices) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (Point p : vertices) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        return new int[]{minX, maxX, minY, maxY};
    }

    static List<Point> buildPerimeterPoints(int gridSize, int step) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Point> perimeter = new ArrayList<>();

        for (int x = 1; x <= gridSize; x += step) addPerimeterPoint(perimeter, seen, x, 1);
        addPerimeterPoint(perimeter, seen, gridSize, 1);

        for (int y = 1; y <= gridSize; y += step) addPerimeterPoint(perimeter, seen, gridSize, y);
        addPerimeterPoint(perimeter, seen, gridSize, gridSize);

        for (int x = gridSize; x >= 1; x -= step) addPerimeterPoint(perimeter, seen, x, gridSize);
        addPerimeterPoint(perimeter, seen, 1, gridSize);

        for (int y = gridSize; y >= 1; y -= step) addPerimeterPoint(perimeter, seen, 1, y);
        addPerimeterPoint(perimeter, seen, 1, 1);

        return perimeter;
    }

    static void addPerimeterPoint(List<Point> perimeter, Set<String> seen, int x, int y) {
        String key = x + "," + y;
        if (seen.add(key)) perimeter.add(new Point(x, y));
    }

    static List<CutLine> buildAllLines(List<Point> perimeter) {
        List<CutLine> lines = new ArrayList<>();
        for (int i = 0; i < perimeter.size(); i++) {
            for (int j = i + 1; j < perimeter.size(); j++) {
                lines.add(new CutLine(perimeter.get(i), perimeter.get(j)));
            }
        }
        return lines;
    }

    static ShapeResult evaluateShape(List<Point> shape, List<CutLine> cuts) {
        int[] bbox = boundingBox(shape);
        int minX = bbox[0], maxX = bbox[1], minY = bbox[2], maxY = bbox[3];

        int plus = 0, minus = 0;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                Point p = new Point(x, y);

                if (!pointInPolygon(p, shape)) continue;

                boolean positive = true;
                for (CutLine line : cuts) {
                    if (line.side(p) > 0) positive = !positive;
                }

                if (positive) plus++;
                else minus++;
            }
        }

        return new ShapeResult(plus, minus);
    }

    static void evaluateCombination(List<CutLine> cuts) {
        checked++;

        List<ShapeResult> results = new ArrayList<>();
        double worstPercentage = 0.0;

        for (List<Point> shape : shapesGlobal) {
            ShapeResult r = evaluateShape(shape, cuts);
            results.add(r);
            worstPercentage = Math.max(worstPercentage, r.percentage);
        }

        if (best == null || worstPercentage < best.worstPercentage) {
            best = new SearchResult(new ArrayList<>(cuts), results, worstPercentage);

            System.out.println("\nNew best found after checking " + checked + " combinations:");
            for (int i = 0; i < best.shapeResults.size(); i++) {
                ShapeResult r = best.shapeResults.get(i);
                System.out.println("Shape " + (i + 1)
                        + " +:" + r.plus
                        + ", Shape " + (i + 1)
                        + " -:" + r.minus
                        + ", Difference:" + r.difference
                        + " (" + String.format("%.2f", r.percentage) + "%)");
            }
            System.out.println("Worst percentage so far: " + String.format("%.2f", best.worstPercentage) + "%");
        }

        if (worstPercentage <= targetPercentageGlobal) {
            stopEarly = true;
        }
    }

    static void generateAndEvaluateCombinations(
            List<CutLine> allLines,
            int start,
            int numLines,
            List<CutLine> current
    ) {
        if (stopEarly) return;

        if (current.size() == numLines) {
            evaluateCombination(current);
            return;
        }

        for (int i = start; i < allLines.size(); i++) {
            current.add(allLines.get(i));
            generateAndEvaluateCombinations(allLines, i + 1, numLines, current);
            current.remove(current.size() - 1);

            if (stopEarly) return;
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        int gridSize = 1000;

        System.out.println("How many shapes?");
        int numShapes = sc.nextInt();

        List<List<Point>> shapes = new ArrayList<>();
        for (int s = 1; s <= numShapes; s++) {
            System.out.println("Enter number of points for Shape " + s + ":");
            int nPoints = sc.nextInt();

            List<Point> vertices = new ArrayList<>();
            for (int i = 1; i <= nPoints; i++) {
                System.out.print("Point " + i + ": ");
                int x = sc.nextInt();
                int y = sc.nextInt();
                vertices.add(new Point(x, y));
            }
            shapes.add(vertices);
        }

        shapesGlobal = shapes;

        System.out.print("How many cut lines should be used? ");
        int numLines = sc.nextInt();

        System.out.print("Enter target percentage to stop early (e.g. 5): ");
        targetPercentageGlobal = sc.nextDouble();

        System.out.print("Enter perimeter sampling step (e.g. 100, 200, 250): ");
        int step = sc.nextInt();

        System.out.println("\nBuilding sampled perimeter points...");
        List<Point> perimeter = buildPerimeterPoints(gridSize, step);
        System.out.println("Sampled perimeter points: " + perimeter.size());

        System.out.println("Building candidate lines...");
        List<CutLine> allLines = buildAllLines(perimeter);
        System.out.println("Candidate lines: " + allLines.size());

        System.out.println("Starting brute force search...");
        generateAndEvaluateCombinations(allLines, 0, numLines, new ArrayList<>());

        System.out.println("\nRESULTS:");
        System.out.println("Checked combinations: " + checked);

        if (best != null) {
            for (int i = 0; i < best.shapeResults.size(); i++) {
                ShapeResult r = best.shapeResults.get(i);
                System.out.println("Shape " + (i + 1)
                        + " +:" + r.plus
                        + ", Shape " + (i + 1)
                        + " -:" + r.minus
                        + ", Difference:" + r.difference
                        + " (" + String.format("%.2f", r.percentage) + "%)");
            }

            System.out.println("Best cuts:");
            for (int i = 0; i < best.cuts.size(); i++) {
                System.out.println("Line " + (i + 1) + ": " + best.cuts.get(i));
            }

            System.out.println("Worst shape percentage: " + String.format("%.2f", best.worstPercentage) + "%");
        }

        sc.close();
    }
}