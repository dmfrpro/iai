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
            var inputHelper = new InputHelper(Path.of("input.txt"));
            var coordinates = inputHelper.getPositions();
            var scenario = inputHelper.getScenario();
            var cells = new Cells(coordinates);

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
    FREE("."),
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

    private Pos getRandomCoordinates() {
        return new Pos(random.nextInt(0, 9), random.nextInt(0, 9));
    }

    public Cells(List<Pos> coordinates) {
        cells = emptyCells();

        var generationResult = Stream.of(
                setDavyJones(coordinates.get(1).x(), coordinates.get(1).y()),
                setKraken(coordinates.get(2).x(), coordinates.get(2).y()),
                setRock(coordinates.get(3).x(), coordinates.get(3).y()),
                setChest(coordinates.get(4).x(), coordinates.get(4).y()),
                setTortuga(coordinates.get(5).x(), coordinates.get(5).y())
        ).allMatch(result -> result);

        if (!generationResult)
            throw new IllegalArgumentException("Invalid coordinates");
    }

    public Cells() {
        cells = emptyCells();

        var coordinates = getRandomCoordinates();

        while (!setDavyJones(coordinates.x(), coordinates.y()))
            coordinates = getRandomCoordinates();

        while (!setKraken(coordinates.x(), coordinates.y()))
            coordinates = getRandomCoordinates();

        while (!setRock(coordinates.x(), coordinates.y()))
            coordinates = getRandomCoordinates();

        while (!setChest(coordinates.x(), coordinates.y()))
            coordinates = getRandomCoordinates();

        while (!setTortuga(coordinates.x(), coordinates.y()))
            coordinates = getRandomCoordinates();

        while (!setJackSparrow(coordinates.x(), coordinates.y()))
            coordinates = getRandomCoordinates();
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
    private List<String> inputData;
    private List<Pos> positions;
    private int scenario;

    private void tryInitStreams(Path inputPath) {
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

    public InputHelper(Path inputFilePath) {
        tryInitStreams(inputFilePath);
    }

    private void readPositions() throws IOException {
        positions = Arrays.stream(inputData.get(0).split("\\s+"))
                .filter(x -> x.matches("\\[\\d,\\d]"))
                .map(x -> {
                    var split = x.replaceAll("[\\[\\]]+", "").split(",");
                    return new Pos(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                })
                .filter(coordinate -> coordinate.x() >= 0 && coordinate.x() < 9)
                .filter(coordinate -> coordinate.y() >= 0 && coordinate.y() < 9)
                .toList();

        if (positions.size() != 6)
            throw new IOException("Invalid coordinates");
    }

    private void readScenario() throws IOException {
        scenario = Integer.parseInt(inputData.get(1));
        if (scenario != 1 && scenario != 2)
            throw new IOException("Invalid scenario");
    }

    public List<Pos> getPositions() {
        return positions;
    }

    public int getScenario() {
        return scenario;
    }
}