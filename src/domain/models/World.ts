export interface BlockPos {
  x: number;
  y: number;
  z: number;
}

export interface WorldPoint {
  x: number;
  y: number;
  z: number;
}

export interface ChunkKey {
  cx: number;
  cy: number;
}

export const CHUNK_SIZE = 16;
export const CHUNK_HEIGHT = 16;

export interface EnemyEntity {
  id: string;
  definitionId: string;
  name: string;
  glyph: string;
  position: WorldPoint;
  facing: 'N' | 'S' | 'E' | 'W' | 'NE' | 'NW' | 'SE' | 'SW';
  health: number;
  maxHealth: number;
  attackPower: number;
  moveSpeed: number;
  state: 'IDLE' | 'WALK' | 'ATTACK' | 'HURT' | 'DIE';
  stateTime: number;
  cooldown: number;
  isDead: boolean;
}

export interface GroundLoot {
  id: string;
  name: string;
  glyph: string;
  color: string;
  position: WorldPoint;
  kind: 'item' | 'block' | 'insert' | 'cowries';
  itemRefId: string;
  amount: number;
}

export interface CombatFeedback {
  id: string;
  position: WorldPoint;
  text: string;
  color: string;
  isCrit: boolean;
  createdAt: number;
  durationMs: number;
}

export type BuildTool = 'SINGLE' | 'LINE' | 'BOX' | 'WALL' | 'TERRACE';
