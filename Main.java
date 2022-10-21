import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        try {
            var inputHelper = new InputHelper(Path.of("input.txt"));
            var coordinates = inputHelper.getCoordinates();
            var scenario = inputHelper.getScenario();
            var cells = new Cells(coordinates);
            cells.removeKraken();

            System.out.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

enum CellType {
    FREE,
    KRAKEN,
    ROCK,
    KRAKEN_ROCK,
    DANGEROUS,
    DAVY_JONES,
    TORTUGA,
    CHEST
}

class Coordinates {
    private int x;
    private int y;

    public Coordinates(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}

class Cells {
    private final CellType[][] cells;

    private Coordinates jackSparrow;

    private Coordinates davyJones;
    private Coordinates kraken;

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

    private void validateAndSetCell(CellType type, int x, int y, CellType... exclusions) {
        if (!trySetCell(type, x, y, exclusions))
            throw new IllegalArgumentException("Invalid coordinates of " + type);
    }

    private void setNeighbors(CellType type, int x, int y, CellType... exclusions) {
        trySetCell(type, x + 1, y, exclusions);
        trySetCell(type, x - 1, y, exclusions);
        trySetCell(type, x, y + 1, exclusions);
        trySetCell(type, x, y - 1, exclusions);
    }

    private void setDangerousCorners(int x, int y, CellType... exclusions) {
        trySetCell(CellType.DANGEROUS, x + 1, y + 1, exclusions);
        trySetCell(CellType.DANGEROUS, x + 1, y - 1, exclusions);
        trySetCell(CellType.DANGEROUS, x - 1, y + 1, exclusions);
        trySetCell(CellType.DANGEROUS, x - 1, y - 1, exclusions);
    }

    private void setDavyJones(int x, int y) {
        validateAndSetCell(CellType.DAVY_JONES, x, y, davyJonesExclusions);
        setNeighbors(CellType.DANGEROUS, x, y, dangerousExclusions);
        setDangerousCorners(x, y, dangerousExclusions);

        davyJones = new Coordinates(x, y);
    }

    private void setKraken(int x, int y) {
        kraken = new Coordinates(x, y);
        validateAndSetCell(CellType.KRAKEN, x, y, krakenExclusions);
        setNeighbors(CellType.DANGEROUS, x, y, dangerousExclusions);
    }

    private void setRock(int x, int y) {
        var type = cells[y][x] == CellType.KRAKEN ? CellType.KRAKEN_ROCK : CellType.ROCK;
        validateAndSetCell(type, x, y, rockExclusions);
    }

    private void setChest(int x, int y) {
        validateAndSetCell(CellType.CHEST, x, y, chestExclusions);
    }

    private void setTortuga(int x, int y) {
        validateAndSetCell(CellType.TORTUGA, x, y, tortugaExclusions);
    }

    private void setJackSparrow(int x, int y) {
        if (getCell(x, y).isEmpty())
            throw new IllegalArgumentException("Invalid Jack Sparrow coordinates");
        else {
            var currentCell = getCell(x, y).get();
            if (Stream.of(CellType.KRAKEN, CellType.ROCK, CellType.DAVY_JONES).anyMatch(cell -> cell == currentCell))
                throw new IllegalArgumentException("Invalid Jack Sparrow coordinates");
        }

        if (jackSparrow == null) jackSparrow = new Coordinates(x, y);

        jackSparrow.setX(x);
        jackSparrow.setY(y);
    }

    public Cells(List<Coordinates> coordinates) {
        Supplier<CellType[]> rowGenerator = () -> Stream.generate(() -> CellType.FREE)
                .limit(9)
                .toArray(CellType[]::new);

        cells = Stream.generate(rowGenerator)
                .limit(9)
                .toArray(x -> new CellType[9][9]);

        setDavyJones(coordinates.get(1).getX(), coordinates.get(1).getY());
        setKraken(coordinates.get(2).getX(), coordinates.get(2).getY());
        setRock(coordinates.get(3).getX(), coordinates.get(3).getY());
        setChest(coordinates.get(4).getX(), coordinates.get(4).getY());
        setTortuga(coordinates.get(5).getX(), coordinates.get(5).getY());

        setJackSparrow(coordinates.get(0).getX(), coordinates.get(0).getY());
    }

    void removeKraken() {
        var type = cells[kraken.getY()][kraken.getX()] == CellType.KRAKEN_ROCK
                ? CellType.ROCK
                : CellType.FREE;

        validateAndSetCell(type, kraken.getX(), kraken.getY(), freeExclusions);
        setNeighbors(CellType.FREE, kraken.getX(), kraken.getY(), freeExclusions);

        // Update perception zones of Dave Jones
        setNeighbors(CellType.DANGEROUS, davyJones.getX(), davyJones.getY(), dangerousExclusions);
        setDangerousCorners(davyJones.getX(), davyJones.getY(), dangerousExclusions);
    }

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
}

class InputHelper {
    private List<String> inputData;
    private List<Coordinates> coordinates;
    private int scenario;

    private void tryInitStreams(Path inputPath) {
        try {
            inputData = Files.readAllLines(inputPath);

            if (inputData.size() < 2)
                throw new IOException("Number of input file lines is < 2");

            readCoordinates();
            readScenario();
        } catch (IOException ignored) {
            try (var console = new BufferedReader(new InputStreamReader(System.in))) {
                System.out.println("Enter the input data:");
                inputData = Stream.of(console.readLine(), console.readLine()).toList();

                if (inputData.size() < 2)
                    throw new IOException("Number of input console lines is < 2");

                readCoordinates();
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

    private void readCoordinates() throws IOException {
        coordinates = Arrays.stream(inputData.get(0).split("\\s+"))
                .filter(x -> x.matches("\\[\\d,\\d]"))
                .map(x -> {
                    var split = x.replaceAll("[\\[\\]]+", "").split(",");
                    return new Coordinates(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                })
                .filter(coordinate -> coordinate.getX() >= 0 && coordinate.getX() < 9)
                .filter(coordinate -> coordinate.getY() >= 0 && coordinate.getY() < 9)
                .toList();

        if (coordinates.size() != 6)
            throw new IOException("Invalid coordinates");
    }

    private void readScenario() throws IOException {
        var scenario = Integer.parseInt(inputData.get(1));
        if (scenario != 1 && scenario != 2)
            throw new IOException("Invalid scenario");
    }

    public List<Coordinates> getCoordinates() {
        return coordinates;
    }

    public int getScenario() {
        return scenario;
    }
}