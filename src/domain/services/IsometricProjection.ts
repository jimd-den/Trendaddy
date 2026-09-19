import { BlockPos, WorldPoint } from '../models/World';

export class IsometricProjection {
  constructor(
    public tileWidth: number = 72,
    public tileHeight: number = 36,
    public blockHeight: number = 26,
    public zoom: number = 1
  ) {}

  get halfWidth(): number {
    return (this.tileWidth * this.zoom) / 2;
  }

  get halfHeight(): number {
    return (this.tileHeight * this.zoom) / 2;
  }

  get liftPerLevel(): number {
    return this.blockHeight * this.zoom;
  }

  project(x: number, y: number, z: number): { x: number; y: number } {
    return {
      x: (x - y) * this.halfWidth,
      y: (x + y) * this.halfHeight - z * this.liftPerLevel,
    };
  }

  projectPos(pos: BlockPos): { x: number; y: number } {
    return this.project(pos.x, pos.y, pos.z);
  }

  projectPoint(point: WorldPoint): { x: number; y: number } {
    return this.project(point.x, point.y, point.z);
  }

  unproject(screenX: number, screenY: number, z: number = 0): WorldPoint {
    const liftedY = screenY + z * this.liftPerLevel;
    const a = screenX / this.halfWidth;
    const b = liftedY / this.halfHeight;
    return {
      x: (a + b) / 2,
      y: (b - a) / 2,
      z,
    };
  }

  pickColumn(
    screenX: number,
    screenY: number,
    isSolidAt: (pos: BlockPos) => boolean,
    maxZ: number = 15
  ): BlockPos | null {
    for (let z = maxZ; z >= 0; z--) {
      const candidate = this.unproject(screenX, screenY, z);
      const pos: BlockPos = {
        x: Math.floor(candidate.x),
        y: Math.floor(candidate.y),
        z,
      };
      if (isSolidAt(pos)) {
        return pos;
      }
    }
    return null;
  }

  depthKey(pos: BlockPos | WorldPoint): number {
    return (pos.x + pos.y) * 16 + pos.z;
  }
}
