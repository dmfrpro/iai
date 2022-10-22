import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    static final Path backtrackingOut = Path.of("outputBacktracking.txt");
    static final Path aStarOut = Path.of("outputAStar.txt");

    public static void main(String[] args) {
        try {
            InputHelper.tryInitStreams(Path.of("input.txt"));
            var positions = InputHelper.getPositions();
            var scenario = InputHelper.getScenario();
            var cells = new Cells(positions);

            System.out.println(cells);

            var backtrackingRunner = new Backtracking(cells, scenario);
            backtrackingRunner.run();

            var snapshot = backtrackingRunner.getCurrentSnapshot();
            OutputHelper.printResult(backtrackingOut, snapshot);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

enum CellType {
    FREE("_"),
    KRAKEN("k"),
    ROCK("R"),
    KRAKEN_ROCK("K"),
    DANGEROUS("#"),
    DAVY_JONES("D"),
    TORTUGA("T"),
    CHEST("C"),

    PATH("*");

    private final String icon;

    CellType(String icon) {
        this.icon = icon;
    }

    @Override
    public String toString() {
        return icon;
    }
}

class Pos {

    private final int x;
    private final int y;
    private static final Pos[][] posMatrix = new Pos[9][9];

    static {
        for (int i = 0; i < 9; i++)
            posMatrix[i] = new Pos[9];

        for (int i = 0; i < 9; i++)
            for (int j = 0; j < 9; j++)
                posMatrix[j][i] = new Pos(i, j);
    }

    public static Optional<Pos> getInstance(int x, int y) {
        if (y < 0 || y >= posMatrix.length || x < 0 || x >= posMatrix[0].length)
            return Optional.empty();
        return Optional.of(posMatrix[y][x]);
    }
    public boolean isPresent() {
        return y >= 0 && y < posMatrix.length && x >= 0 && x < posMatrix[0].length;
    }

    private Pos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public List<Pos> neighbors() {
        return Stream.of(
                        getInstance(x, y + 1), getInstance(x, y - 1),
                        getInstance(x + 1, y), getInstance(x - 1, y)
                )
                .filter(Optional::isPresent)
                .map(p -> p.orElse(null))
                .toList();
    }

    public List<Pos> corners() {
        return Stream.of(
                        getInstance(x + 1, y + 1), getInstance(x + 1, y - 1),
                        getInstance(x - 1, y + 1), getInstance(x - 1, y - 1)
                )
                .filter(Optional::isPresent)
                .map(p -> p.orElse(null))
                .toList();
    }

    public List<Pos> secondNeighbors() {
        return Stream.of(
                        getInstance(x, y + 2), getInstance(x, y - 2),
                        getInstance(x + 2, y), getInstance(x - 2, y)
                )
                .filter(Optional::isPresent)
                .map(p -> p.orElse(null))
                .toList();
    }

    public int squaredDistanceTo(Pos pos) {
        return (int) (Math.pow(pos.x - x, 2) + Math.pow(pos.y - y, 2));
    }

    @Override
    public String toString() {
        return String.format("[%d,%d]", x, y);
    }
}

record Snapshot(List<Pos> shortestPath, String cells, long timeMillis) {
    @Override
    public String toString() {
        var shortestPathString = shortestPath.stream()
                .map(Pos::toString)
                .collect(Collectors.joining(" "));

        return String.format("%d\n%s\n%s%d ms\n", shortestPath.size(), shortestPathString, cells, timeMillis);
    }
}

class Cells {
    private static final Random random = new Random();

    private final CellType[][] cells;
    private Pos jackSparrow;
    private Pos davyJones;
    private Pos kraken;

    private Pos chest;
    private Pos tortuga;

    private final CellType[] davyJonesExclusions = new CellType[0];

    private final CellType[] krakenExclusions = new CellType[]{
            CellType.DAVY_JONES
    };

    private final CellType[] rockExclusions = new CellType[]{
            CellType.DAVY_JONES,
            CellType.TORTUGA,
            CellType.CHEST
    };

    private final CellType[] chestExclusions = new CellType[]{
            CellType.DAVY_JONES,
            CellType.KRAKEN,
            CellType.ROCK,
            CellType.KRAKEN_ROCK,
            CellType.DANGEROUS
    };

    private final CellType[] tortugaExclusions = new CellType[]{
            CellType.DAVY_JONES,
            CellType.KRAKEN,
            CellType.ROCK,
            CellType.KRAKEN_ROCK,
            CellType.DANGEROUS,
            CellType.CHEST
    };

    private final CellType[] dangerousExclusions = new CellType[]{
            CellType.DAVY_JONES,
            CellType.TORTUGA,
            CellType.CHEST,
            CellType.ROCK,
            CellType.KRAKEN_ROCK,
            CellType.KRAKEN
    };

    private final CellType[] freeExclusions = new CellType[]{
            CellType.DAVY_JONES,
            CellType.TORTUGA,
            CellType.CHEST,
            CellType.ROCK
    };

    private boolean trySetCell(CellType type, Pos pos, CellType... exclusions) {
        if (getCell(pos).isPresent() && Arrays.stream(exclusions).noneMatch(cell -> cell == cells[pos.getY()][pos.getX()])) {
            cells[pos.getY()][pos.getX()] = type;
            return true;
        }
        return false;
    }

    private void setNeighbors(CellType type, Pos pos, CellType... exclusions) {
        for (var p : pos.neighbors()) trySetCell(type, p, exclusions);
    }

    private void setDangerousCorners(Pos pos) {
        for (var p : pos.corners()) trySetCell(CellType.DANGEROUS, p, dangerousExclusions);
    }

    private boolean setDavyJones(Pos pos) {
        if (trySetCell(CellType.DAVY_JONES, pos, davyJonesExclusions)) {
            setNeighbors(CellType.DANGEROUS, pos, dangerousExclusions);
            setDangerousCorners(pos);

            davyJones = pos;
            return true;
        }
        return false;
    }

    private boolean setKraken(Pos pos) {
        if (trySetCell(CellType.KRAKEN, pos, krakenExclusions)) {
            kraken = pos;
            setNeighbors(CellType.DANGEROUS, pos, dangerousExclusions);
            return true;
        }
        return false;
    }

    private boolean setRock(Pos pos) {
        var type = cells[pos.getY()][pos.getX()] == CellType.KRAKEN ? CellType.KRAKEN_ROCK : CellType.ROCK;
        return trySetCell(type, pos, rockExclusions);
    }

    private boolean setChest(Pos pos) {
        var result = trySetCell(CellType.CHEST, pos, chestExclusions);
        if (result) chest = pos;
        return result;
    }

    private boolean setTortuga(Pos pos) {
        var result = trySetCell(CellType.TORTUGA, pos, tortugaExclusions);
        if (result) tortuga = pos;
        return result;
    }

    private boolean setJackSparrow(Pos pos) {
        if (getCell(pos).isEmpty())
            return false;
        else {
            var currentCell = getCell(pos).get();
            if (Stream.of(CellType.KRAKEN, CellType.ROCK, CellType.DAVY_JONES).noneMatch(cell -> cell == currentCell)) {
                if (jackSparrow == null) jackSparrow = pos;
                return true;
            }
        }

        return false;
    }

    private CellType[][] emptyCells() {
        Supplier<CellType[]> rowGenerator = () -> Stream.generate(() -> CellType.FREE)
                .limit(9)
                .toArray(CellType[]::new);

        return Stream.generate(rowGenerator)
                .limit(9)
                .toArray(x -> new CellType[9][9]);
    }

    private Pos getRandomPos() {
        var optPos = Pos.getInstance(random.nextInt(0, 9), random.nextInt(0, 9));
        if (optPos.isEmpty() || (optPos.get().getX() == 0 && optPos.get().getY() != 0))
            return getRandomPos();
        return optPos.get();
    }

    Cells(List<Pos> pos) {
        cells = emptyCells();

        var generationResult = Stream.of(
                setDavyJones(pos.get(1)),
                setKraken(pos.get(2)),
                setRock(pos.get(3)),
                setChest(pos.get(4)),
                setTortuga(pos.get(5)),
                setJackSparrow(pos.get(0))
        ).allMatch(result -> result);

        if (!generationResult)
            throw new IllegalArgumentException("Invalid pos");
    }

    Cells() {
        cells = emptyCells();

        var optPos = Pos.getInstance(0, 0);
        optPos.ifPresent(this::setJackSparrow);

        var pos = getRandomPos();
        while (!setDavyJones(pos))
            pos = getRandomPos();

        pos = getRandomPos();
        while (!setKraken(pos))
            pos = getRandomPos();

        pos = getRandomPos();
        while (!setRock(pos))
            pos = getRandomPos();

        pos = getRandomPos();
        while (!setChest(pos))
            pos = getRandomPos();

        pos = getRandomPos();
        while (!setTortuga(pos))
            pos = getRandomPos();
    }

    void trySetPath(Pos pos) {
        trySetCell(CellType.PATH, pos);
    }

    void tryUnsetPath(CellType previousCell, Pos pos) {
        trySetCell(previousCell, pos);
    }

    void removeKraken() {
        var type = cells[kraken.getY()][kraken.getX()] == CellType.KRAKEN_ROCK
                ? CellType.ROCK
                : CellType.FREE;

        trySetCell(type, kraken, freeExclusions);
        setNeighbors(CellType.FREE, kraken, freeExclusions);

        // Update perception zones of Davy Jones
        setNeighbors(CellType.DANGEROUS, davyJones, dangerousExclusions);
        setDangerousCorners(davyJones);
    }

    Optional<CellType> getCell(Pos pos) {
        if (!pos.isPresent())
            return Optional.empty();

        return Optional.ofNullable(cells[pos.getY()][pos.getX()]);
    }

    Pos getJackSparrow() {
        return jackSparrow;
    }

    Pos getChest() {
        return chest;
    }

    Pos getTortuga() {
        return tortuga;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder("-".repeat(19)).append("\n  ");

        for (int i = 0; i < 8; i++)
            builder.append(i).append(" ");
        builder.append(8).append("\n");

        for (int x = 0; x < 9; x++) {
            builder.append(x).append(" ");
            for (int y = 0; y < 8; y++)
                builder.append(cells[y][x]).append(" ");
            builder.append(cells[8][x]).append("\n");
        }

        builder.append("-".repeat(19)).append('\n');
        return builder.toString();
    }
}

class Backtracking {

    private final Stack<Pos> shortestPath = new Stack<>();
    private final Cells cells;

    private final int scenario;
    private final Pos tortuga;

    private Snapshot currentSnapshot;
    private int minShortestPath = Integer.MAX_VALUE;
    private int currentSquaredDistance = Integer.MAX_VALUE;

    private List<Pos> scenarioMoves(Pos pos) {
        return Stream.of(pos.neighbors(), pos.corners())
                .flatMap(List::stream)
                .filter(p -> !shortestPath.contains(p))
                .sorted((p1, p2) -> p1.squaredDistanceTo(cells.getChest()) - p2.squaredDistanceTo(cells.getChest()))
                .toList();
    }

    private boolean isLosingPos(Pos pos) {
        var cell = cells.getCell(pos);
        return cell.map(type -> type == CellType.DANGEROUS ||
                type == CellType.ROCK ||
                type == CellType.KRAKEN ||
                type == CellType.KRAKEN_ROCK).orElse(false);
    }

    private void takeSnapshot() {
        currentSnapshot = new Snapshot(new ArrayList<>(shortestPath), cells.toString(), 0);
    }

    private void doBacktracking(Pos currentPos) {
        if (isLosingPos(currentPos)) return;

        // +1 for the new pos which will be pushed into shortestPath
        if (shortestPath.size() + 1 < minShortestPath) {

            var tmp = currentSquaredDistance;
            if (currentPos.squaredDistanceTo(cells.getChest()) < currentSquaredDistance)
                currentSquaredDistance = currentPos.squaredDistanceTo(cells.getChest());
            else if (scenarioMoves(currentPos).isEmpty()) return;

            shortestPath.push(currentPos);

            var optCell = cells.getCell(currentPos);
            if (optCell.isEmpty()) return;

            cells.trySetPath(currentPos);

            if (optCell.get() == CellType.CHEST) {
                takeSnapshot();
                minShortestPath = shortestPath.size();
            } else
                for (var pos : scenarioMoves(currentPos))
                    doBacktracking(pos);

            shortestPath.pop();
            cells.tryUnsetPath(optCell.get(), currentPos);
            currentSquaredDistance = tmp;
        }
    }

    Backtracking(Cells cells, int scenario) {
        this.cells = cells;
        this.scenario = scenario;
        this.tortuga = cells.getTortuga();
    }

    Snapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    void run() {
        long startMillis = System.currentTimeMillis();

        // If Jack Sparrow is already spawned in danger zone,
        // then we immediately lose
        if (isLosingPos(cells.getJackSparrow())) return;

        var oldCell = cells.getCell(cells.getJackSparrow());
        if (oldCell.isPresent()) {
            cells.trySetPath(cells.getJackSparrow());

            for (var pos : scenarioMoves(cells.getJackSparrow()))
                doBacktracking(pos);

            // Force update snapshot time via copying the snapshot
            if (currentSnapshot != null)
                currentSnapshot = new Snapshot(
                        currentSnapshot.shortestPath(),
                        currentSnapshot.cells(),
                        System.currentTimeMillis() - startMillis
                );

            cells.tryUnsetPath(oldCell.get(), cells.getJackSparrow());
        }
    }
}

class InputHelper {
    private static List<String> inputData;
    private static List<Pos> positions;
    private static int scenario;

    static void tryInitStreams(Path inputPath) {
        try {
            inputData = Files.readAllLines(inputPath);

            if (inputData.size() < 2)
                throw new IOException("Number of input file lines is < 2");

            readPositions();
            readScenario();
        } catch (IOException ignored) {
            try (var console = new BufferedReader(new InputStreamReader(System.in))) {
                System.out.println("Enter the input data:");
                inputData = Stream.of(console.readLine(), console.readLine()).toList();

                if (inputData.size() < 2)
                    throw new IOException("Number of input console lines is < 2");

                readPositions();
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

    private static void readPositions() throws IOException {
        positions = Arrays.stream(inputData.get(0).split("\\s+"))
                .filter(x -> x.matches("\\[[0-8],[0-8]]"))
                .map(x -> {
                    var split = x.replaceAll("[\\[\\]]+", "").split(",");
                    return Pos.getInstance(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                })
                .map(p -> p.orElse(null))
                .filter(Objects::nonNull)
                .toList();

        if (positions.size() != 6)
            throw new IOException("Invalid positions");
    }

    private static void readScenario() throws IOException {
        scenario = Integer.parseInt(inputData.get(1));
        if (scenario != 1 && scenario != 2)
            throw new IOException("Invalid scenario");
    }

    static List<Pos> getPositions() {
        return positions;
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