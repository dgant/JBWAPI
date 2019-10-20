package bwapi;

import java.util.Arrays;

/**
 * An enum that represents game types in Broodwar.
 * <p>
 * A game type is selected when creating a game.
 */
public enum GameType {
    None(0),
    Custom(1),          // Warcraft III
    Melee(2),
    Free_For_All(3),
    One_on_One(4),
    Capture_The_Flag(5),
    Greed(6),
    Slaughter(7),
    Sudden_Death(8),
    Ladder(9),
    Use_Map_Settings(10),
    Team_Melee(11),
    Team_Free_For_All(12),
    Team_Capture_The_Flag(13),
    Unknown_0x0E(14),
    Top_vs_Bottom(15),
    Iron_Man_Ladder(16),    // Warcraft II

    Pro_Gamer_League(32),  // Not valid
    Unknown(33);

    static final GameType[] idToEnum = new GameType[33 + 1];

    static {
        Arrays.stream(GameType.values()).forEach(v -> idToEnum[v.id] = v);
    }


    final int id;

    GameType(final int id) {
        this.id = id;
    }
}
