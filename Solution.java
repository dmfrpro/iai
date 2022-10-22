import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Solution {

    private static final Path INPUT = Path.of("input.txt");
    private static final Path BACKTRACKING_OUT = Path.of("outputBacktracking.txt");
    private static final Path A_STAR_OUT = Path.of("outputAStar.txt");

    public static void main(String[] args) {
        try {
            InputHelper.tryInitStreams(INPUT);

            var point = InputHelper.getPoints();
            var scenario = InputHelper.getScenario();

            var game = new GameData(point);
            System.out.println(game);

            var backtracking = new Backtracking(game);
            backtracking.run();

            var snapshot = backtracking.getCurrentSnapshot();
            OutputHelper.printResult(snapshot);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

interface Cell {
    default boolean hasType(Class<?> cls) {
        return this.getClass() == cls;
    }

    default boolean isDangerous() {
        return this instanceof EnemyCell || this instanceof KrakenPairCell || this == AirCell.PERCEPTION;
    }

    default boolean isSafe() {
        return this instanceof ObjectCell || (this == AirCell.PATH || this == AirCell.FREE);
    }

    default boolean isPerception() {
        return this == AirCell.PERCEPTION;
    }

    default boolean isKraken() {
        return this == KrakenPairCell.KRAKEN || this == KrakenPairCell.KRAKEN_ROCK;
    }

    default boolean isRock() {
        return this == KrakenPairCell.ROCK;
    }

    default boolean isFree() {
        return this.hasType(AirCell.class);
    }
}

enum AirCell implements Cell {
    PERCEPTION("#"),
    FREE("_"),
    PATH("*");

    private final String name;

    AirCell(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}

enum EnemyCell implements Cell {
    DAVY_JONES("D");

    private final String name;

    EnemyCell(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}

enum KrakenPairCell implements Cell {
    KRAKEN("k"),
    ROCK("R"),
    KRAKEN_ROCK("K");

    private final String name;

    KrakenPairCell(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}

enum ObjectCell implements Cell {
    TORTUGA("T"),
    CHEST("C");

    private final String name;

    ObjectCell(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
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
                builder.append(matrix[y][x].getCell()).append(" ");
            builder.append(matrix[y][8].getCell()).append("\n");
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

    private Cell cell;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
        this.cell = AirCell.FREE;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Cell getCell() {
        return cell;
    }

    public void setCell(Cell cell) {
        this.cell = cell;
    }

    public int distanceSquared(Point point) {
        return (int) (Math.pow(point.x - x, 2) + Math.pow(point.y - y, 2));
    }

    @Override
    public String toString() {
        return String.format("[%d,%d]", x, y);
    }

    @Override
    public Point clone() {
        try {
            var clone = (Point) super.clone();
            clone.cell = cell;
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

    private boolean trySetAir(Cell cell, int x, int y) {
        return trySetEnemy(cell, x, y);
    }

    private boolean trySetJackSparrow(int x, int y) {
        if (matrix.getPoint(x, y).isPresent()) {
            var cell = matrix.getPoint(x, y).get().getCell();
            if (cell.isFree()) {
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

    public boolean trySetPath(int x, int y) {
        if (matrix.getPoint(x, y).isPresent()) {

            var point = matrix.getPoint(x, y).get();
            var matrixCell = point.getCell();

            if (matrixCell.hasType(AirCell.class) || matrixCell.hasType(ObjectCell.class)) {
                point.setCell(AirCell.PATH);
                return true;
            }
        }

        return false;
    }

    public boolean tryUnsetPath(Cell newCell, int x, int y) {
        return trySetAir(newCell, x, y);
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

record Snapshot(List<Point> steps, GameData gameData, long timeMillis) {
    @Override
    public String toString() {
        var shortestPathString = steps.stream()
                .map(Point::toString)
                .collect(Collectors.joining(" "));

        return String.format("%d\n%s\n%s\n%d ms\n", steps.size(), shortestPathString, gameData.getMatrix(), timeMillis);
    }
}

class Backtracking {
    private GameData gameData;
    private Snapshot currentSnapshot;

    private final Stack<Point> steps = new Stack<>();

    private Point target;

    private int minStepsCount = Integer.MAX_VALUE;

    private int minPrevDistance = Integer.MAX_VALUE;
    private int potential = 0;
    private int potentialLimit = 9;

    private List<Point> scenarioMoves(Point point) {
        return Stream.concat(
                        gameData.getMatrix().corners(point.getX(), point.getY()),
                        gameData.getMatrix().neighbors(point.getX(), point.getY())
                )
                .filter(p -> !steps.contains(p))
                .sorted((p1, p2) -> p1.distanceSquared(target) - p2.distanceSquared(target))
                .toList();
    }

    private boolean isLosing(Point point) {
        return point.getCell().isDangerous();
    }

    private void overrideSnapshot() {
        if (currentSnapshot == null || currentSnapshot.steps().size() > steps.size())
            currentSnapshot = new Snapshot(new ArrayList<>(steps), gameData.clone(), 0);
    }

    private void overrideSnapshot(List<Point> steps, GameData gameData, long timeMillis) {
        currentSnapshot = new Snapshot(steps, gameData, timeMillis);
    }

    private void doBacktracking(Point point) {
        if (isLosing(point)) return;
        if (steps.size() + 1 >= minStepsCount) return;
        if (potential > potentialLimit) return;

        steps.push(point);

        var cellCopy = point.getCell();
        gameData.trySetPath(point.getX(), point.getY());

        var currentDistance = point.distanceSquared(target);

        potential = currentDistance < minPrevDistance ? 0 : potential + 1;

        var moves = scenarioMoves(point);

        if (target.getCell().isKraken()) {
            if (moves.stream().anyMatch(p -> p.getCell().isKraken())) {
                overrideSnapshot();

                gameData.tryUnsetPath(cellCopy, point.getX(), point.getY());
                steps.pop();

                return; // We found Kraken!
            }
        }

        if (point.equals(target)) {
            overrideSnapshot();
            minStepsCount = steps.size();
        } else {
            minPrevDistance = currentDistance;

            for (var p : moves)
                doBacktracking(p);
        }

        gameData.tryUnsetPath(cellCopy, point.getX(), point.getY());
        steps.pop();
    }

    private void doRun(Point start, Point target) {
        if (isLosing(start)) return;

        this.target = target;

        var cellCopy = start.getCell();
        gameData.trySetPath(start.getX(), start.getY());

        for (var p : scenarioMoves(start))
            doBacktracking(p);

        gameData.tryUnsetPath(cellCopy, start.getX(), start.getY());

        // Force reset minStepsCount for other runs
        minStepsCount = Integer.MAX_VALUE;
        potential = 0;
    }

    public Backtracking(GameData gameData) {
        this.gameData = gameData;
    }

    public Snapshot wrappedRun(Point start, Point target, GameData data) {
        var tmpGameData = gameData.clone();
        gameData = data;

        doRun(start, target);

        gameData = tmpGameData;

        var snapshotCopy = currentSnapshot;
        currentSnapshot = null;

        return snapshotCopy;
    }

    public void run() {
        long startMillis = System.currentTimeMillis();

        var initialGameData = gameData.clone();


        Snapshot firstRun = wrappedRun(gameData.getJackSparrow(), gameData.getTortuga(), initialGameData);
        Snapshot secondRun = null;
        Snapshot thirdRun = null;

        Snapshot combinedRun = null;

        Snapshot immediateRun = null;

        potentialLimit = 6;

        if (firstRun != null) {
            var tortugaStartData = firstRun.gameData().clone();

            secondRun = wrappedRun(gameData.getTortuga(), gameData.getKraken(), tortugaStartData);

            if (secondRun != null) {
                var krakenStartData = secondRun.gameData().clone();
                krakenStartData.tryRemoveKraken();

                var nearKraken = secondRun.steps().get(secondRun.steps().size() - 1);

                thirdRun = wrappedRun(nearKraken, gameData.getChest(), krakenStartData);

                if (thirdRun != null) {
                    var combinedList = new ArrayList<>(firstRun.steps());
                    combinedList.addAll(secondRun.steps());
                    combinedList.addAll(thirdRun.steps());
                    overrideSnapshot(combinedList, thirdRun.gameData(), 0);

                    combinedRun = currentSnapshot;
                    currentSnapshot = null;
                }
            }
        }

        potentialLimit = 9;

        immediateRun = wrappedRun(gameData.getJackSparrow(), gameData.getChest(), initialGameData);

        var result = Stream.of(combinedRun, immediateRun)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(p -> p.steps().size()))
                .toList();

        if (!result.isEmpty()) {
            currentSnapshot = result.get(0);

            // Force update time
            overrideSnapshot(
                    currentSnapshot.steps(),
                    currentSnapshot.gameData(),
                    System.currentTimeMillis() - startMillis
            );
        }
    }

    public Snapshot getCurrentSnapshot() {
        return currentSnapshot;
    }
}

class InputHelper {
    private static List<String> inputData;
    private static List<Point> points;
    private static int scenario;

    private static final Matrix MATRIX = new Matrix();

    static void tryInitStreams(Path inputPath) {
        try {
            inputData = Files.readAllLines(inputPath);

            if (inputData.size() < 2)
                throw new IOException("Number of input file lines is < 2");

            readPoints();
            readScenario();
        } catch (IOException ignored) {
            try (var console = new BufferedReader(new InputStreamReader(System.in))) {
                System.out.println("Enter the input data:");
                inputData = Stream.of(console.readLine(), console.readLine()).toList();

                if (inputData.size() < 2)
                    throw new IOException("Number of input console lines is < 2");

                readPoints();
                readScenario();
            } catch (IOException e) {
                System.out.printf(
                        "Please either update the data in %s or type correct input in the console\n",
                        inputPath
                );

//                tryInitStreams(inputPath);
            }
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

    static void printResult(Path outputPath, Snapshot snapshot) throws IOException {
        if (snapshot == null) Files.writeString(outputPath, "Lose\n");
        else Files.writeString(outputPath, String.format("Win\n%s", snapshot));
    }

    static void printResult(Snapshot snapshot) {
        if (snapshot == null) System.out.println("Lose");
        else System.out.printf("Win\n%s", snapshot);
    }
}