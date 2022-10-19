import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {

    }
}

enum CellType {
    PLAYER,
    KRAKEN,
    DANGEROUS,
    BARREL,

    TREASURE
}

record Cell(CellType type, int x, int y) {
}

record Board(Cell[][] cells, Cell player, Cell exit) {

    Optional<Cell> getCell(int x, int y) {
        if (y < 0 || y >= cells.length) return Optional.empty();
        if (x < 0 || x >= cells.length) return Optional.empty();
        return Optional.ofNullable(cells[y][x]);
    }

    Set<Cell> getAdjacentCells(int x, int y) {
        return Stream.of(getCell(x, ++y), getCell(x, --y), getCell(++x, y), getCell(--x, y))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    Set<Cell> getCornerCells(int x, int y) {
        return Stream.of(getCell(++x, ++y), getCell(--x, ++y), getCell(++x, --y), getCell(--x, --y))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }
}

/*
TODO

Board
Cell
Kraken
DavyJones
Barrel
*/