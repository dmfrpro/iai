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
            InputHelper.tryInitStreams();

            var point = InputHelper.getPoints();
            var scenario = InputHelper.getScenario();

            var game = new GameData(point);

            var backtracking = new Backtracking(game);
            backtracking.run();
            OutputHelper.printResult(backtracking.getCurrentSnapshot());

        } catch (Exception e) {
            System.out.println(e.getMessage());
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
        return this instanceof ObjectCell || this == AirCell.FREE;
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
    private BPoint[][] matrix = new BPoint[9][9];

    public Matrix() {
        for (int y = 0; y < 9; y++)
            matrix[y] = new BPoint[9];

        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++)
                matrix[x][y] = new BPoint(x, y);
    }

    public Optional<BPoint> getPoint(int x, int y) {
        if (y < 0 || y >= 9 || x < 0 || x >= 9) return Optional.empty();
        return Optional.of(matrix[x][y]);
    }

    public Stream<BPoint> neighbors(int x, int y) {
        return Stream.of(
                        getPoint(x, y + 1), getPoint(x, y - 1),
                        getPoint(x + 1, y), getPoint(x - 1, y)
                )
                .filter(Optional::isPresent)
                .map(p -> p.orElse(null));
    }

    public Stream<BPoint> corners(int x, int y) {
        return Stream.of(
                        getPoint(x + 1, y + 1), getPoint(x + 1, y - 1),
                        getPoint(x - 1, y + 1), getPoint(x - 1, y - 1)
                )
                .filter(Optional::isPresent)
                .map(p -> p.orElse(null));
    }

    public Stream<BPoint> secondNeighbors(int x, int y) {
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

            clone.matrix = new BPoint[9][9];

            for (int i = 0; i < 9; i++)
                clone.matrix[i] = new BPoint[9];

            for (int y = 0; y < 9; y++)
                for (int x = 0; x < 9; x++)
                    clone.matrix[x][y] = matrix[x][y].clone();

            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}

abstract class Point implements Cloneable {
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
    public BPoint clone() {
        try {
            var clone = (BPoint) super.clone();
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

class BPoint extends Point {

    private int cost = Integer.MAX_VALUE;

    public BPoint(int x, int y) {
        super(x, y);
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public int distanceSquared(BPoint point) {
        return (int) (Math.pow(point.getX() - getX(), 2) + Math.pow(point.getY() - getY(), 2));
    }
}

class GameData implements Cloneable {

    private static final Random RANDOM = new Random();
    private Matrix matrix = new Matrix();

    private BPoint jackSparrow;
    private BPoint davyJones;
    private BPoint kraken;
    private BPoint rock;
    private BPoint chest;
    private BPoint tortuga;

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

    private BPoint getRandomPoint() {
        var optPos = matrix.getPoint(RANDOM.nextInt(0, 9), RANDOM.nextInt(0, 9));
        if (optPos.isEmpty() || (optPos.get().getX() == 0 && optPos.get().getY() != 0))
            return getRandomPoint();
        return optPos.get();
    }

    public GameData(List<BPoint> points) {
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

    public BPoint getJackSparrow() {
        return jackSparrow;
    }

    public BPoint getDavyJones() {
        return davyJones;
    }

    public BPoint getKraken() {
        return kraken;
    }

    public BPoint getRock() {
        return rock;
    }

    public BPoint getChest() {
        return chest;
    }

    public BPoint getTortuga() {
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

    private List<BPoint> steps;
    private GameData gameData;
    private long timeMillis;

    public Snapshot(List<BPoint> steps, GameData gameData, long timeMillis) {
        this.steps = steps;
        this.gameData = gameData;
        this.timeMillis = timeMillis;
    }

    public List<BPoint> getSteps() {
        return steps;
    }

    public void setSteps(List<BPoint> steps) {
        this.steps = steps;
    }

    public GameData getGameData() {
        return gameData;
    }

    public void setGameData(GameData gameData) {
        this.gameData = gameData;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public void setTimeMillis(long timeMillis) {
        this.timeMillis = timeMillis;
    }

    @Override
    public String toString() {
        var shortestPathString = steps.stream()
                .map(BPoint::toString)
                .collect(Collectors.joining(" "));

        return String.format("%d\n%s\n%s\n%d ms\n", steps.size(), shortestPathString, gameData.getMatrix(), timeMillis);
    }
}

class Backtracking {
    private GameData gameData;

    private int[][] costs = new int[9][9];
    private Snapshot currentSnapshot;

    private final Stack<BPoint> steps = new Stack<>();

    private BPoint target;

    private int minStepsCount = Integer.MAX_VALUE;

    private List<BPoint> moves(BPoint point) {
        return Stream.concat(
                        gameData.getMatrix().corners(point.getX(), point.getY()),
                        gameData.getMatrix().neighbors(point.getX(), point.getY())
                )
                .filter(p -> p.getCell().isKraken() || p.getCell().isSafe())
                .filter(p -> p.getCost() >= steps.size())
                .sorted((p1, p2) -> p1.distanceSquared(target) - p2.distanceSquared(target))
                .toList();
    }

    private boolean isLosing(BPoint point) {
        return point.getCell().isDangerous();
    }

    private void takeSnapshot() {
        if (currentSnapshot == null || currentSnapshot.getSteps().size() > steps.size())
            currentSnapshot = new Snapshot(new ArrayList<>(steps), gameData.clone(), -1);
        else {
            currentSnapshot.setSteps(steps);
            currentSnapshot.setGameData(gameData);
        }
    }

    private void takeSnapshot(List<BPoint> steps, GameData gameData) {
        if (currentSnapshot == null)
            currentSnapshot = new Snapshot(steps, gameData, -1);
        else {
            currentSnapshot.setSteps(steps);
            currentSnapshot.setGameData(gameData);
        }
    }

    private void doBacktracking(BPoint point) {
        if (isLosing(point)) return;
        if (steps.size() + 1 >= minStepsCount) return;

        if (costs[point.getX()][point.getY()] < steps.size()) return;

        if (point.getX() == 8 && point.getY() == 6) {
            int a = 0;
        }

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

    private Snapshot wrappedRun(BPoint start, BPoint target, GameData data) {
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
        start.setCost(Integer.MAX_VALUE);

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
        long startMillis = System.currentTimeMillis();

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

        if (!result.isEmpty()) {
            currentSnapshot = result.get(0);

            // Force update time
            currentSnapshot.setTimeMillis(System.currentTimeMillis() - startMillis);
        }
    }

    public Snapshot getCurrentSnapshot() {
        return currentSnapshot;
    }
}

class InputHelper {
    private static final Path INPUT = Path.of("input.txt");
    private static List<String> inputData;
    private static List<BPoint> points;
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

    static List<BPoint> getPoints() {
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