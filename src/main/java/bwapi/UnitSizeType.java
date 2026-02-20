package bwapi;

/**
 * Size types are used by unit types in Broodwar to determine how much damage will be
 * applied.
 * <p>
 * This corresponds with {@link DamageType} for several different damage reduction
 * applications.
 *
 * @see DamageType
 * @see UnitType
 * @see UnitSizeType
 * <p>
 * [View on Starcraft Campendium (Official Website)](http://classic.battle.net/scc/gs/damage.shtml)<br>
 */
public enum UnitSizeType {
    Independent(0),
    Small(1),
    Medium(2),
    Large(3),
    None(4),
    Unknown(5);

    // Legacy proxy compatibility aliases.
    public static final UnitSizeType INDEPENDENT = Independent;
    public static final UnitSizeType SMALL = Small;
    public static final UnitSizeType MEDIUM = Medium;
    public static final UnitSizeType LARGE = Large;
    public static final UnitSizeType NONE = None;
    public static final UnitSizeType UNKNOWN = Unknown;

    public final int id;

    UnitSizeType(final int id) {
        this.id = id;
    }

    public int getID() {
        return id;
    }
}
