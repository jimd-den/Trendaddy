export interface PackPalette {
  surface: string;
  surfaceRaised: string;
  surfaceSunken: string;
  ink: string;
  inkMuted: string;
  accent: string;
  accentAlt: string;
  danger: string;
  bevel?: string;
  hairline?: string;
}

export interface BlockType {
  id: string;
  displayName: string;
  topColor: string;
  sideColor: string;
  hardness: number;
  glyph: string;
  description?: string;
}

export interface Biome {
  id: string;
  name: string;
  description: string;
  surfaceBlock: string;
  subSurfaceBlock?: string;
  ambientColor: string;
}

export interface LoreEntry {
  id: string;
  title: string;
  body: string;
  category: 'ARTIFACT' | 'DEITY' | 'PLACE' | 'RITUAL';
  subjectId?: string;
}

export interface DamageType {
  id: string;
  name: string;
  color: string;
  glyph: string;
}

export interface Affix {
  id: string;
  name: string;
  statBonus: Record<string, number>;
  description: string;
}

export interface Insert {
  id: string;
  name: string;
  glyph: string;
  color: string;
  statBonus: {
    attackPower?: number;
    health?: number;
    armour?: number;
    critChance?: number;
    lifeSteal?: number;
  };
  description: string;
  count: number;
}

export interface NoiseLayer {
  scale: number;
  amplitude: number;
  seedOffset?: number;
}

export interface StratumLayer {
  blockId: string;
  thickness: number;
}

export interface TerrainRecipe {
  elevation: NoiseLayer[];
  terraceStep: number;
  strata: StratumLayer[];
}

export interface ContentPack {
  id: string;
  name: string;
  author: string;
  version: string;
  description: string;
  palette: PackPalette;
  blocks: BlockType[];
  biomes: Biome[];
  heroClasses: any[];
  loreEntries: LoreEntry[];
  damageTypes: DamageType[];
  affixes: Affix[];
  inserts: Insert[];
  weapons: any[];
  enemies: any[];
  skills: any[];
  terrain: TerrainRecipe;
}
