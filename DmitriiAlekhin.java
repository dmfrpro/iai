import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Assignment 1 main class.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 */
public class DmitriiAlekhin {
    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                InputHelper.tryInitAndParse();

                var points = InputHelper.getPoints();
                var scenario = InputHelper.getScenario();
                var game = new GameData(points);
                var backtracking = new Backtracking(game, scenario);

                var startMillis = System.currentTimeMillis();
                OutputHelper.printResult(
                        OutputHelper.BACKTRACKING_OUT,
                        backtracking.run(),
                        System.currentTimeMillis() - startMillis
                );

                var aStar = new AStar(new GameData(points), scenario);

                startMillis = System.currentTimeMillis();
                OutputHelper.printResult(
                        OutputHelper.A_STAR_OUT,
                        aStar.run(),
                        System.currentTimeMillis() - startMillis
                );

            } else if (args[0].equals("-t") || args[0].equals("--test"))
                TestHelper.run(1000);
        } catch (Exception e) {
            System.out.println("Exception occurred!");
            System.out.println("Message: " + e.getMessage());
            System.out.println("Stacktrace: ");
            e.printStackTrace();
        }
    }
}

/**
 * Cell interface containing useful default methods for any derived cell type.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 */
interface Cell {
    /**
     * Compares cell's class with the given class. Used to determine
     * if the cell belongs to the group specified by class.
     *
     * @param cls Cell enum
     * @return true if the class is identical to cls
     */
    default boolean isTypeOf(Class<?> cls) {
        return this.getClass() == cls;
    }

    /**
     * Determines if the cell is safe to step on.
     *
     * @return true if the cell is safe to step on, false otherwise
     */
    default boolean isSafe() {
        return this instanceof ObjectCell || this == AirCell.FREE;
    }

    /**
     * Determines if the cell is free to step on / spawn.
     *
     * @return true if the cell is free to step on / spawn, false otherwise
     */
    default boolean isFree() {
        return this.isTypeOf(AirCell.class);
    }

    default boolean isKraken() {
        return this == KrakenEnemiesFamilyCell.KRAKEN_ROCK || this == KrakenEnemiesFamilyCell.KRAKEN;
    }
}

/**
 * Air cells (i.e. not containing objects like enemies, tortuga, or chest).
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see Cell
 */
enum AirCell implements Cell {
    PERCEPTION,
    FREE
}

/**
 * Enemy cells.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see Cell
 */
enum EnemyCell implements Cell {
    DAVY_JONES
}

/**
 * Kraken family cells. They are also considered as removable enemy cells excluding Rock :).
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see Cell
 */
enum KrakenEnemiesFamilyCell implements Cell {
    KRAKEN,
    ROCK,
    KRAKEN_ROCK
}

/**
 * Other object cells. Jack Sparrow can visit them safely.
 * Includes the exit - chest.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see Cell
 */
enum ObjectCell implements Cell {
    TORTUGA,
    CHEST
}

/**
 * 9x9 matrix representation.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see GameData
 * @see Point
 * @see Cloneable
 */
class Matrix implements Cloneable {

    /**
     * 9x9 Point matrix
     *
     * @see Point
     */
    private Point[][] matrix = new Point[9][9];

    /**
     * Initialization of an empty matrix.
     */
    public Matrix() {
        for (int y = 0; y < 9; y++)
            matrix[y] = new Point[9];

        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++)
                matrix[x][y] = new Point(x, y);
    }

    /**
     * Returns the point by the coordinates if it exists.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return point by the coordinates if it exists, <code>Optional.empty()</code> otherwise.
     */
    public Optional<Point> getPoint(int x, int y) {
        if (y < 0 || y >= 9 || x < 0 || x >= 9) return Optional.empty();
        return Optional.of(matrix[x][y]);
    }

    /**
     * Returns available (size=[2, 4]) Von-Neumann neighbors of the point by its coordinates.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return opened stream of available Von-Neumann neighbors of the point.
     */
    public Stream<Point> neighbors(int x, int y) {
        return Stream.of(
                        getPoint(x, y + 1), getPoint(x, y - 1),
                        getPoint(x + 1, y), getPoint(x - 1, y)
                )
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * Returns available (size=[1, 4]) diagonal neighbors of the point by its coordinates.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return opened stream of available diagonal neighbors of the point.
     */
    public Stream<Point> corners(int x, int y) {
        return Stream.of(
                        getPoint(x + 1, y + 1), getPoint(x + 1, y - 1),
                        getPoint(x - 1, y + 1), getPoint(x - 1, y - 1)
                )
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * Returns available (size=[2, 4]) 2nd-order Von-neumann neighbors of the point by its coordinates.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return opened stream of available 2nd-order Von-neumann neighbors of the point.
     * @see AStar
     */
    public Stream<Point> secondNeighbors(int x, int y) {
        return Stream.of(
                        getPoint(x, y + 2), getPoint(x, y - 2),
                        getPoint(x + 2, y), getPoint(x - 2, y)
                )
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * First scenario moves.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return stream of neighbors + corners of the given point.
     */
    public Stream<Point> firstScenario(int x, int y) {
        return Stream.concat(neighbors(x, y), corners(x, y));
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

/**
 * Point representation.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see Matrix
 */
class Point implements Cloneable {

    /**
     * X-coordinate.
     */
    private final int x;

    /**
     * Y-coordinate.
     */
    private final int y;

    /**
     * Flag indicating if this point is included into the path.
     */
    private boolean path = false;

    /**
     * Point's cell type. Free for default.
     */
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
            clone.cell = cell;
            clone.path = path;
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

/**
 * Sea map generator class.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see Backtracking
 * @see AStar
 */
class GameData implements Cloneable {

    /**
     * Random numbers generator.
     */
    private static final Random RANDOM = new Random();

    /**
     * 9x9 points matrix.
     */
    private Matrix matrix = new Matrix();

    /**
     * Jack Sparrow (Main hero) initial spawn coordinates.
     */
    private Point jackSparrow;

    /**
     * Davy Jones (Enemy) initial spawn coordinates.
     */
    private Point davyJones;

    /**
     * Kraken (Removable Enemy) initial spawn coordinates.
     */
    private Point kraken;

    /**
     * Rock (Enemy) initial spawn coordinates.
     */
    private Point rock;

    /**
     * Chest (Exit) initial spawn coordinates.
     */
    private Point chest;

    /**
     * Tortuga (Island, grants ability to kill kraken after visiting) initial spawn coordinates.
     */
    private Point tortuga;

    /**
     * Tries to spawn an enemy by the given coordinates.
     *
     * @param cell enemy cell.
     * @param x    x-coordinate.
     * @param y    y-coordinate.
     * @return true if the spawn result is success, false otherwise.
     * @see EnemyCell
     */
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

    /**
     * Tries to spawn a safe object by the given coordinates.
     *
     * @param cell safe object cell.
     * @param x    x-coordinate.
     * @param y    y-coordinate.
     * @return true if the spawn result is success, false otherwise.
     * @see ObjectCell
     */
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

    /**
     * Tries to spawn an air by the given coordinates. Ignores unsuccessful tries.
     *
     * @param cell air cell.
     * @param x    x-coordinate.
     * @param y    y-coordinate.
     * @see AirCell
     */
    private void trySetAir(Cell cell, int x, int y) {
        trySetEnemy(cell, x, y);
    }

    /**
     * Tries to spawn Jack Sparrow by the given coordinates.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return true if the spawn result is success, false otherwise.
     */
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

    /**
     * Tries to spawn Davy Jones by the given coordinates.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return true if the spawn result is success, false otherwise.
     */
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

    /**
     * Tries to spawn Davy Jones by the given coordinates.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return true if the spawn result is success, false otherwise.
     */
    public boolean trySetKraken(int x, int y) {
        if (matrix.getPoint(x, y).isPresent()) {

            var point = matrix.getPoint(x, y).get();
            var matrixCell = point.getCell();

            var newCell = matrixCell == KrakenEnemiesFamilyCell.ROCK ||
                    matrixCell == KrakenEnemiesFamilyCell.KRAKEN_ROCK
                    ? KrakenEnemiesFamilyCell.KRAKEN_ROCK
                    : KrakenEnemiesFamilyCell.KRAKEN;

            if (matrixCell.isTypeOf(KrakenEnemiesFamilyCell.class) || matrixCell.isFree()) {
                point.setCell(newCell);
                matrix.neighbors(x, y).forEach(c -> trySetAir(AirCell.PERCEPTION, c.getX(), c.getY()));
                kraken = matrix.getPoint(x, y).get();

                return true;
            }
        }

        return false;
    }

    /**
     * Tries to remove Kraken from the map.
     * Ignores unsuccessful tries (i.e. Kraken is already removed / not spawned).
     */
    public void tryRemoveKraken() {
        if (kraken == null) return;

        var newCell = kraken.getCell() == KrakenEnemiesFamilyCell.KRAKEN_ROCK
                ? KrakenEnemiesFamilyCell.ROCK
                : AirCell.FREE;

        kraken.setCell(newCell);

        var optPoint = matrix.getPoint(kraken.getX(), kraken.getY());
        optPoint.ifPresent(point -> point.setCell(newCell));

        matrix.neighbors(kraken.getX(), kraken.getY()).forEach(c -> trySetAir(AirCell.FREE, c.getX(), c.getY()));

        // Force update DavyJones perception zones in order to restore some of them
        // After Kraken removal
        trySetDavyJones(davyJones.getX(), davyJones.getY());

    }

    /**
     * Tries to spawn Rock by the given coordinates.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return true if the spawn result is success, false otherwise.
     */
    private boolean trySetRock(int x, int y) {
        if (matrix.getPoint(x, y).isPresent()) {

            var point = matrix.getPoint(x, y).get();
            var matrixCell = point.getCell();

            var newCell = matrixCell == KrakenEnemiesFamilyCell.KRAKEN
                    ? KrakenEnemiesFamilyCell.KRAKEN_ROCK
                    : KrakenEnemiesFamilyCell.ROCK;

            if (matrixCell.isTypeOf(KrakenEnemiesFamilyCell.class) || matrixCell.isFree()) {
                point.setCell(newCell);
                rock = point;

                return true;
            }
        }

        return false;
    }

    /**
     * Tries to spawn chest by the given coordinates.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return true if the spawn result is success, false otherwise.
     */
    private boolean trySetChest(int x, int y) {
        if (trySetObject(ObjectCell.CHEST, x, y)) {

            if (matrix.getPoint(x, y).isPresent()) {
                chest = matrix.getPoint(x, y).get();
                return true;
            }
        }

        return false;
    }

    /**
     * Tries to spawn Tortuga by the given coordinates.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return true if the spawn result is success, false otherwise.
     */
    private boolean trySetTortuga(int x, int y) {
        if (trySetObject(ObjectCell.TORTUGA, x, y)) {

            if (matrix.getPoint(x, y).isPresent()) {
                tortuga = matrix.getPoint(x, y).get();
                return true;
            }
        }

        return false;
    }

    /**
     * Tries to mark the point as "included to the path" by the given coordinates.
     * Ignores unsuccessful tries
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     */
    public void setPath(int x, int y) {
        matrix.getPoint(x, y).ifPresent(p -> p.setPath(true));
    }

    /**
     * Generates a random integer in the range [0, 8].
     *
     * @return random integer in the range [0, 8].
     */
    private Point getRandomPoint() {
        var optPos = matrix.getPoint(RANDOM.nextInt(0, 9), RANDOM.nextInt(0, 9));
        if (optPos.isEmpty() || (optPos.get().getX() == 0 && optPos.get().getY() != 0))
            return getRandomPoint();
        return optPos.get();
    }

    /**
     * Generates a sea map with respect to the given points.
     *
     * @param points Parsed coordinates from the input.
     * @throws IllegalArgumentException if any point is incorrect with respect to the game rules.
     */
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

    /**
     * Generates a sea map with random valid coordinates. Jack Sparrow is always spawned
     * at (0, 0) point.
     */
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

    public Point getKraken() {
        return kraken;
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

/**
 * Stores the most successful (during the algorithm execution, not always)
 * result of algorithm's run from start to target. Nullable.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see Backtracking
 * @see AStar
 * @see OutputHelper
 */
class Snapshot {

    /**
     * Shortest path points from start to target.
     */
    private List<Point> steps;

    /**
     * Start point. Included in the matrix rendering, but not in the steps.
     */
    private Point start;

    /**
     * GameData copy.
     */
    private final GameData gameData;

    public Snapshot(List<Point> steps, GameData gameData, Point start) {
        this.steps = steps;
        this.gameData = gameData;
        this.start = start;

        // Force remove start point from the steps
        steps.remove(start);
    }

    public List<Point> getSteps() {
        return steps;
    }

    public void setSteps(List<Point> steps) {
        this.steps = steps;
        this.steps.remove(start);
    }

    public void setStart(Point start) {
        this.start = start;
        steps.remove(this.start);
    }

    @Override
    public String toString() {
        var shortestPathString = steps.stream()
                .map(Point::toString)
                .collect(Collectors.joining(" "));

        // Include start to the matrix rendering, but not in steps.
        gameData.setPath(start.getX(), start.getY());

        steps.forEach(s -> gameData.setPath(s.getX(), s.getY()));
        return String.format("%d\n%s\n%s", steps.size(), shortestPathString, gameData.getMatrix());
    }
}

/**
 * Common logic for searching algorithms with respect to game rules.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see Backtracking
 * @see AStar
 * @see TestHelper
 */
abstract class SearchingAlgorithm {
    /**
     * Current game data during the execution of a partial run from start to target.
     */
    protected GameData gameData;

    /**
     * Game scenario
     */
    protected final int scenario;

    /**
     * Stores the current snapshot during the execution of a partial run from start to target.
     */
    protected Snapshot currentSnapshot;

    /**
     * Stores the current path steps during the execution of a partial run from start to target.
     */
    protected final Stack<Point> steps = new Stack<>();

    /**
     * Start point (Either Jack Sparrow - immediate run, of Tortuga - Tortuga + Chest run).
     */
    protected Point start;

    /**
     * Target point (Tortuga + Chest run or just Chest run)
     */
    protected Point target;

    /**
     * Stores the minimum steps count over a run from start to target.
     */
    protected int minStepsCount = Integer.MAX_VALUE;

    /**
     * Indicates if Jack Sparrow visited Tortuga.
     */
    protected boolean visitedTortuga = false;

    private Snapshot exchangeSnapshot() {
        var snapshotCopy = currentSnapshot;
        currentSnapshot = null;
        return snapshotCopy;
    }

    /**
     * Wraps partial runs with checking for initial conditions checking.
     * Also wraps substitutions of <code>start</code> and <code>target</code>
     * argument points.
     *
     * @param start  start point.
     * @param target target point.
     * @return resulting nullable snapshot.
     */
    private Snapshot wrappedRun(Point start, Point target) {
        if (start == target) {
            takeSnapshot(new ArrayList<>(), start);
            return exchangeSnapshot();
        }

        if (isLosing(start)) return exchangeSnapshot();

        var startCopy = this.start;
        var targetCopy = this.target;

        this.start = start;
        this.target = target;

        partialRun();

        this.start = startCopy;
        this.target = targetCopy;

        return exchangeSnapshot();
    }

    /**
     * Indicates if the point is dangerous.
     *
     * @param point current point.
     * @return true if the point is dangerous, false otherwise.
     */
    protected boolean isLosing(Point point) {
        return !point.getCell().isSafe();
    }

    /**
     * Takes the snapshot.
     */
    protected void takeSnapshot() {
        if (currentSnapshot == null || currentSnapshot.getSteps().size() > steps.size())
            currentSnapshot = new Snapshot(new ArrayList<>(steps), gameData, start);
        else
            currentSnapshot.setSteps(new ArrayList<>(steps));
    }

    /**
     * Takes the snapshot with the custom data.
     *
     * @param steps custom steps.
     */
    protected void takeSnapshot(List<Point> steps, Point start) {
        if (currentSnapshot == null) {
            currentSnapshot = new Snapshot(steps, gameData, start);
        } else {
            currentSnapshot.setSteps(steps);
            currentSnapshot.setStart(start);
            currentSnapshot.getSteps().remove(start); // Force remove start point from the steps
        }
    }

    public SearchingAlgorithm(GameData gameData, int scenario) {
        this.gameData = gameData;
        this.scenario = scenario;

        // Default values
        start = gameData.getJackSparrow();
        target = gameData.getChest();
    }

    /**
     * Performs run from <code>start</code> and <code>target</code> points.
     */
    protected abstract void partialRun();

    /**
     * Preforms 2 complete runs and chooses the best one.
     * <ol>
     *     <li>start->tortuga + tortuga->best_kraken + kraken->chest</li>
     *     <li>start->chest without tortuga</li>
     * </ol>
     * <p>
     * Best_kraken run is the best run from Tortuga to the one of Kraken's corners.
     */
    public Snapshot run() {

        var tortugaRun = wrappedRun(gameData.getJackSparrow(), gameData.getTortuga());
        visitedTortuga = true;

        Snapshot combinedRun = null;
        if (tortugaRun != null) {
            var chestRun = wrappedRun(gameData.getTortuga(), gameData.getChest());

            if (chestRun != null) {
                var combinedList = new ArrayList<>(tortugaRun.getSteps());
                combinedList.addAll(chestRun.getSteps());
                takeSnapshot(combinedList, start);

                combinedRun = exchangeSnapshot();
            }
        }

        visitedTortuga = false;
        var immediateRun = wrappedRun(gameData.getJackSparrow(), gameData.getChest());

        var result = Stream.of(combinedRun, immediateRun)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(p -> p.getSteps().size()))
                .toList();

        return result.isEmpty() ? null : result.get(0);
    }
}

/**
 * Backtracking algorithm over a sea map. Uses greedy point selection and basic heuristic
 * as the optimizations.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see GameData
 * @see Snapshot
 * @see Point
 * @see SearchingAlgorithm
 */
class Backtracking extends SearchingAlgorithm {

    /**
     * Heuristic storage. Heuristic is given as a <code>distanceSquared(target)</code>
     * for each point.
     */
    private final int[][] costs = new int[9][9];

    /**
     * Returns available moves excluding dangerous (except for Kraken), previously observed ones,
     * and those which have less cost than computed current;
     * in a list sorted by the distance to the target (greedy approach).
     *
     * @param point current point.
     * @return available moves.
     */
    private List<Point> moves(Point point) {
        return gameData.getMatrix().firstScenario(point.getX(), point.getY())
                .filter(p -> p.getCell().isSafe())
                .filter(p -> costs[p.getX()][p.getY()] >= steps.size())
                .sorted((p1, p2) -> distanceSquared(p1) - distanceSquared(p2))
                .toList();
    }

    /**
     * Point from which Kraken has been killed.
     */
    private Point krakenKilledPoint = null;

    /**
     * Returns squared euclidean distance from the given point to target.
     *
     * @param point comparator point value.
     * @return squared euclidean distance from the given point to target.
     */
    private int distanceSquared(Point point) {
        return (int) (Math.pow(point.getX() - target.getX(), 2) + Math.pow(point.getY() - target.getY(), 2));
    }

    /**
     * Performs an intermediate backtracking approach for the point and its
     * available neighbors + corners.
     *
     * @param point current point.
     */
    private void doRun(Point point) {
        if (steps.size() + 1 >= minStepsCount) return;

        steps.push(point);

        if (
                visitedTortuga && gameData.getMatrix().firstScenario(point.getX(), point.getY())
                        .anyMatch(p -> p.getCell().isKraken())
        ) {
            gameData.tryRemoveKraken();
            krakenKilledPoint = point;
        }

        var moves = moves(point);

        if (point.equals(target)) {
            takeSnapshot();
            minStepsCount = steps.size();
        } else {
            updateNeighborCosts(point);
            for (var p : moves) doRun(p);
        }

        if (point.equals(krakenKilledPoint))
            gameData.trySetKraken(gameData.getKraken().getX(), gameData.getKraken().getY());

        steps.pop();
    }

    /**
     * Performs a backtracking search run from start to target.
     */
    @Override
    protected void partialRun() {
        cleanCosts();

        costs[start.getX()][start.getY()] = 0;

        updateNeighborCosts(start);
        doRun(start);

        costs[start.getX()][start.getY()] = Integer.MAX_VALUE;
        cleanCosts();

        // Force reset minStepsCount for other runs
        minStepsCount = Integer.MAX_VALUE;
    }

    /**
     * Resets heuristic costs.
     */
    private void cleanCosts() {
        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++)
                costs[y][x] = 81; // The maximum possible path length is 81
    }

    /**
     * Updates the heuristic values for the point's neighbors.
     *
     * @param point current point.
     */
    private void updateNeighborCosts(Point point) {
        var pointCost = costs[point.getX()][point.getY()];

        moves(point).forEach(p -> costs[p.getX()][p.getY()] = Math.min(pointCost + 1, costs[p.getX()][p.getY()]));

        if (scenario == 2)
            gameData.getMatrix().secondNeighbors(point.getX(), point.getY())
                    .filter(p -> {
                        var middleX = Math.abs(point.getX() + p.getX()) / 2;
                        var middleY = Math.abs(point.getY() + p.getY()) / 2;
                        var middlePoint = gameData.getMatrix().getPoint(middleX, middleY);

                        return middlePoint.isPresent() && middlePoint.get().getCell().isSafe();
                    })
                    .filter(p -> p.getCell().isSafe())
                    .forEach(p -> costs[p.getX()][p.getY()] = Math.min(pointCost + 2, costs[p.getX()][p.getY()]));
    }

    public Backtracking(GameData gameData, int scenario) {
        super(gameData, scenario);
        cleanCosts();
    }
}

/**
 * A* algorithm over a sea map. Implementation is taken from the lecture 5
 * as the optimizations.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @author Innopolis University F22-Introduction-to-AI course instructors
 * @see GameData
 * @see Snapshot
 * @see Point
 * @see SearchingAlgorithm
 */
class AStar extends SearchingAlgorithm {
    /**
     * Node containing a point, a parent, and the cost (length of the path from <code>start</code> to
     * this point).
     *
     * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
     */
    private class Node implements Comparable<Node> {

        /**
         * Parent node.
         */
        Node parent;

        /**
         * Corresponding point.
         */
        Point point;

        /**
         * G cost - length of the path from <code>start</code> to this point.
         */

        int gCost = 0;

        public Node(Point point) {
            this.point = point;
        }

        /**
         * Comparison by the formula <code>f = g + h</code>
         *
         * <ol>
         *     <li><code>g</code> = g cost</li>
         *     <li><code>h</code> - heuristic function result</li>
         * </ol>
         * <p>
         * In this case <code>h = max(abs(x - target.x), abs(y - target.y))</code>
         *
         * @param o the object to be compared.
         * @return f - o.f.
         */
        @Override
        public int compareTo(Node o) {

            var h = Math.max(
                    Math.abs(point.getX() - target.getX()),
                    Math.abs(point.getY() - target.getY())
            );

            var oh = Math.max(
                    Math.abs(o.point.getX() - target.getX()),
                    Math.abs(o.point.getY() - target.getY())
            );

            var g = parent != null ? parent.gCost + 1 : 0;
            var og = o.parent != null ? o.parent.gCost + 1 : 0;

            return (h + g) - (oh + og);
        }
    }

    /**
     * Nodes matrix.
     */
    private final Node[][] nodes = new Node[9][9];

    /**
     * Queue for opened nodes.
     */
    private final Queue<Node> opened = new PriorityQueue<>();

    /**
     * Collection of closed (observed) points.
     */
    private final Set<Node> closed = new HashSet<>();

    /**
     * Returns a node having the given point.
     *
     * @param point point.
     * @return node having <code>point</code>.
     */
    private Node getNode(Point point) {
        return nodes[point.getX()][point.getY()];
    }

    /**
     * Resets the data of the <code>nodes</code>.
     */
    private void cleanNodes() {
        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++) {
                nodes[x][y].parent = null;
                nodes[x][y].gCost = 0;
            }
    }

    /**
     * Returns available moves for the given <code>node</code>.
     *
     * @param node node for which the available moves will be returned.
     * @return list of nodes - available moves for the given <code>node</code>.
     */
    private List<Node> moves(Node node) {
        return gameData.getMatrix().firstScenario(node.point.getX(), node.point.getY())
                .filter(p -> {
                    var b1 = !closed.contains(getNode(p));
                    var b2 = p.getCell().isSafe();

                    return b1 && b2;
                })
                .map(this::getNode)
                .toList();
    }

    /**
     * Returns extra 4 second-order neighbors for the given <code>node</code>.
     *
     * @param node node for which the available moves will be returned.
     * @return list of nodes - extra 4 second-order neighbors for the given <code>node</code>.
     */
    private List<Node> secondScenarioMoves(Node node) {
        return gameData.getMatrix().secondNeighbors(node.point.getX(), node.point.getY())
                .filter(p -> {
                    var middleX = Math.abs(node.point.getX() + p.getX()) / 2;
                    var middleY = Math.abs(node.point.getY() + p.getY()) / 2;
                    var middlePoint = gameData.getMatrix().getPoint(middleX, middleY);

                    return middlePoint.isPresent() && middlePoint.get().getCell().isSafe();
                })
                .filter(p -> p.getCell().isSafe())
                .map(this::getNode)
                .toList();
    }

    /**
     * Core A* algorithm. Common implementation is taken from the lecture notes.
     *
     * @param start start node.
     */
    private void doRun(Node start) {
        opened.offer(start);

        while (!opened.isEmpty()) {

            var current = opened.poll();

            if (visitedTortuga && gameData.getMatrix().firstScenario(current.point.getX(), current.point.getY())
                    .anyMatch(p -> p.getCell().isKraken()))
                gameData.tryRemoveKraken();

            for (var n : moves(current)) {

                if (!opened.contains(n)) {
                    n.gCost = current.gCost + 1;
                    n.parent = current;
                    opened.offer(n);
                } else {
                    if (current.gCost + 1 < n.gCost) {
                        n.gCost = current.gCost + 1;
                        n.parent = current;
                    }
                }

                closed.add(n);
            }

            if (scenario == 2)
                for (var n : secondScenarioMoves(current)) {
                    if (!opened.contains(n))
                        n.gCost = current.gCost + 2;
                    else if (current.gCost + 2 < n.gCost)
                        n.gCost = current.gCost + 2;
                }

            closed.add(current);
        }

        gameData.trySetKraken(gameData.getKraken().getX(), gameData.getKraken().getY());
    }

    public AStar(GameData gameData, int scenario) {
        super(gameData, scenario);

        for (int i = 0; i < 9; i++)
            nodes[i] = new Node[9];

        // Initialization of empty nodes matrix
        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++)
                if (gameData.getMatrix().getPoint(x, y).isPresent())
                    nodes[x][y] = new Node(gameData.getMatrix().getPoint(x, y).get());
    }

    /**
     * Performs an A* search run from the start to target.
     */
    @Override
    protected void partialRun() {

        steps.clear();
        closed.clear();
        cleanNodes();

        doRun(getNode(start));

        var current = getNode(target);

        for (; current != null && !current.point.equals(getNode(start).point); current = current.parent)
            steps.push(current.point);

        var stepsList = new ArrayList<>(steps);
        Collections.reverse(stepsList);

        // Fix for the blocked kraken cells: If we cannot connect our path with the start point => return null
        if (
                steps.isEmpty() || gameData.getMatrix().firstScenario(start.getX(), start.getY())
                        .noneMatch(p -> p.equals(stepsList.get(0)))
        ) {
            currentSnapshot = null;
            return;
        }

        takeSnapshot(stepsList, start);
    }
}

/**
 * Input helper utility class.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see GameData
 * @see AStar
 */
class InputHelper {

    /**
     * Input file path.
     */
    public static final Path INPUT = Path.of("input.txt");

    /**
     * Input lines list.
     */
    private static List<String> inputData;

    /**
     * List of spawn points used in GameData.
     *
     * @see GameData
     */
    private static List<Point> points;

    /**
     * Game scenario. Used in AStar.
     *
     * @see AStar
     */
    private static int scenario;

    /**
     * 9x9 utility matrix. Used as a cache for the points.
     */
    private static final Matrix MATRIX = new Matrix();

    /**
     * Parses the inputData.
     *
     * @throws IOException if input lines is not equal to 2.
     */
    private static void parseInput() throws IOException {
        if (inputData.size() != 2)
            throw new IOException("Number of input lines is not equal to 2");

        parsePoints();
        parseScenario();
    }

    /**
     * Tries to init and parse the reading streams. Accepts input from console and the file.
     *
     * @throws IOException if it can't open the streams, given invalid input data, or
     *                     user selected invalid input way.
     */
    public static void tryInitAndParse() throws IOException {
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

    /**
     * Parses and validates the points list.
     *
     * @throws IOException if any of the positions is invalid.
     */
    private static void parsePoints() throws IOException {
        points = Arrays.stream(inputData.get(0).split("\\s+"))
                .filter(x -> x.matches("\\[[0-8],[0-8]]"))
                .map(x -> {
                    var split = x.replaceAll("[\\[\\]]+", "").split(",");
                    return MATRIX.getPoint(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        if (points.size() != 6)
            throw new IOException("Invalid positions");
    }

    /**
     * Parses and validates the game scenario.
     *
     * @throws IOException if scenario is invalid.
     */
    private static void parseScenario() throws IOException {
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

/**
 * Output helper utility class.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see Snapshot
 */
class OutputHelper {

    /**
     * Backtracking output file path.
     *
     * @see Backtracking
     */
    public static final Path BACKTRACKING_OUT = Path.of("outputBacktracking.txt");

    /**
     * AStar output file path.
     *
     * @see AStar
     */
    public static final Path A_STAR_OUT = Path.of("outputAStar.txt");

    /**
     * Writes the given nullable snapshot (null = lose) to the given output file.
     *
     * @param outputPath output file path.
     * @param snapshot   nullable snapshot.
     * @param millis     algorithm execution time in milliseconds.
     * @throws IOException default cases of IOException.
     */
    static void printResult(Path outputPath, Snapshot snapshot, long millis) throws IOException {
        if (snapshot == null) Files.writeString(outputPath, "Lose\n");
        else Files.writeString(outputPath, String.format("Win\n%s\n%d ms\n", snapshot, millis));
    }
}

/**
 * Tests helper utility class.
 *
 * @author Dmitrii Alekhin (B21-03 d.alekhin@innopolis.university / @dmfrpro (Telegram))
 * @see GameData
 * @see AStar
 */
class TestHelper {

    /**
     * Returns mean of tests results.
     *
     * @param results list of tests results.
     * @return mean of tests results.
     */
    private static double mean(List<Long> results) {
        return results.stream().mapToDouble(Long::doubleValue).sum() / results.size();
    }

    /**
     * Returns median of tests results.
     *
     * @param results list of tests results.
     * @return median of tests results.
     */
    private static double median(List<Long> results) {
        results.sort(Comparator.comparingInt(Long::intValue));

        return results.size() % 2 == 0
                ? (results.get(results.size() / 2) + results.get(results.size() / 2)) / 2d
                : results.get(results.size() / 2) / 2d;
    }

    /**
     * Returns mode of tests results.
     *
     * @param results list of tests results.
     * @return mode of tests results.
     */
    private static long mode(List<Long> results) {
        var maxFrequency = -1;
        var mode = -1L;

        for (var r : results) {
            var frequency = Collections.frequency(results, r);
            if (frequency > maxFrequency) {
                maxFrequency = frequency;
                mode = r;
            }
        }

        return mode;
    }

    /**
     * Returns variance of tests results.
     *
     * @param results list of tests results.
     * @return variance of tests results.
     */
    private static double variance(List<Long> results) {
        var mean = mean(results);
        return results.stream().mapToDouble(x -> Math.pow(x - mean, 2)).sum() / (results.size() - 1);
    }

    /**
     * Returns standard deviation of tests results.
     *
     * @param results list of tests results.
     * @return standard deviation of tests results.
     */
    private static double sDeviation(List<Long> results) {
        return Math.sqrt(variance(results));
    }

    /**
     * Prints the descriptive statistics for the given list of execution times:
     * <ol>
     *     <li>Win rate</li>
     *     <li>Lose rate</li>
     *     <li>Mean of the execution times</li>
     *     <li>Mode of the execution times</li>
     *     <li>Median of the execution times</li>
     *     <li>Standard deviation of the execution times</li>
     * </ol>
     *
     * @param winRate     win rate.
     * @param loseRate    lose rate.
     * @param timeResults list of execution times.
     */
    private static void printStats(int winRate, int loseRate, List<Long> timeResults) {
        System.out.printf(
                "winRate: %d\nloseRate: %d\nmean: %f\nmode: %d\nmedian: %f\nsDeviation: %f\n\n",
                winRate, loseRate,
                mean(timeResults), mode(timeResults), median(timeResults),
                sDeviation(timeResults)
        );
    }

    /**
     * Generates and runs <code>repeatNumber</code> tests and returns the results
     * prints the statistical data of both Backtracking and A* algorithms:
     * <ol>
     *     <li>Win rate</li>
     *     <li>Lose rate</li>
     *     <li>Mean of the execution times</li>
     *     <li>Mode of the execution times</li>
     *     <li>Median of the execution times</li>
     *     <li>Standard deviation of the execution times</li>
     * </ol>
     *
     * @param repeatNumber number of generated random tests.
     */
    public static void run(int repeatNumber) throws IOException {
        var backtrackingFirstTimes = new ArrayList<Long>(repeatNumber);
        var backtrackingFirstWins = 0;

        var backtrackingSecondTimes = new ArrayList<Long>(repeatNumber);
        var backtrackingSecondWins = 0;

        var aStarFirstTimes = new ArrayList<Long>(repeatNumber);
        var aStarFirstWins = 0;

        var aStarSecondTimes = new ArrayList<Long>(repeatNumber);
        var aStarSecondWins = 0;

        for (int i = 0; i < repeatNumber; i++) {
            if (i % 100 == 0)
                System.out.printf("Running test N = %d\n", i);

            var data1 = new GameData();
            var data2 = data1.clone();
            var data3 = data2.clone();
            var data4 = data3.clone();

            var startMillis = System.currentTimeMillis();
            var result1 = new Backtracking(data1, 1).run();
            backtrackingFirstTimes.add(System.currentTimeMillis() - startMillis);
            if (result1 != null) ++backtrackingFirstWins;

            startMillis = System.currentTimeMillis();
            var result2 = new Backtracking(data2, 2).run();
            backtrackingSecondTimes.add(System.currentTimeMillis() - startMillis);
            if (result2 != null) ++backtrackingSecondWins;

            startMillis = System.currentTimeMillis();
            var result3 = new AStar(data3, 1).run();
            aStarFirstTimes.add(System.currentTimeMillis() - startMillis);
            if (result3 != null) ++aStarFirstWins;

            startMillis = System.currentTimeMillis();
            var result4 = new AStar(data4, 2).run();
            aStarSecondTimes.add(System.currentTimeMillis() - startMillis);
            if (result4 != null) ++aStarSecondWins;

            if (backtrackingFirstWins != aStarFirstWins) {
                System.out.println("FATAL!");

                OutputHelper.printResult(OutputHelper.BACKTRACKING_OUT, result1, 0);
                OutputHelper.printResult(OutputHelper.A_STAR_OUT, result3, 0);

                break;
            }
        }

        printStats(backtrackingFirstWins, repeatNumber - backtrackingFirstWins, backtrackingFirstTimes);
        printStats(backtrackingSecondWins, repeatNumber - backtrackingSecondWins, backtrackingSecondTimes);
        printStats(aStarFirstWins, repeatNumber - aStarFirstWins, aStarFirstTimes);
        printStats(aStarSecondWins, repeatNumber - aStarSecondWins, aStarSecondTimes);
    }
}