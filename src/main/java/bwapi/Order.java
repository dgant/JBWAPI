package bwapi;

import java.util.Arrays;

/**
 * An Order (Order type) represents a Unit's current action and can be retrieved with
 * {@link Unit#getOrder}.
 * <p>
 * It can also be used to identify the current state of the
 * unit during command execution (gathering minerals can consist of {@link #MoveToMinerals},
 * {@link #WaitForMinerals}, {@link #MiningMinerals}, etc.).
 */
public enum Order {
    Die(0),
    Stop(1),
    Guard(2),
    PlayerGuard(3),
    TurretGuard(4),
    BunkerGuard(5),
    Move(6),
    ReaverStop(7),
    Attack1(8),
    Attack2(9),
    AttackUnit(10),
    AttackFixedRange(11),
    AttackTile(12),
    Hover(13),
    AttackMove(14),
    InfestedCommandCenter(15),
    UnusedNothing(16),
    UnusedPowerup(17),
    TowerGuard(18),
    TowerAttack(19),
    VultureMine(20),
    StayInRange(21),
    TurretAttack(22),
    Nothing(23),
    Unused_24(24),
    DroneStartBuild(25),
    DroneBuild(26),
    CastInfestation(27),
    MoveToInfest(28),
    InfestingCommandCenter(29),
    PlaceBuilding(30),
    PlaceProtossBuilding(31),
    CreateProtossBuilding(32),
    ConstructingBuilding(33),
    Repair(34),
    MoveToRepair(35),
    PlaceAddon(36),
    BuildAddon(37),
    Train(38),
    RallyPointUnit(39),
    RallyPointTile(40),
    ZergBirth(41),
    ZergUnitMorph(42),
    ZergBuildingMorph(43),
    IncompleteBuilding(44),
    IncompleteMorphing(45),
    BuildNydusExit(46),
    EnterNydusCanal(47),
    IncompleteWarping(48),
    Follow(49),
    Carrier(50),
    ReaverCarrierMove(51),
    CarrierStop(52),
    CarrierAttack(53),
    CarrierMoveToAttack(54),
    CarrierIgnore2(55),
    CarrierFight(56),
    CarrierHoldPosition(57),
    Reaver(58),
    ReaverAttack(59),
    ReaverMoveToAttack(60),
    ReaverFight(61),
    ReaverHoldPosition(62),
    TrainFighter(63),
    InterceptorAttack(64),
    ScarabAttack(65),
    RechargeShieldsUnit(66),
    RechargeShieldsBattery(67),
    ShieldBattery(68),
    InterceptorReturn(69),
    DroneLand(70),
    BuildingLand(71),
    BuildingLiftOff(72),
    DroneLiftOff(73),
    LiftingOff(74),
    ResearchTech(75),
    Upgrade(76),
    Larva(77),
    SpawningLarva(78),
    Harvest1(79),
    Harvest2(80),
    MoveToGas(81),
    WaitForGas(82),
    HarvestGas(83),
    ReturnGas(84),
    MoveToMinerals(85),
    WaitForMinerals(86),
    MiningMinerals(87),
    Harvest3(88),
    Harvest4(89),
    ReturnMinerals(90),
    Interrupted(91),
    EnterTransport(92),
    PickupIdle(93),
    PickupTransport(94),
    PickupBunker(95),
    Pickup4(96),
    PowerupIdle(97),
    Sieging(98),
    Unsieging(99),
    WatchTarget(100),
    InitCreepGrowth(101),
    SpreadCreep(102),
    StoppingCreepGrowth(103),
    GuardianAspect(104),
    ArchonWarp(105),
    CompletingArchonSummon(106),
    HoldPosition(107),
    QueenHoldPosition(108),
    Cloak(109),
    Decloak(110),
    Unload(111),
    MoveUnload(112),
    FireYamatoGun(113),
    MoveToFireYamatoGun(114),
    CastLockdown(115),
    Burrowing(116),
    Burrowed(117),
    Unburrowing(118),
    CastDarkSwarm(119),
    CastParasite(120),
    CastSpawnBroodlings(121),
    CastEMPShockwave(122),
    NukeWait(123),
    NukeTrain(124),
    NukeLaunch(125),
    NukePaint(126),
    NukeUnit(127),
    CastNuclearStrike(128),
    NukeTrack(129),
    InitializeArbiter(130),
    CloakNearbyUnits(131),
    PlaceMine(132),
    RightClickAction(133),
    SuicideUnit(134),
    SuicideLocation(135),
    SuicideHoldPosition(136),
    CastRecall(137),
    Teleport(138),
    CastScannerSweep(139),
    Scanner(140),
    CastDefensiveMatrix(141),
    CastPsionicStorm(142),
    CastIrradiate(143),
    CastPlague(144),
    CastConsume(145),
    CastEnsnare(146),
    CastStasisField(147),
    CastHallucination(148),
    Hallucination2(149),
    ResetCollision(150),
    ResetHarvestCollision(151),
    Patrol(152),
    CTFCOPInit(153),
    CTFCOPStarted(154),
    CTFCOP2(155),
    ComputerAI(156),
    AtkMoveEP(157),
    HarassMove(158),
    AIPatrol(159),
    GuardPost(160),
    RescuePassive(161),
    Neutral(162),
    ComputerReturn(163),
    InitializePsiProvider(164),
    SelfDestructing(165),
    Critter(166),
    HiddenGun(167),
    OpenDoor(168),
    CloseDoor(169),
    HideTrap(170),
    RevealTrap(171),
    EnableDoodad(172),
    DisableDoodad(173),
    WarpIn(174),
    Medic(175),
    MedicHeal(176),
    HealMove(177),
    MedicHoldPosition(178),
    MedicHealToIdle(179),
    CastRestoration(180),
    CastDisruptionWeb(181),
    CastMindControl(182),
    DarkArchonMeld(183),
    CastFeedback(184),
    CastOpticalFlare(185),
    CastMaelstrom(186),
    JunkYardDog(187),
    Fatal(188),
    None(189),
    Unknown(190);

    // Legacy proxy compatibility aliases used by historical bots.
    public static final Order RETURN_GAS = ReturnGas;
    public static final Order RETURN_MINERALS = ReturnMinerals;
    public static final Order BUILD_PROTOSS_1 = PlaceProtossBuilding;
    public static final Order BUILD_PROTOSS_2 = CreateProtossBuilding;
    public static final Order RESET_COLLISION_1 = ResetCollision;
    public static final Order RESET_COLLISION_2 = ResetHarvestCollision;
    public static final Order BUILD_TERRAN = PlaceBuilding;
    public static final Order ZERG_BUILDING_MORPH = ZergBuildingMorph;
    public static final Order ZERG_UNIT_MORPH = ZergUnitMorph;
    public static final Order DRONE_BUILD = DroneBuild;
    public static final Order DRONE_LAND = DroneLand;
    public static final Order DRONE_START_BUILD = DroneStartBuild;
    public static final Order DRONE_LIFT_OFF = DroneLiftOff;
    public static final Order ZERG_BIRTH = ZergBirth;
    public static final Order ZERG_BUILD_SELF = ConstructingBuilding;
    public static final Order ATTACK_MOVE = AttackMove;
    public static final Order ATTACK_1 = Attack1;
    public static final Order ATTACK_2 = Attack2;
    public static final Order ATTACK_FIXED_RANGE = AttackFixedRange;
    public static final Order ATTACK_UNIT = AttackUnit;
    public static final Order ATTACK_TILE = AttackTile;
    public static final Order CARRIER_HOLD_POSITION = CarrierHoldPosition;
    public static final Order REAVER_HOLD_POSITION = ReaverHoldPosition;
    public static final Order HOLD_POSITION = HoldPosition;
    public static final Order GUARD_POST = GuardPost;
    public static final Order NOTHING_3 = Nothing;
    public static final Order Nothing_3 = Nothing;

    public static final Order DIE = Die;
    public static final Order STOP = Stop;
    public static final Order GUARD = Guard;
    public static final Order PLAYERGUARD = PlayerGuard;
    public static final Order TURRETGUARD = TurretGuard;
    public static final Order BUNKERGUARD = BunkerGuard;
    public static final Order MOVE = Move;
    public static final Order REAVERSTOP = ReaverStop;
    public static final Order ATTACK1 = Attack1;
    public static final Order ATTACK2 = Attack2;
    public static final Order ATTACKUNIT = AttackUnit;
    public static final Order ATTACKFIXEDRANGE = AttackFixedRange;
    public static final Order ATTACKTILE = AttackTile;
    public static final Order HOVER = Hover;
    public static final Order ATTACKMOVE = AttackMove;
    public static final Order INFESTEDCOMMANDCENTER = InfestedCommandCenter;
    public static final Order UNUSEDNOTHING = UnusedNothing;
    public static final Order UNUSEDPOWERUP = UnusedPowerup;
    public static final Order TOWERGUARD = TowerGuard;
    public static final Order TOWERATTACK = TowerAttack;
    public static final Order VULTUREMINE = VultureMine;
    public static final Order STAYINRANGE = StayInRange;
    public static final Order TURRETATTACK = TurretAttack;
    public static final Order NOTHING = Nothing;
    public static final Order UNUSED_24 = Unused_24;
    public static final Order DRONESTARTBUILD = DroneStartBuild;
    public static final Order DRONEBUILD = DroneBuild;
    public static final Order CASTINFESTATION = CastInfestation;
    public static final Order MOVETOINFEST = MoveToInfest;
    public static final Order INFESTINGCOMMANDCENTER = InfestingCommandCenter;
    public static final Order PLACEBUILDING = PlaceBuilding;
    public static final Order PLACEPROTOSSBUILDING = PlaceProtossBuilding;
    public static final Order CREATEPROTOSSBUILDING = CreateProtossBuilding;
    public static final Order CONSTRUCTINGBUILDING = ConstructingBuilding;
    public static final Order REPAIR = Repair;
    public static final Order MOVETOREPAIR = MoveToRepair;
    public static final Order PLACEADDON = PlaceAddon;
    public static final Order BUILDADDON = BuildAddon;
    public static final Order TRAIN = Train;
    public static final Order RALLYPOINTUNIT = RallyPointUnit;
    public static final Order RALLYPOINTTILE = RallyPointTile;
    public static final Order ZERGBIRTH = ZergBirth;
    public static final Order ZERGUNITMORPH = ZergUnitMorph;
    public static final Order ZERGBUILDINGMORPH = ZergBuildingMorph;
    public static final Order INCOMPLETEBUILDING = IncompleteBuilding;
    public static final Order INCOMPLETEMORPHING = IncompleteMorphing;
    public static final Order BUILDNYDUSEXIT = BuildNydusExit;
    public static final Order ENTERNYDUSCANAL = EnterNydusCanal;
    public static final Order INCOMPLETEWARPING = IncompleteWarping;
    public static final Order FOLLOW = Follow;
    public static final Order CARRIER = Carrier;
    public static final Order REAVERCARRIERMOVE = ReaverCarrierMove;
    public static final Order CARRIERSTOP = CarrierStop;
    public static final Order CARRIERATTACK = CarrierAttack;
    public static final Order CARRIERMOVETOATTACK = CarrierMoveToAttack;
    public static final Order CARRIERIGNORE2 = CarrierIgnore2;
    public static final Order CARRIERFIGHT = CarrierFight;
    public static final Order CARRIERHOLDPOSITION = CarrierHoldPosition;
    public static final Order REAVER = Reaver;
    public static final Order REAVERATTACK = ReaverAttack;
    public static final Order REAVERMOVETOATTACK = ReaverMoveToAttack;
    public static final Order REAVERFIGHT = ReaverFight;
    public static final Order REAVERHOLDPOSITION = ReaverHoldPosition;
    public static final Order TRAINFIGHTER = TrainFighter;
    public static final Order INTERCEPTORATTACK = InterceptorAttack;
    public static final Order SCARABATTACK = ScarabAttack;
    public static final Order RECHARGESHIELDSUNIT = RechargeShieldsUnit;
    public static final Order RECHARGESHIELDSBATTERY = RechargeShieldsBattery;
    public static final Order SHIELDBATTERY = ShieldBattery;
    public static final Order INTERCEPTORRETURN = InterceptorReturn;
    public static final Order DRONELAND = DroneLand;
    public static final Order BUILDINGLAND = BuildingLand;
    public static final Order BUILDINGLIFTOFF = BuildingLiftOff;
    public static final Order DRONELIFTOFF = DroneLiftOff;
    public static final Order LIFTINGOFF = LiftingOff;
    public static final Order RESEARCHTECH = ResearchTech;
    public static final Order UPGRADE = Upgrade;
    public static final Order LARVA = Larva;
    public static final Order SPAWNINGLARVA = SpawningLarva;
    public static final Order HARVEST1 = Harvest1;
    public static final Order HARVEST2 = Harvest2;
    public static final Order MOVETOGAS = MoveToGas;
    public static final Order WAITFORGAS = WaitForGas;
    public static final Order HARVESTGAS = HarvestGas;
    public static final Order RETURNGAS = ReturnGas;
    public static final Order MOVETOMINERALS = MoveToMinerals;
    public static final Order WAITFORMINERALS = WaitForMinerals;
    public static final Order MININGMINERALS = MiningMinerals;
    public static final Order HARVEST3 = Harvest3;
    public static final Order HARVEST4 = Harvest4;
    public static final Order RETURNMINERALS = ReturnMinerals;
    public static final Order INTERRUPTED = Interrupted;
    public static final Order ENTERTRANSPORT = EnterTransport;
    public static final Order PICKUPIDLE = PickupIdle;
    public static final Order PICKUPTRANSPORT = PickupTransport;
    public static final Order PICKUPBUNKER = PickupBunker;
    public static final Order PICKUP4 = Pickup4;
    public static final Order POWERUPIDLE = PowerupIdle;
    public static final Order SIEGING = Sieging;
    public static final Order UNSIEGING = Unsieging;
    public static final Order WATCHTARGET = WatchTarget;
    public static final Order INITCREEPGROWTH = InitCreepGrowth;
    public static final Order SPREADCREEP = SpreadCreep;
    public static final Order STOPPINGCREEPGROWTH = StoppingCreepGrowth;
    public static final Order GUARDIANASPECT = GuardianAspect;
    public static final Order ARCHONWARP = ArchonWarp;
    public static final Order COMPLETINGARCHONSUMMON = CompletingArchonSummon;
    public static final Order HOLDPOSITION = HoldPosition;
    public static final Order QUEENHOLDPOSITION = QueenHoldPosition;
    public static final Order CLOAK = Cloak;
    public static final Order DECLOAK = Decloak;
    public static final Order UNLOAD = Unload;
    public static final Order MOVEUNLOAD = MoveUnload;
    public static final Order FIREYAMATOGUN = FireYamatoGun;
    public static final Order MOVETOFIREYAMATOGUN = MoveToFireYamatoGun;
    public static final Order CASTLOCKDOWN = CastLockdown;
    public static final Order BURROWING = Burrowing;
    public static final Order BURROWED = Burrowed;
    public static final Order UNBURROWING = Unburrowing;
    public static final Order CASTDARKSWARM = CastDarkSwarm;
    public static final Order CASTPARASITE = CastParasite;
    public static final Order CASTSPAWNBROODLINGS = CastSpawnBroodlings;
    public static final Order CASTEMPSHOCKWAVE = CastEMPShockwave;
    public static final Order NUKEWAIT = NukeWait;
    public static final Order NUKETRAIN = NukeTrain;
    public static final Order NUKELAUNCH = NukeLaunch;
    public static final Order NUKEPAINT = NukePaint;
    public static final Order NUKEUNIT = NukeUnit;
    public static final Order CASTNUCLEARSTRIKE = CastNuclearStrike;
    public static final Order NUKETRACK = NukeTrack;
    public static final Order INITIALIZEARBITER = InitializeArbiter;
    public static final Order CLOAKNEARBYUNITS = CloakNearbyUnits;
    public static final Order PLACEMINE = PlaceMine;
    public static final Order RIGHTCLICKACTION = RightClickAction;
    public static final Order SUICIDEUNIT = SuicideUnit;
    public static final Order SUICIDELOCATION = SuicideLocation;
    public static final Order SUICIDEHOLDPOSITION = SuicideHoldPosition;
    public static final Order CASTRECALL = CastRecall;
    public static final Order TELEPORT = Teleport;
    public static final Order CASTSCANNERSWEEP = CastScannerSweep;
    public static final Order SCANNER = Scanner;
    public static final Order CASTDEFENSIVEMATRIX = CastDefensiveMatrix;
    public static final Order CASTPSIONICSTORM = CastPsionicStorm;
    public static final Order CASTIRRADIATE = CastIrradiate;
    public static final Order CASTPLAGUE = CastPlague;
    public static final Order CASTCONSUME = CastConsume;
    public static final Order CASTENSNARE = CastEnsnare;
    public static final Order CASTSTASISFIELD = CastStasisField;
    public static final Order CASTHALLUCINATION = CastHallucination;
    public static final Order HALLUCINATION2 = Hallucination2;
    public static final Order RESETCOLLISION = ResetCollision;
    public static final Order RESETHARVESTCOLLISION = ResetHarvestCollision;
    public static final Order PATROL = Patrol;
    public static final Order CTFCOPINIT = CTFCOPInit;
    public static final Order CTFCOPSTARTED = CTFCOPStarted;
    public static final Order COMPUTERAI = ComputerAI;
    public static final Order ATKMOVEEP = AtkMoveEP;
    public static final Order HARASSMOVE = HarassMove;
    public static final Order AIPATROL = AIPatrol;
    public static final Order GUARDPOST = GuardPost;
    public static final Order RESCUEPASSIVE = RescuePassive;
    public static final Order NEUTRAL = Neutral;
    public static final Order COMPUTERRETURN = ComputerReturn;
    public static final Order INITIALIZEPSIPROVIDER = InitializePsiProvider;
    public static final Order SELFDESTRUCTING = SelfDestructing;
    public static final Order CRITTER = Critter;
    public static final Order HIDDENGUN = HiddenGun;
    public static final Order OPENDOOR = OpenDoor;
    public static final Order CLOSEDOOR = CloseDoor;
    public static final Order HIDETRAP = HideTrap;
    public static final Order REVEALTRAP = RevealTrap;
    public static final Order ENABLEDOODAD = EnableDoodad;
    public static final Order DISABLEDOODAD = DisableDoodad;
    public static final Order WARPIN = WarpIn;
    public static final Order MEDIC = Medic;
    public static final Order MEDICHEAL = MedicHeal;
    public static final Order HEALMOVE = HealMove;
    public static final Order MEDICHOLDPOSITION = MedicHoldPosition;
    public static final Order MEDICHEALTOIDLE = MedicHealToIdle;
    public static final Order CASTRESTORATION = CastRestoration;
    public static final Order CASTDISRUPTIONWEB = CastDisruptionWeb;
    public static final Order CASTMINDCONTROL = CastMindControl;
    public static final Order DARKARCHONMELD = DarkArchonMeld;
    public static final Order CASTFEEDBACK = CastFeedback;
    public static final Order CASTOPTICALFLARE = CastOpticalFlare;
    public static final Order CASTMAELSTROM = CastMaelstrom;
    public static final Order JUNKYARDDOG = JunkYardDog;
    public static final Order FATAL = Fatal;
    public static final Order NONE = None;

    static final Order[] idToEnum = new Order[190 + 1];

    static {
        Arrays.stream(Order.values()).forEach(v -> idToEnum[v.id] = v);
    }

    public final int id;

    Order(final int id) {
        this.id = id;
    }

    public String getName() {
        return name();
    }

    public int getID() {
        return id;
    }
}
