export type AnimationState = 'IDLE' | 'WALK' | 'ATTACK' | 'HURT' | 'DIE';

export interface FrameRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

export interface SpriteClip {
  state: AnimationState;
  firstFrame: number;
  frameCount: number;
  frameRate: number;
  standsInFor?: AnimationState;
}

export interface SpriteSheet {
  id: string;
  name: string;
  frameWidth: number;
  frameHeight: number;
  columns: number;
  rows: number;
  clips: SpriteClip[];
  anchorX: number; // 0.0 - 1.0 (baseline anchor, default 0.5)
  anchorY: number; // default 0.85 (feet)
}

export interface SpriteAtlasFrame {
  id: string;
  rect: FrameRect;
  anchorX: number;
  anchorY: number;
  flipped: boolean;
}

export interface SpriteAtlas {
  id: string;
  sourceWidth: number;
  sourceHeight: number;
  frames: SpriteAtlasFrame[];
  clips: Record<AnimationState, string[]>; // state -> frame ids
}
