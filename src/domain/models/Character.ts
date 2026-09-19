import { SpriteSheet } from './Sprite';
import { WeaponFit } from './Weapon';

export type CharacterRole = 'hero' | 'enemy';

export interface PoseFrameRecord {
  stepIndex: number;
  state: 'IDLE' | 'WALK' | 'ATTACK' | 'HURT' | 'DIE';
  poseName: string;
  imageData?: string; // base64 / data URL
  createdAt: number;
}

export interface PoseGuides {
  style: 'openpose' | 'stratum_bronze';
  angles?: Record<string, number>;
}

/**
 * Booch Character Aggregate.
 * Unifies setId, role, raw poses, baked SpriteSheet, skeleton guides, and weapon fit into a single cohesive entity.
 * Eliminates the disconnect between PoseLibrary and SpriteLibrary.
 */
export interface CharacterAggregate {
  id: string; // e.g. "hero:dike_ozo" or "enemy:mmuo_spectre"
  name: string;
  role: CharacterRole;
  createdAt: number;
  updatedAt: number;
  
  // Pose Generation Pipeline
  hasReference: boolean;
  referenceImageData?: string;
  poses: Record<string, PoseFrameRecord>; // key: state_step
  guides: PoseGuides;
  
  // Packed Asset
  isPacked: boolean;
  packedSheet?: SpriteSheet;
  sheetImageData?: string; // Canvas data URL for preview and renderer
  
  // Weapon Rigging
  weaponFit: WeaponFit;
}
