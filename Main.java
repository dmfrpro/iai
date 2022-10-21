import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        try {
            InputHelper.tryInitStreams(Path.of("input.txt"));
            var positions = InputHelper.getPositions();
            var scenario = InputHelper.getScenario();
            var cells = new Cells(positions);

            System.out.println(cells);
            cells.removeKraken();
            System.out.println(cells);
            System.out.println(new Cells());

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
    CHEST("C");

    private final String icon;

    CellType(String icon) {
        this.icon = icon;
    }

    @Override
    public String toString() {
        return icon;
    }
}

record Pos(int x, int y) {
    Set<Pos> neighbors(int x, int y) {
        return Set.of(
                        new Pos(x, y + 1), new Pos(x, y - 1),
                        new Pos(x + 1, y), new Pos(x - 1, y)
                );
    }

    Set<Pos> corners(int x, int y) {
        return Set.of(
                        new Pos(x, y + 1), new Pos(x, y - 1),
                        new Pos(x + 1, y), new Pos(x - 1, y)
                );
    }

    @Override
    public String toString() {
        return String.format("[%d,%d]", y, x);
    }
}

class Cells {
    private static final Random random = new Random();

    private final CellType[][] cells;
    private Pos jackSparrow;
    private Pos davyJones;
    private Pos kraken;

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

    private boolean trySetCell(CellType type, int x, int y, CellType... exclusions) {
        if (getCell(x, y).isPresent() && Arrays.stream(exclusions).noneMatch(cell -> cell == cells[y][x])) {
            cells[y][x] = type;
            return true;
        }
        return false;
    }

    private void setNeighbors(CellType type, int x, int y, CellType... exclusions) {
        trySetCell(type, x + 1, y, exclusions);
        trySetCell(type, x - 1, y, exclusions);
        trySetCell(type, x, y + 1, exclusions);
        trySetCell(type, x, y - 1, exclusions);
    }

    private void setDangerousCorners(int x, int y) {
        trySetCell(CellType.DANGEROUS, x + 1, y + 1, dangerousExclusions);
        trySetCell(CellType.DANGEROUS, x + 1, y - 1, dangerousExclusions);
        trySetCell(CellType.DANGEROUS, x - 1, y + 1, dangerousExclusions);
        trySetCell(CellType.DANGEROUS, x - 1, y - 1, dangerousExclusions);
    }

    private boolean setDavyJones(int x, int y) {
        if (trySetCell(CellType.DAVY_JONES, x, y, davyJonesExclusions)) {
            setNeighbors(CellType.DANGEROUS, x, y, dangerousExclusions);
            setDangerousCorners(x, y);

            davyJones = new Pos(x, y);
            return true;
        }
        return false;
    }

    private boolean setKraken(int x, int y) {
        if (trySetCell(CellType.KRAKEN, x, y, krakenExclusions)) {
            kraken = new Pos(x, y);
            setNeighbors(CellType.DANGEROUS, x, y, dangerousExclusions);
            return true;
        }
        return false;
    }

    private boolean setRock(int x, int y) {
        var type = cells[y][x] == CellType.KRAKEN ? CellType.KRAKEN_ROCK : CellType.ROCK;
        return trySetCell(type, x, y, rockExclusions);
    }

    private boolean setChest(int x, int y) {
        return trySetCell(CellType.CHEST, x, y, chestExclusions);
    }

    private boolean setTortuga(int x, int y) {
        return trySetCell(CellType.TORTUGA, x, y, tortugaExclusions);
    }

    private boolean setJackSparrow(int x, int y) {
        if (getCell(x, y).isEmpty())
            return false;
        else {
            var currentCell = getCell(x, y).get();
            if (Stream.of(CellType.KRAKEN, CellType.ROCK, CellType.DAVY_JONES).noneMatch(cell -> cell == currentCell)) {
                if (jackSparrow == null) jackSparrow = new Pos(x, y);
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
        var result = new Pos(random.nextInt(0, 9), random.nextInt(0, 9));
        if (result.x() != 0 && result.y() != 0) return result;
        else return getRandomPos();
    }

    public Cells(List<Pos> pos) {
        cells = emptyCells();

        var generationResult = Stream.of(
                setDavyJones(pos.get(1).x(), pos.get(1).y()),
                setKraken(pos.get(2).x(), pos.get(2).y()),
                setRock(pos.get(3).x(), pos.get(3).y()),
                setChest(pos.get(4).x(), pos.get(4).y()),
                setTortuga(pos.get(5).x(), pos.get(5).y()),
                setJackSparrow(pos.get(0).x(),pos.get(0).y())
        ).allMatch(result -> result);

        if (!generationResult)
            throw new IllegalArgumentException("Invalid pos");
    }

    public Cells() {
        cells = emptyCells();

        setJackSparrow(0, 0);

        var pos = getRandomPos();
        while (!setDavyJones(pos.x(), pos.y()))
            pos = getRandomPos();

        pos = getRandomPos();
        while (!setKraken(pos.x(), pos.y()))
            pos = getRandomPos();

        pos = getRandomPos();
        while (!setRock(pos.x(), pos.y()))
            pos = getRandomPos();

        pos = getRandomPos();
        while (!setChest(pos.x(), pos.y()))
            pos = getRandomPos();

        pos = getRandomPos();
        while (!setTortuga(pos.x(), pos.y()))
            pos = getRandomPos();
    }

    void removeKraken() {
        var type = cells[kraken.y()][kraken.x()] == CellType.KRAKEN_ROCK
                ? CellType.ROCK
                : CellType.FREE;

        trySetCell(type, kraken.x(), kraken.y(), freeExclusions);
        setNeighbors(CellType.FREE, kraken.x(), kraken.y(), freeExclusions);

        // Update perception zones of Davy Jones
        setNeighbors(CellType.DANGEROUS, davyJones.x(), davyJones.y(), dangerousExclusions);
        setDangerousCorners(davyJones.x(), davyJones.y());
    }

    Optional<CellType> getCell(int x, int y) {
        if (y < 0 || y >= cells.length || x < 0 || x >= cells.length)
            return Optional.empty();

        return Optional.ofNullable(cells[y][x]);
    }

    @Override
    public String toString() {
        var indexesRow = IntStream.iterate(0, i -> i + 1)
                .limit(9)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(" "));

        var builder = new StringBuilder("  ").append(indexesRow).append('\n');

        IntStream.iterate(0, i -> i + 1)
                .limit(9)
                .forEach(i -> {
                    builder.append(i).append(' ');
                    var row = Arrays.stream(cells[i])
                            .map(Object::toString)
                            .collect(Collectors.joining(" "));
                    builder.append(row).append('\n');
                });

        return builder.toString();
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
                .filter(x -> x.matches("\\[\\d,\\d]"))
                .map(x -> {
                    var split = x.replaceAll("[\\[\\]]+", "").split(",");
                    return new Pos(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                })
                .filter(p -> p.x() >= 0 && p.x() < 9)
                .filter(p -> p.y() >= 0 && p.y() < 9)
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