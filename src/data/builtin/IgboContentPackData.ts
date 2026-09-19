import { ContentPack, BlockType, Biome, LoreEntry, DamageType, Affix, Insert } from '../../domain/models/ContentPack';
import { HeroClassDefinition } from '../../domain/models/HeroClass';
import { WeaponDefinition } from '../../domain/models/Weapon';

export const IGBO_PALETTE = {
  surface: '#14110E',
  surfaceRaised: '#211C16',
  surfaceSunken: '#0D0B09',
  ink: '#F4EBDC',
  inkMuted: '#A1907A',
  accent: '#CD7F32',     // Bronze
  accentAlt: '#00B8A9',  // Verdigris
  danger: '#C1453B',     // Laterite crimson
  bevel: 'rgba(244, 235, 220, 0.10)',
  hairline: 'rgba(244, 235, 220, 0.16)',
};

export const IGBO_BLOCKS: BlockType[] = [
  { id: 'igbo:red_earth', displayName: 'Red Earth', topColor: '#8E3E2B', sideColor: '#6B2B1B', hardness: 1, glyph: '■', description: 'Rich red laterite loam of Ala Igbo' },
  { id: 'igbo:river_clay', displayName: 'River Clay', topColor: '#5C4A3B', sideColor: '#423326', hardness: 2, glyph: '░', description: 'Dense alluvial mud suitable for pottery and bricks' },
  { id: 'igbo:granite_stone', displayName: 'Granite Stone', topColor: '#6A635B', sideColor: '#4C4640', hardness: 3, glyph: '▣', description: 'Subterranean bedrock beneath ancient shrine mounds' },
  { id: 'igbo:bronze_ore', displayName: 'Igbo-Ukwu Bronze Ore', topColor: '#CD7F32', sideColor: '#9C5B1C', hardness: 4, glyph: '✦', description: 'Vein rich in copper and tin, waiting for the forge' },
  { id: 'igbo:obsidian_crag', displayName: 'Obsidian Crag', topColor: '#2B2729', sideColor: '#1A1819', hardness: 5, glyph: '▲', description: 'Glassy rock formed where lightning struck molten stone' },
  { id: 'igbo:nsibidi_seal', displayName: 'Nsibidi Seal Stone', topColor: '#1F3A3B', sideColor: '#142728', hardness: 4, glyph: '§', description: 'Ancient boundary marker inscribed with protected symbols' },
  { id: 'igbo:grove_turf', displayName: 'Sacred Grove Turf', topColor: '#2E5A36', sideColor: '#1F3D24', hardness: 1, glyph: '☘', description: 'Mossy earth under the canopy of centenary Iroko' },
  { id: 'igbo:catacomb_masonry', displayName: 'Catacomb Masonry', topColor: '#4A3E38', sideColor: '#332A25', hardness: 3, glyph: '▤', description: 'Hewn stone from old burial vaults of ancient titled men' },
];

export const IGBO_BIOMES: Biome[] = [
  { id: 'igbo:savannah', name: 'Red Earth Savannah', description: 'Rolling plateaus of red earth under the midday heat.', surfaceBlock: 'igbo:red_earth', ambientColor: '#2B1A14' },
  { id: 'igbo:sacred_grove', name: 'Sacred Grove of Idemili', description: 'Deep shade where sacred pythons glide and Iroko roots drink deep.', surfaceBlock: 'igbo:grove_turf', ambientColor: '#172B1E' },
  { id: 'igbo:thunder_peak', name: 'Thunder Peak of Amadioha', description: 'High crags echoing with lightning and storm resonance.', surfaceBlock: 'igbo:obsidian_crag', ambientColor: '#1A1E2B' },
  { id: 'igbo:sunken_shrines', name: 'Nri Sunken Shrines', description: 'Catacombs of carved stone and ancient wisdom.', surfaceBlock: 'igbo:granite_stone', ambientColor: '#2B2418' },
  { id: 'igbo:copper_vein', name: 'Deep Bronze Mine', description: 'Rich veins of molten memory and raw bronze ore.', surfaceBlock: 'igbo:bronze_ore', ambientColor: '#301F12' },
];

export const IGBO_CLASSES: HeroClassDefinition[] = [
  {
    id: 'igbo:dike_ozo',
    name: 'Dike Ozo',
    title: 'Titled Bladesman',
    description: 'A titled warrior of the Ozo society who answers a problem with reach and a wide arc. Digs well, dies slowly.',
    baseHealth: 260,
    baseResource: 90,
    resourceName: 'Resolve',
    strength: 16,
    agility: 11,
    insight: 8,
    startingBlockIds: ['igbo:red_earth', 'igbo:granite_stone'],
    abilityIds: ['igbo:mma_cleave', 'igbo:ikenga_tremor'],
    baseStats: {
      maxHealth: 260,
      attackPower: 16,
      armour: 6,
      critChance: 0.08,
      critMultiplier: 1.5,
      attackSpeed: 1.0,
      attackRange: 1.5,
    },
    startingWeaponId: 'weapon:mma_nkwu',
  },
  {
    id: 'igbo:amadioha_invoker',
    name: 'Amadioha Invoker',
    title: 'Caller of the Storm',
    description: 'Pulls the storm down onto a line of ground and everything standing on it. Fragile until lightning strikes.',
    baseHealth: 180,
    baseResource: 160,
    resourceName: 'Charge',
    strength: 8,
    agility: 12,
    insight: 18,
    startingBlockIds: ['igbo:obsidian_crag'],
    abilityIds: ['igbo:thunder_spear', 'igbo:shockwave_spark'],
    baseStats: {
      maxHealth: 180,
      attackPower: 20,
      armour: 2,
      critChance: 0.14,
      critMultiplier: 1.75,
      attackSpeed: 1.1,
      attackRange: 2.8,
    },
    startingWeaponId: 'weapon:bronze_spear',
  },
  {
    id: 'igbo:dibia_nzu',
    name: 'Dibia Nzu',
    title: 'Chalk Diviner',
    description: 'Reads the ground before breaking it. Sees ore through stone and turns venom back on whoever sent it.',
    baseHealth: 200,
    baseResource: 140,
    resourceName: 'Communion',
    strength: 9,
    agility: 13,
    insight: 17,
    startingBlockIds: ['igbo:nsibidi_seal', 'igbo:grove_turf'],
    abilityIds: ['igbo:venom_geyser', 'igbo:solar_supernova'],
    baseStats: {
      maxHealth: 200,
      attackPower: 17,
      armour: 4,
      critChance: 0.10,
      critMultiplier: 1.6,
      attackSpeed: 1.0,
      attackRange: 2.5,
      lifeSteal: 0.05,
    },
    startingWeaponId: 'weapon:ofo_staff',
  },
  {
    id: 'igbo:ikenga_berserker',
    name: 'Ikenga Berserker',
    title: 'Horned Right Hand',
    description: 'Trades armour for speed and swings until the room is quiet. The fastest hands underground.',
    baseHealth: 300,
    baseResource: 70,
    resourceName: 'Fury',
    strength: 19,
    agility: 15,
    insight: 6,
    startingBlockIds: ['igbo:catacomb_masonry'],
    abilityIds: ['igbo:ikenga_tremor', 'igbo:mma_cleave'],
    baseStats: {
      maxHealth: 300,
      attackPower: 22,
      armour: 2,
      critChance: 0.15,
      critMultiplier: 1.85,
      attackSpeed: 1.3,
      attackRange: 1.4,
    },
    startingWeaponId: 'weapon:ikenga_axe',
  }
];

export const IGBO_WEAPONS: WeaponDefinition[] = [
  {
    id: 'weapon:mma_nkwu',
    name: 'Mma Nkwu Cleaver',
    kind: 'cleaver',
    glyph: '⚔',
    attackPower: 18,
    critChance: 0.10,
    attackSpeed: 1.0,
    sockets: 2,
    slottedInserts: [null, null],
    description: 'Heavy bronze blade shaped like a palm-cutter. Wide cleaves through brush and bone.'
  },
  {
    id: 'weapon:ofo_staff',
    name: 'Sacred Ofo Staff',
    kind: 'staff',
    glyph: '⚚',
    attackPower: 15,
    critChance: 0.12,
    attackSpeed: 1.1,
    sockets: 3,
    slottedInserts: [null, null, null],
    description: 'Staff of authoritative truth, carved with spiral rings of bronze.'
  },
  {
    id: 'weapon:bronze_spear',
    name: 'Amadioha Spear',
    kind: 'spear',
    glyph: '⤋',
    attackPower: 21,
    critChance: 0.16,
    attackSpeed: 1.15,
    sockets: 2,
    slottedInserts: [null, null],
    description: 'Forged bronze tip barbed to channel thunderbolts directly into targets.'
  },
  {
    id: 'weapon:ikenga_axe',
    name: 'Horned Ikenga Axe',
    kind: 'axe',
    glyph: '🪓',
    attackPower: 24,
    critChance: 0.14,
    attackSpeed: 0.9,
    sockets: 1,
    slottedInserts: [null],
    description: 'Dual-bearded war axe consecrated to a warrior\'s right hand.'
  }
];

export const IGBO_INSERTS: Insert[] = [
  { id: 'insert:bronze_ring', name: 'Igbo-Ukwu Bronze Ring', glyph: '◎', color: '#CD7F32', statBonus: { attackPower: 5, armour: 2 }, description: 'Adds +5 Attack Power and +2 Armour.', count: 3 },
  { id: 'insert:star_sapphire', name: 'Star Sapphire of Ala', glyph: '✦', color: '#3A86FF', statBonus: { critChance: 0.05, lifeSteal: 0.04 }, description: 'Adds +5% Crit Chance and +4% Life Steal.', count: 2 },
  { id: 'insert:thunder_amber', name: 'Thunder Amber', glyph: '⚡', color: '#FFBE0B', statBonus: { attackPower: 8 }, description: 'Surges with raw lightning, +8 Attack Power.', count: 1 },
  { id: 'insert:nsibidi_rune', name: 'Nsibidi Warding Glyph', glyph: '§', color: '#00B8A9', statBonus: { health: 40, armour: 4 }, description: 'Sacred protection symbol granting +40 HP and +4 Armour.', count: 2 },
  { id: 'insert:cowrie_blessing', name: 'Cowrie of Abundance', glyph: '𓆉', color: '#F4EBDC', statBonus: { health: 25, attackPower: 3 }, description: 'Brings prosperity and vitality.', count: 4 },
];

export const IGBO_SKILLS = [
  { id: 'igbo:mma_cleave', name: 'Mma Cleave', cost: 20, cooldown: 1.5, color: '#CD7F32', range: 2.2, damageMultiplier: 1.8, radius: 2.0, description: 'Sweeps weapon in a 180° arc hitting all foes.' },
  { id: 'igbo:ikenga_tremor', name: 'Ikenga Tremor', cost: 35, cooldown: 4.0, color: '#C1453B', range: 3.0, damageMultiplier: 2.5, radius: 3.2, description: 'Slams the earth, fracturing the ground and stunning enemies.' },
  { id: 'igbo:thunder_spear', name: 'Thunder Bolt', cost: 25, cooldown: 2.0, color: '#00B8A9', range: 5.0, damageMultiplier: 2.2, radius: 1.5, description: 'Calls down thunder from Amadioha striking from range.' },
  { id: 'igbo:shockwave_spark', name: 'Spark Wave', cost: 30, cooldown: 3.5, color: '#FFBE0B', range: 3.5, damageMultiplier: 1.9, radius: 2.5, description: 'Emits an electrical burst in all directions.' },
  { id: 'igbo:venom_geyser', name: 'Venom Geyser', cost: 25, cooldown: 3.0, color: '#2E5A36', range: 4.0, damageMultiplier: 2.0, radius: 2.0, description: 'Causes toxic earth to erupt under target column.' },
  { id: 'igbo:solar_supernova', name: 'Sunburst Rite', cost: 45, cooldown: 6.0, color: '#FF9F1C', range: 4.5, damageMultiplier: 3.0, radius: 3.5, description: 'Channels solar wrath, healing the hero and incinerating foes.' },
];

export const IGBO_ENEMIES = [
  { id: 'igbo:mmuo_spectre', name: 'Mmuo Mask Spectre', glyph: '🎭', health: 120, maxHealth: 120, attackPower: 12, moveSpeed: 0.04 },
  { id: 'igbo:agwo_python', name: 'Agwo Python Shade', glyph: '🐍', health: 90, maxHealth: 90, attackPower: 15, moveSpeed: 0.06 },
  { id: 'igbo:leopard_shaman', name: 'Ekpe Leopard Shaman', glyph: '🐆', health: 180, maxHealth: 180, attackPower: 18, moveSpeed: 0.05 },
  { id: 'igbo:bush_phantom', name: 'Bush Phantom', glyph: '♨', health: 110, maxHealth: 110, attackPower: 14, moveSpeed: 0.045 },
];

export const IGBO_LORE: LoreEntry[] = [
  {
    id: 'igbo:lore_bronze',
    title: 'The Roped Vessels',
    body: 'The bronzes of Igbo-Ukwu were cast before most of the world had a word for the technique. Rope, insect, and leaf were pressed into wax and lost to the fire, and what came out was metal that remembered them. What you pull out of a vein down here is the raw form of that memory.',
    category: 'ARTIFACT',
  },
  {
    id: 'igbo:lore_amadioha',
    title: 'Amadioha\'s Judgement',
    body: 'Thunder is not weather. It is a verdict delivered without appeal, and the crags on the peak are where it has been delivered most often. Crystal grows where the charge had nowhere left to go.',
    category: 'DEITY',
  },
  {
    id: 'igbo:lore_idemili',
    title: 'Idemili\'s Grove',
    body: 'The sacred python passes and the ground is not disturbed. Iroko stands where it was planted and outlives the argument about who planted it. Cut the canopy if you must, but the roots are older than your claim.',
    category: 'PLACE',
  },
  {
    id: 'igbo:lore_nsibidi',
    title: 'Marks That Are Not Letters',
    body: 'Nsibidi was never an alphabet. A mark settles a debt, names a union, or warns a stranger away from a doorway, and the reading depends on who is entitled to read. A seal cut into the ground here refuses the pick for the same reason.',
    category: 'RITUAL',
  },
  {
    id: 'igbo:lore_ofo',
    title: 'The Ofo Staff',
    body: 'Ofo is held by the one entitled to speak. It is not a weapon and it does not make its holder right, only answerable. The shrines keep that weight in stone.',
    category: 'RITUAL',
  },
  {
    id: 'igbo:lore_ikenga',
    title: 'The Right Hand',
    body: 'Ikenga is carved for a person\'s own achievement, horned because strength should be visible and answerable. It is retired when they are, and broken when they are buried.',
    category: 'DEITY',
  },
];

export const BUILTIN_IGBO_PACK: ContentPack = {
  id: 'igbo',
  name: 'Igbo-Ukwu Bronze',
  author: 'Stratum Core',
  version: '2.0.0',
  description: 'Five regions of Igbo mythology rendered as a mineable voxel world, with bronze at the bottom and ancient spirits in the dark.',
  palette: IGBO_PALETTE,
  blocks: IGBO_BLOCKS,
  biomes: IGBO_BIOMES,
  heroClasses: IGBO_CLASSES,
  loreEntries: IGBO_LORE,
  damageTypes: [
    { id: 'physical', name: 'Physical', color: '#CD7F32', glyph: '⚔' },
    { id: 'thunder', name: 'Thunder', color: '#00B8A9', glyph: '⚡' },
    { id: 'venom', name: 'Venom', color: '#2E5A36', glyph: '☠' },
    { id: 'solar', name: 'Solar', color: '#FF9F1C', glyph: '☀' },
  ],
  affixes: [
    { id: 'sharp', name: 'Keen-Edged', statBonus: { attackPower: 4 }, description: '+4 Attack Power' },
    { id: 'heavy', name: 'Weighted Bronze', statBonus: { attackPower: 6, attackSpeed: -0.1 }, description: '+6 Attack, -10% Speed' },
    { id: 'swift', name: 'Leopard Wind', statBonus: { attackSpeed: 0.15, critChance: 0.05 }, description: '+15% Speed, +5% Crit' },
  ],
  inserts: IGBO_INSERTS,
  weapons: IGBO_WEAPONS,
  enemies: IGBO_ENEMIES,
  skills: IGBO_SKILLS,
  terrain: {
    elevation: [
      { scale: 0.03, amplitude: 1.0 },
      { scale: 0.08, amplitude: 0.35, seedOffset: 101 },
    ],
    terraceStep: 3,
    strata: [
      { blockId: 'igbo:red_earth', thickness: 2 },
      { blockId: 'igbo:river_clay', thickness: 2 },
      { blockId: 'igbo:granite_stone', thickness: 4 },
    ],
  },
};
