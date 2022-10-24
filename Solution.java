import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Solution {

    private static final Path BACKTRACKING_OUT = Path.of("outputBacktracking.txt");
    private static final Path A_STAR_OUT = Path.of("outputAStar.txt");

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                InputHelper.tryInitStreams();

                var point = InputHelper.getPoints();
                var scenario = InputHelper.getScenario();

                var game = new GameData(point);

                var backtracking = new Backtracking(game);

                var startMillis = System.currentTimeMillis();
                backtracking.run();

                OutputHelper.printResult(
                        backtracking.getCurrentSnapshot(),
                        System.currentTimeMillis() - startMillis
                );
            } else if (args[0].equals("-t") || args[0].equals("--test")) {
                System.out.println(TestHelper.run(1000, TestHelper.BACKTRACKING));
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}

interface Cell {
    default boolean hasType(Class<?> cls) {
        return this.getClass() == cls;
    }

    default boolean isSafe() {
        return this instanceof ObjectCell || this == AirCell.FREE;
    }

    default boolean isKraken() {
        return this == KrakenPairCell.KRAKEN || this == KrakenPairCell.KRAKEN_ROCK;
    }

    default boolean isFree() {
        return this.hasType(AirCell.class);
    }
}

enum AirCell implements Cell {
    PERCEPTION,
    FREE
}

enum EnemyCell implements Cell {
    DAVY_JONES
}

enum KrakenPairCell implements Cell {
    KRAKEN,
    ROCK,
    KRAKEN_ROCK
}

enum ObjectCell implements Cell {
    TORTUGA,
    CHEST
}

class Matrix implements Cloneable {
    private Point[][] matrix = new Point[9][9];

    public Matrix() {
        for (int y = 0; y < 9; y++)
            matrix[y] = new Point[9];

        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++)
                matrix[x][y] = new Point(x, y);
    }

    public Optional<Point> getPoint(int x, int y) {
        if (y < 0 || y >= 9 || x < 0 || x >= 9) return Optional.empty();
        return Optional.of(matrix[x][y]);
    }

    public Stream<Point> neighbors(int x, int y) {
        return Stream.of(
                        getPoint(x, y + 1), getPoint(x, y - 1),
                        getPoint(x + 1, y), getPoint(x - 1, y)
                )
                .filter(Optional::isPresent)
                .map(p -> p.orElse(null));
    }

    public Stream<Point> corners(int x, int y) {
        return Stream.of(
                        getPoint(x + 1, y + 1), getPoint(x + 1, y - 1),
                        getPoint(x - 1, y + 1), getPoint(x - 1, y - 1)
                )
                .filter(Optional::isPresent)
                .map(p -> p.orElse(null));
    }

    public Stream<Point> secondNeighbors(int x, int y) {
        return Stream.of(
                        getPoint(x, y + 2), getPoint(x, y - 2),
                        getPoint(x + 2, y), getPoint(x - 2, y)
                )
                .filter(Optional::isPresent)
                .map(p -> p.orElse(null));
    }

    @Override
    public String toString() {
        var builder = new StringBuilder("-".repeat(19)).append("\n  ");

        for (int i = 0; i < 8; i++)
            builder.append(i).append(" ");
        builder.append(8).append("\n");

        for (int y = 0; y < 9; y++) {
            builder.append(y).append(" ");
            for (int x = 0; x < 8; x++)
                builder.append(matrix[y][x].isPath() ? "*" : "_").append(" ");
            builder.append(matrix[y][8].isPath() ? "*" : "_").append("\n");
        }

        builder.append("-".repeat(19));
        return builder.toString();
    }

    @Override
    public Matrix clone() {
        try {
            var clone = (Matrix) super.clone();

            clone.matrix = new Point[9][9];

            for (int i = 0; i < 9; i++)
                clone.matrix[i] = new Point[9];

            for (int y = 0; y < 9; y++)
                for (int x = 0; x < 9; x++)
                    clone.matrix[x][y] = matrix[x][y].clone();

            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}

class Point implements Cloneable {
    private final int x;

    private final int y;

    private boolean path = false;

    private Cell cell = AirCell.FREE;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isPath() {
        return path;
    }

    public Cell getCell() {
        return cell;
    }

    public void setPath(boolean path) {
        this.path = path;
    }

    public void setCell(Cell cell) {
        this.cell = cell;
    }

    @Override
    public Point clone() {
        try {
            var clone = (Point) super.clone();
            clone.setCell(getCell());
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var point = (Point) o;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return String.format("[%d,%d]", getX(), getY());
    }
}

class GameData implements Cloneable {

    private static final Random RANDOM = new Random();
    private Matrix matrix = new Matrix();

    private Point jackSparrow;
    private Point davyJones;
    private Point kraken;
    private Point rock;
    private Point chest;
    private Point tortuga;

    private boolean trySetEnemy(Cell cell, int x, int y) {
        if (matrix.getPoint(x, y).isPresent()) {

            var point = matrix.getPoint(x, y).get();
            var matrixCell = point.getCell();

            if (matrixCell.isFree() || cell == matrixCell) {
                point.setCell(cell);
                return true;
            }
        }

        return false;
    }

    private boolean trySetObject(Cell cell, int x, int y) {
        if (matrix.getPoint(x, y).isPresent()) {

            var point = matrix.getPoint(x, y).get();
            var matrixCell = point.getCell();

            if (matrixCell.isFree() && matrixCell.isSafe() || cell == matrixCell) {
                point.setCell(cell);
                return true;
            }
        }

        return false;
    }

    private void trySetAir(Cell cell, int x, int y) {
        trySetEnemy(cell, x, y);
    }

    private boolean trySetJackSparrow(int x, int y) {
        if (matrix.getPoint(x, y).isPresent()) {
            var cell = matrix.getPoint(x, y).get().getCell();
            if (cell.isFree() || cell == ObjectCell.TORTUGA) {
                jackSparrow = matrix.getPoint(x, y).get();
                return true;
            }
        }

        return false;
    }

    private boolean trySetDavyJones(int x, int y) {
        if (trySetEnemy(EnemyCell.DAVY_JONES, x, y)) {

            if (matrix.getPoint(x, y).isPresent()) {
                matrix.neighbors(x, y).forEach(c -> trySetAir(AirCell.PERCEPTION, c.getX(), c.getY()));
                matrix.corners(x, y).forEach(c -> trySetAir(AirCell.PERCEPTION, c.getX(), c.getY()));
                davyJones = matrix.getPoint(x, y).get();

                return true;
            }
        }

        return false;
    }

    public void setPath(int x, int y) {
        matrix.getPoint(x, y).ifPresent(p -> p.setPath(true));
    }

    public void unsetPath(int x, int y) {
        matrix.getPoint(x, y).ifPresent(p -> p.setPath(false));
    }

    public boolean trySetKraken(int x, int y) {
        if (matrix.getPoint(x, y).isPresent()) {

            var point = matrix.getPoint(x, y).get();
            var matrixCell = point.getCell();

            var newCell = matrixCell == KrakenPairCell.ROCK
                    ? KrakenPairCell.KRAKEN_ROCK
                    : KrakenPairCell.KRAKEN;

            if (matrixCell.hasType(KrakenPairCell.class) || matrixCell.isFree()) {
                point.setCell(newCell);
                matrix.neighbors(x, y).forEach(c -> trySetAir(AirCell.PERCEPTION, c.getX(), c.getY()));
                kraken = matrix.getPoint(x, y).get();

                return true;
            }
        }

        return false;
    }

    public void tryRemoveKraken() {
        if (kraken == null) return;

        var newCell = kraken.getCell() == KrakenPairCell.KRAKEN_ROCK
                ? KrakenPairCell.ROCK
                : AirCell.FREE;

        kraken.setCell(newCell);

        var optPoint = matrix.getPoint(kraken.getX(), kraken.getY());
        optPoint.ifPresent(point -> point.setCell(newCell));

        matrix.neighbors(kraken.getX(), kraken.getY()).forEach(c -> trySetAir(AirCell.FREE, c.getX(), c.getY()));

        // Force update DavyJones perception zones in order to restore some of them
        // After Kraken removal
        trySetDavyJones(davyJones.getX(), davyJones.getY());

    }

    private boolean trySetRock(int x, int y) {
        if (matrix.getPoint(x, y).isPresent()) {

            var point = matrix.getPoint(x, y).get();
            var matrixCell = point.getCell();

            var newCell = matrixCell == KrakenPairCell.KRAKEN
                    ? KrakenPairCell.KRAKEN_ROCK
                    : KrakenPairCell.ROCK;

            if (matrixCell.hasType(KrakenPairCell.class) || matrixCell.isFree()) {
                point.setCell(newCell);
                rock = point;

                return true;
            }
        }

        return false;
    }

    private boolean trySetChest(int x, int y) {
        if (trySetObject(ObjectCell.CHEST, x, y)) {

            if (matrix.getPoint(x, y).isPresent()) {
                chest = matrix.getPoint(x, y).get();
                return true;
            }
        }

        return false;
    }

    private boolean trySetTortuga(int x, int y) {
        if (trySetObject(ObjectCell.TORTUGA, x, y)) {

            if (matrix.getPoint(x, y).isPresent()) {
                tortuga = matrix.getPoint(x, y).get();
                return true;
            }
        }

        return false;
    }

    private Point getRandomPoint() {
        var optPos = matrix.getPoint(RANDOM.nextInt(0, 9), RANDOM.nextInt(0, 9));
        if (optPos.isEmpty() || (optPos.get().getX() == 0 && optPos.get().getY() != 0))
            return getRandomPoint();
        return optPos.get();
    }

    public GameData(List<Point> points) {
        var generationResult = Stream.of(
                trySetDavyJones(points.get(1).getX(), points.get(1).getY()),
                trySetKraken(points.get(2).getX(), points.get(2).getY()),
                trySetRock(points.get(3).getX(), points.get(3).getY()),
                trySetChest(points.get(4).getX(), points.get(4).getY()),
                trySetTortuga(points.get(5).getX(), points.get(5).getY()),
                trySetJackSparrow(points.get(0).getX(), points.get(0).getY())
        ).allMatch(x -> x);

        if (!generationResult)
            throw new IllegalArgumentException("Failed to spawn game entities!");
    }

    public GameData() {
        var optPoint = matrix.getPoint(0, 0);

        optPoint.ifPresent(point -> trySetJackSparrow(point.getX(), point.getY()));

        var point = getRandomPoint();
        while (!trySetDavyJones(point.getX(), point.getY()))
            point = getRandomPoint();

        point = getRandomPoint();
        while (!trySetKraken(point.getX(), point.getY()))
            point = getRandomPoint();

        point = getRandomPoint();
        while (!trySetRock(point.getX(), point.getY()))
            point = getRandomPoint();

        point = getRandomPoint();
        while (!trySetChest(point.getX(), point.getY()))
            point = getRandomPoint();

        point = getRandomPoint();
        while (!trySetTortuga(point.getX(), point.getY()))
            point = getRandomPoint();
    }

    public Matrix getMatrix() {
        return matrix;
    }

    public Point getJackSparrow() {
        return jackSparrow;
    }

    public Point getDavyJones() {
        return davyJones;
    }

    public Point getKraken() {
        return kraken;
    }

    public Point getRock() {
        return rock;
    }

    public Point getChest() {
        return chest;
    }

    public Point getTortuga() {
        return tortuga;
    }

    @Override
    public String toString() {
        return matrix.toString();
    }

    @Override
    public GameData clone() {
        try {
            var clone = (GameData) super.clone();
            clone.matrix = matrix.clone();
            clone.chest = chest;
            clone.davyJones = davyJones;
            clone.jackSparrow = jackSparrow;
            clone.kraken = kraken;
            clone.rock = rock;
            clone.tortuga = tortuga;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}

class Snapshot {

    private List<Point> steps;
    private GameData gameData;

    public Snapshot(List<Point> steps, GameData gameData) {
        this.steps = steps;
        this.gameData = gameData;
    }

    public List<Point> getSteps() {
        return steps;
    }

    public void setSteps(List<Point> steps) {
        this.steps = steps;
    }

    public GameData getGameData() {
        return gameData;
    }

    public void setGameData(GameData gameData) {
        this.gameData = gameData;
    }

    @Override
    public String toString() {
        var shortestPathString = steps.stream()
                .map(Point::toString)
                .collect(Collectors.joining(" "));

        return String.format("%d\n%s\n%s", steps.size(), shortestPathString, gameData.getMatrix());
    }
}

class Backtracking {
    private GameData gameData;

    private final int[][] costs = new int[9][9];
    private Snapshot currentSnapshot;

    private final Stack<Point> steps = new Stack<>();

    private Point target;

    private int minStepsCount = Integer.MAX_VALUE;

    private List<Point> moves(Point point) {
        return Stream.concat(
                        gameData.getMatrix().corners(point.getX(), point.getY()),
                        gameData.getMatrix().neighbors(point.getX(), point.getY())
                )
                .filter(p -> p.getCell().isKraken() || p.getCell().isSafe())
                .filter(p -> costs[p.getX()][p.getY()] >= steps.size())
                .toList();
    }

    private boolean isLosing(Point point) {
        return !point.getCell().isSafe();
    }

    private void takeSnapshot() {
        if (currentSnapshot == null || currentSnapshot.getSteps().size() > steps.size())
            currentSnapshot = new Snapshot(new ArrayList<>(steps), gameData.clone());
        else {
            currentSnapshot.setSteps(steps);
            currentSnapshot.setGameData(gameData);
        }
    }

    private void takeSnapshot(List<Point> steps, GameData gameData) {
        if (currentSnapshot == null)
            currentSnapshot = new Snapshot(steps, gameData);
        else {
            currentSnapshot.setSteps(steps);
            currentSnapshot.setGameData(gameData);
        }
    }

    private void doBacktracking(Point point) {
        if (isLosing(point)) return;
        if (steps.size() + 1 >= minStepsCount) return;

        if (costs[point.getX()][point.getY()] < steps.size()) return;

        steps.push(point);

        gameData.setPath(point.getX(), point.getY());

        var moves = moves(point);

        if (target.getCell().isKraken()) {
            if (moves.stream().anyMatch(p -> p.getCell().isKraken())) {
                takeSnapshot();

                minStepsCount = steps.size();

                gameData.unsetPath(point.getX(), point.getY());
                steps.pop();

                return; // We found Kraken!
            }
        }

        if (point.equals(target)) {
            takeSnapshot();

            minStepsCount = steps.size();

        } else {

            for (var p : moves)
                costs[p.getX()][p.getY()] = Math.min(costs[point.getX()][point.getY()] + 1, costs[p.getX()][p.getY()]);

            for (var p : moves) {
                doBacktracking(p);
            }
        }

        gameData.unsetPath(point.getX(), point.getY());
        steps.pop();
    }

    private Snapshot wrappedRun(Point start, Point target, GameData data) {
        if (start == target) {
            takeSnapshot(new ArrayList<>(), gameData.clone());
            var snapshotCopy = currentSnapshot;
            currentSnapshot = null;

            return snapshotCopy;
        }

        var tmpGameData = gameData.clone();
        gameData = data;

        cleanCosts();

        if (isLosing(start)) return null;
        this.target = target;
        costs[start.getX()][start.getY()] = 0;
        var moves = moves(start);

        for (var p : moves)
            costs[p.getX()][p.getY()] = Math.min(costs[start.getX()][start.getY()] + 1, costs[p.getX()][p.getY()]);

        gameData.setPath(start.getX(), start.getY());

        for (var p : moves)
            doBacktracking(p);

        gameData.unsetPath(start.getX(), start.getY());
        cleanCosts();

        // Force reset minStepsCount for other runs
        minStepsCount = Integer.MAX_VALUE;
        gameData = tmpGameData;

        var snapshotCopy = currentSnapshot;
        currentSnapshot = null;

        return snapshotCopy;
    }

    private void cleanCosts() {
        for (int i = 0; i < 9; i++)
            for (int j = 0; j < 9; j++)
                costs[i][j] = Integer.MAX_VALUE;
    }

    public Backtracking(GameData gameData) {
        this.gameData = gameData;

        cleanCosts();
    }

    public void run() {
        var initialGameData = gameData.clone();

        var firstRun = wrappedRun(gameData.getJackSparrow(), gameData.getTortuga(), initialGameData);
        Snapshot combinedRun = null;

        if (firstRun != null) {
            var tortugaStartData = firstRun.getGameData().clone();

            var secondRun = wrappedRun(gameData.getTortuga(), gameData.getKraken(), tortugaStartData);

            if (secondRun != null) {
                var krakenStartData = secondRun.getGameData().clone();
                krakenStartData.tryRemoveKraken();

                var nearKraken = secondRun.getSteps().get(secondRun.getSteps().size() - 1);

                var thirdRun = wrappedRun(nearKraken, gameData.getChest(), krakenStartData);

                if (thirdRun != null) {
                    var combinedList = new ArrayList<>(firstRun.getSteps());
                    combinedList.addAll(secondRun.getSteps());
                    combinedList.addAll(thirdRun.getSteps());
                    takeSnapshot(combinedList, thirdRun.getGameData());

                    combinedRun = currentSnapshot;
                    currentSnapshot = null;
                }
            }
        }

        var immediateRun = wrappedRun(gameData.getJackSparrow(), gameData.getChest(), initialGameData);

        var result = Stream.of(combinedRun, immediateRun)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(p -> p.getSteps().size()))
                .toList();

        if (!result.isEmpty())
            currentSnapshot = result.get(0);
    }

    public void setGameData(GameData gameData) {
        this.gameData = gameData;
    }

    public Snapshot getCurrentSnapshot() {
        return currentSnapshot;
    }
}

class AStar {
    private static class Node {
        Node parent;
        Point point;

        int g = 1;
        int f = Integer.MAX_VALUE;
        int h = Integer.MAX_VALUE;

        int length = 0;

        boolean checked = false;
        boolean added = false;

        Node(Node parent, Point point) {
            this.parent = parent;
            this.point = point;
        }

        void reset() {
            parent = null;
            f = Integer.MAX_VALUE;
            g = 1;
            h = Integer.MAX_VALUE;
            checked = false;
            added = false;
        }
    }

    private final Node[][] nodes = new Node[9][9];

    private final List<Node> checkedList = new LinkedList<>();
    private final List<Node> addedList = new LinkedList<>();

    private final int scenario;

    private final GameData gameData;

    private Point target;

    private Node currentNode;

    private boolean ended = false;
    private boolean failed = false;

    private void cleanNodes() {
        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++)
                nodes[x][y].reset();
    }

    private void updateHeuristics(Node node) {
        if (node.checked || node.added) return;

        node.g = node.length + 1;
        node.h = Math.max(
                Math.abs(node.point.getX() - target.getX()),
                Math.abs(node.point.getY() - target.getY())
        );
        node.f = node.g + node.h;
    }

    private boolean isLosing(Point point) {
        return gameData.getMatrix().getPoint(point.getX(), point.getY()).isEmpty() || !point.getCell().isSafe();
    }

    private void doAStar(Node node) {
        if (ended) return;

        node.checked = true;
        checkedList.add(node);
    }

    private void wrappedAStar(Point start, Point target) {
        this.target = target;
        cleanNodes();

        doAStar(nodes[start.getX()][start.getY()]);
    }

    public AStar(GameData gameData, int scenario) {
        this.gameData = gameData;
        this.scenario = scenario;

        for (int i = 0; i < 9; i++)
            nodes[i] = new Node[9];

        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++)
                if (gameData.getMatrix().getPoint(x, y).isPresent())
                    nodes[x][y] = new Node(null, gameData.getMatrix().getPoint(x, y).get());
    }
}

class InputHelper {
    private static final Path INPUT = Path.of("input.txt");
    private static List<String> inputData;
    private static List<Point> points;
    private static int scenario;

    private static final Matrix MATRIX = new Matrix();

    private static void parseInput() throws IOException {
        if (inputData.size() < 2)
            throw new IOException("Number of input console lines is < 2");

        readPoints();
        readScenario();
    }

    static void tryInitStreams() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Choose the input source:\n[1] File\n[2] Console\nType number:");

            switch (Integer.parseInt(reader.readLine())) {
                case 1 -> inputData = Files.readAllLines(INPUT);
                case 2 -> {
                    System.out.println("Enter the data:");
                    inputData = Stream.of(reader.readLine(), reader.readLine()).toList();
                }
                default -> throw new IOException("Invalid number!");
            }

            parseInput();
        } catch (NumberFormatException e) {
            throw new IOException("Can't parse the given string to an integer");
        }
    }

    private static void readPoints() throws IOException {
        points = Arrays.stream(inputData.get(0).split("\\s+"))
                .filter(x -> x.matches("\\[[0-8],[0-8]]"))
                .map(x -> {
                    var split = x.replaceAll("[\\[\\]]+", "").split(",");
                    return MATRIX.getPoint(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                })
                .map(p -> p.orElse(null))
                .filter(Objects::nonNull)
                .toList();

        if (points.size() != 6)
            throw new IOException("Invalid positions");
    }

    private static void readScenario() throws IOException {
        scenario = Integer.parseInt(inputData.get(1));
        if (scenario != 1 && scenario != 2)
            throw new IOException("Invalid scenario");
    }

    static List<Point> getPoints() {
        return points;
    }

    static int getScenario() {
        return scenario;
    }
}

class OutputHelper {

    static void printResult(Path outputPath, Snapshot snapshot, long millis) throws IOException {
        if (snapshot == null) Files.writeString(outputPath, "Lose\n");
        else Files.writeString(outputPath, String.format("Win\n%s\n%d ms\n", snapshot, millis));
    }

    static void printResult(Snapshot snapshot, long millis) {
        if (snapshot == null) System.out.println("Lose");
        else System.out.printf("Win\n%s\n%d ms\n", snapshot, millis);
    }
}

record TestResult(
        List<Long> allTimeResults,
        long max, long min,
        double mean, double median,
        double variance, double sDeviation
) {
    @Override
    public String toString() {
        return "max = " + max + "\n" +
                "min = " + min + "\n" +
                "mean = " + mean + "\n" +
                "median = " + median + "\n" +
                "variance = " + variance + "\n" +
                "sDeviation = " + sDeviation;
    }
}

class TestHelper {
    public static final int BACKTRACKING = 0;
    public static final int A_STAR = 1;

    private static double mean(List<Long> results) {
        return results.stream().mapToDouble(Long::doubleValue).sum() / results.size();
    }

    private static double median(List<Long> results) {
        results.sort(Comparator.comparingInt(Long::intValue));

        return results.size() % 2 == 0
                ? (results.get(results.size() / 2) + results.get(results.size() / 2)) / 2d
                : results.get(results.size() / 2) / 2d;
    }

    private static double variance(List<Long> results) {
        var mean = mean(results);
        return results.stream().mapToDouble(x -> Math.pow(x - mean, 2)).sum() / (results.size() - 1);
    }

    private static double sDeviation(List<Long> results) {
        return Math.sqrt(variance(results));
    }

    private static long min(List<Long> results) {
        var result = results.stream().mapToLong(x -> x).min();
        if (result.isEmpty()) throw new RuntimeException("Illegal result of min()");
        return result.getAsLong();
    }

    private static long max(List<Long> results) {
        var result = results.stream().mapToLong(x -> x).max();
        if (result.isEmpty()) throw new RuntimeException("Illegal result of min()");
        return result.getAsLong();
    }

    public static TestResult run(int repeatNumber, int algorithm) {
        var results = new ArrayList<Long>(repeatNumber);
        long startMillis, endMillis;

        switch (algorithm) {
            case BACKTRACKING -> {
                var backtracking = new Backtracking(null);
                for (int i = 0; i < repeatNumber; i++) {
                    if (i % 100 == 0) System.out.printf("Running test No. %d\n", i);

                    backtracking.setGameData(new GameData());

                    startMillis = System.currentTimeMillis();
                    backtracking.run();
                    endMillis = System.currentTimeMillis();

                    results.add(endMillis - startMillis);
                }
            }

            case A_STAR -> throw new IllegalArgumentException("Not working :(");
            default -> throw new IllegalArgumentException("Illegal algorithm ID");
        }

        return new TestResult(
                results,
                max(results), min(results),
                mean(results), median(results),
                variance(results), sDeviation(results)
        );
    }
}