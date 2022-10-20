import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {

    }
}

enum CellType {
    JACK_SPARROW,
    FREE,
    KRAKEN,
    DANGEROUS,
    DAVY_JONES,
    ROCK,
    TORTUGA,
    CHEST
}

record Board(CellType[][] cells) {

    Optional<CellType> getCell(int x, int y) {
        if (y < 0 || y >= cells.length || x < 0 || x >= cells.length)
            return Optional.empty();

        return Optional.ofNullable(cells[y][x]);
    }

    Set<CellType> getAdjacentCells(int x, int y) {
        return Stream.of(
                        getCell(x, y + 1), getCell(x, y - 1),
                        getCell(x + 1, y), getCell(x - 1, y)
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    Set<CellType> getCornerCells(int x, int y) {
        return Stream.of(
                        getCell(x + 1, y + 1), getCell(x - 1, y + 1),
                        getCell(x + 1, y - 1), getCell(x - 1, y - 1)
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return "";
    }
}

record Coordinates(int x, int y) {
}

class BoardGenerator {
    private final int xDimension;
    private final int yDimension;

    private CellType[][] cells;

    private final CellType[] jackSparrowExclusions = new CellType[]{
            CellType.JACK_SPARROW,
            CellType.DAVY_JONES,
            CellType.KRAKEN,
            CellType.ROCK,
            CellType.CHEST,
            CellType.DANGEROUS
    };

    private final CellType[] davyJonesExclusions = new CellType[]{
            CellType.JACK_SPARROW,
            CellType.DAVY_JONES,
            CellType.KRAKEN,
            CellType.ROCK,
            CellType.CHEST,
            CellType.TORTUGA
    };

    private final CellType[] krakenExclusions = new CellType[]{
            CellType.JACK_SPARROW,
            CellType.DAVY_JONES,
            CellType.CHEST,
            CellType.TORTUGA,
    };

    private final CellType[] rockExclusions = new CellType[]{
            CellType.JACK_SPARROW,
            CellType.DAVY_JONES,
            CellType.TORTUGA,
            CellType.CHEST
    };

    private final CellType[] chestExclusions = new CellType[]{
            CellType.JACK_SPARROW,
            CellType.DAVY_JONES,
            CellType.KRAKEN,
            CellType.DANGEROUS,
            CellType.ROCK
    };

    private final CellType[] tortugaExclusions = new CellType[]{
            CellType.DAVY_JONES,
            CellType.CHEST,
            CellType.KRAKEN,
            CellType.DANGEROUS,
            CellType.ROCK
    };

    public BoardGenerator(int xDimension, int yDimension) {
        this.xDimension = xDimension;
        this.yDimension = yDimension;
    }

    BoardGenerator emptyBoard() {
        Supplier<CellType[]> rowGenerator = () -> Stream.generate(() -> CellType.FREE)
                .limit(xDimension)
                .toArray(CellType[]::new);

        cells = Stream.generate(rowGenerator)
                .limit(yDimension)
                .toArray(x -> new CellType[yDimension][xDimension]);

        return this;
    }

    private void tryAppendCell(CellType[][] cells, CellType type, int x, int y, CellType... exclusions) {
        if (y >= 0 && y < cells.length && x >= 0 && x < cells.length)
            if (Arrays.stream(exclusions).noneMatch(cell -> cell == cells[y][x]))
                cells[y][x] = type;
    }

    private void appendDangerousAdjacentCells(CellType[][] cells, int x, int y) {
        tryAppendCell(cells, CellType.DANGEROUS, x + 1, y);
        tryAppendCell(cells, CellType.DANGEROUS, x - 1, y);
        tryAppendCell(cells, CellType.DANGEROUS, x, y + 1);
        tryAppendCell(cells, CellType.DANGEROUS, x, y - 1);
    }

    private void appendDangerousCornerCells(CellType[][] cells, int x, int y) {
        tryAppendCell(cells, CellType.DANGEROUS, x + 1, y + 1);
        tryAppendCell(cells, CellType.DANGEROUS, x - 1, y + 1);
        tryAppendCell(cells, CellType.DANGEROUS, x + 1, y - 1);
        tryAppendCell(cells, CellType.DANGEROUS, x - 1, y - 1);
    }

    BoardGenerator appendChest(int x, int y) {
        tryAppendCell(cells, CellType.CHEST, x, y);
        return this;
    }

    BoardGenerator appendTortuga(int x, int y) {
        tryAppendCell(cells, CellType.TORTUGA, x, y);
        return this;
    }

    BoardGenerator appendRock(int x, int y) {
        tryAppendCell(cells, CellType.ROCK, x, y);
        return this;
    }

    BoardGenerator appendKraken(int x, int y) {
        tryAppendCell(cells, CellType.KRAKEN, x, y);
        appendDangerousAdjacentCells(cells, x, y);
        return this;
    }

    BoardGenerator appendDavyJones(int x, int y) {
        tryAppendCell(cells, CellType.DAVY_JONES, x, y);
        appendDangerousAdjacentCells(cells, x, y);
        appendDangerousCornerCells(cells, x, y);
        return this;
    }

    CellType[][] buildBoard() {
        return cells;
    }
}

class InputReader {
    private final List<String> inputData;
    private final int xDimension;
    private final int yDimension;

    public InputReader(Path inputPath, int xDimension, int yDimension) throws IOException {
        this.xDimension = xDimension;
        this.yDimension = yDimension;

        try(var stream = Files.lines(inputPath)) {
            this.inputData = stream.toList();

            if (inputData.size() != 2)
                throw new IOException("Number of input lines is less than 2");
        }
    }

    List<Coordinates> readCoordinates() throws IOException {
        var coordinates = Arrays.stream(inputData.get(0).split("\\s+"))
                .filter(x -> x.matches("\\[\\d,\\d]"))
                .map(x -> {
                    var split = x.replaceAll("[\\[\\]]+", "").split(",");
                    return new Coordinates(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                })
                .filter(coordinate -> coordinate.x() >= 0 && coordinate.x() < xDimension)
                .filter(coordinate -> coordinate.y() >= 0 && coordinate.y() < yDimension)
                .toList();

        if (coordinates.size() != 6)
            throw new IOException("Invalid coordinates");

        var distinctCoordinatesSize = new HashSet<>(coordinates).size();

        if (distinctCoordinatesSize < 5)
            throw new IOException("Invalid coordinates");

        if (distinctCoordinatesSize == 5) {
            if (!coordinates.get(2).equals(coordinates.get(3)))
                throw new IOException("Invalid coordinates");
        }

        return coordinates;
    }

    int readScenario() throws IOException {
        var scenario = Integer.parseInt(inputData.get(1));
        if (scenario == 1 || scenario == 2)
            return scenario;
        throw new IOException("Invalid scenario");
    }
}