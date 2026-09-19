import { CharacterAggregate, CharacterRole, PoseFrameRecord } from '../../domain/models/Character';
import { SpriteSheet } from '../../domain/models/Sprite';
import { POSE_SCRIPT_POSES } from '../../domain/services/PoseScript';

type Listener = () => void;

// Helper to draw procedural pixel art characters on a 2D canvas
export function generateProceduralCharacterCanvas(
  role: CharacterRole,
  name: string,
  state: 'IDLE' | 'WALK' | 'ATTACK' | 'HURT' | 'DIE',
  frameIndex: number
): string {
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 64;
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';

  ctx.imageSmoothingEnabled = false;

  // Determine palette based on name / role
  let skin = '#D97706';
  let cloth = '#CD7F32';
  let accent = '#00B8A9';
  let metal = '#F4EBDC';

  if (name.includes('Amadioha')) {
    skin = '#B45309';
    cloth = '#1E3A8A';
    accent = '#60A5FA';
  } else if (name.includes('Dibia')) {
    skin = '#78350F';
    cloth = '#065F46';
    accent = '#F4EBDC';
  } else if (name.includes('Ikenga')) {
    skin = '#92400E';
    cloth = '#991B1B';
    accent = '#CD7F32';
  } else if (role === 'enemy') {
    skin = '#4B5563';
    cloth = '#1F2937';
    accent = '#C1453B';
    metal = '#9CA3AF';
  }

  const bob = state === 'IDLE' ? Math.sin(frameIndex * 0.5) * 2 : 0;
  const walkOffset = state === 'WALK' ? Math.sin(frameIndex * 0.8) * 4 : 0;
  const attackExt = state === 'ATTACK' ? (frameIndex % 4) * 3 : 0;

  // Shadow
  ctx.fillStyle = 'rgba(0, 0, 0, 0.35)';
  ctx.beginPath();
  ctx.ellipse(32, 54, 16, 6, 0, 0, Math.PI * 2);
  ctx.fill();

  // Legs / Stance
  ctx.fillStyle = cloth;
  ctx.fillRect(26 - walkOffset / 2, 40 + bob, 5, 14);
  ctx.fillRect(33 + walkOffset / 2, 40 + bob, 5, 14);

  // Torso / Bronze chestplate
  ctx.fillStyle = cloth;
  ctx.fillRect(24, 25 + bob, 16, 17);
  ctx.fillStyle = skin;
  ctx.fillRect(26, 27 + bob, 12, 13);
  // Bronze ornament
  ctx.fillStyle = accent;
  ctx.fillRect(30, 31 + bob, 4, 6);

  // Head / Torc / Mask
  ctx.fillStyle = skin;
  ctx.fillRect(25, 14 + bob, 14, 13);
  // Facial markings (Ichi / Nsibidi)
  ctx.fillStyle = accent;
  ctx.fillRect(28, 17 + bob, 2, 4);
  ctx.fillRect(34, 17 + bob, 2, 4);
  // Eyes
  ctx.fillStyle = metal;
  ctx.fillRect(28, 19 + bob, 2, 2);
  ctx.fillRect(34, 19 + bob, 2, 2);

  // Arms & Weapon
  ctx.fillStyle = skin;
  ctx.fillRect(20, 27 + bob - walkOffset, 4, 12);
  ctx.fillRect(40, 27 + bob + attackExt, 4, 12);

  // Weapon in right hand
  ctx.fillStyle = accent;
  ctx.fillRect(43, 20 + bob - attackExt, 3, 22);
  ctx.fillStyle = metal;
  ctx.fillRect(42, 18 + bob - attackExt, 5, 4);

  return canvas.toDataURL('image/png');
}

class CharacterRepositoryImpl {
  private characters: Map<string, CharacterAggregate> = new Map();
  private listeners: Set<Listener> = new Set();
  
  // Stored selections
  private selectedHeroSheetId: string | null = null;
  private selectedHeroClassId: string = 'igbo:dike_ozo';
  private selectedWeaponId: string = 'weapon:mma_nkwu';

  constructor() {
    this.loadFromStorage();
    this.initializeDefaultsIfEmpty();
  }

  private loadFromStorage() {
    try {
      const raw = localStorage.getItem('stratum:characters');
      if (raw) {
        const list: CharacterAggregate[] = JSON.parse(raw);
        list.forEach(c => this.characters.set(c.id, c));
      }
      this.selectedHeroSheetId = localStorage.getItem('stratum:selectedHeroSheetId');
      this.selectedHeroClassId = localStorage.getItem('stratum:selectedHeroClassId') || 'igbo:dike_ozo';
      this.selectedWeaponId = localStorage.getItem('stratum:selectedWeaponId') || 'weapon:mma_nkwu';
    } catch (e) {
      console.warn('Could not read character store from localStorage', e);
    }
  }

  private saveToStorage() {
    try {
      const list = Array.from(this.characters.values());
      localStorage.setItem('stratum:characters', JSON.stringify(list));
      if (this.selectedHeroSheetId) {
        localStorage.setItem('stratum:selectedHeroSheetId', this.selectedHeroSheetId);
      } else {
        localStorage.removeItem('stratum:selectedHeroSheetId');
      }
      localStorage.setItem('stratum:selectedHeroClassId', this.selectedHeroClassId);
      localStorage.setItem('stratum:selectedWeaponId', this.selectedWeaponId);
    } catch (e) {
      console.warn('Could not save character store to localStorage', e);
    }
  }

  private initializeDefaultsIfEmpty() {
    const defaults = [
      { id: 'hero:dike_ozo', name: 'Dike Ozo', role: 'hero' as CharacterRole },
      { id: 'hero:amadioha_invoker', name: 'Amadioha Invoker', role: 'hero' as CharacterRole },
      { id: 'hero:dibia_nzu', name: 'Dibia Nzu', role: 'hero' as CharacterRole },
      { id: 'hero:ikenga_berserker', name: 'Ikenga Berserker', role: 'hero' as CharacterRole },
      { id: 'enemy:mmuo_spectre', name: 'Mmuo Mask Spectre', role: 'enemy' as CharacterRole },
    ];

    let changed = false;
    for (const def of defaults) {
      if (!this.characters.has(def.id)) {
        const char: CharacterAggregate = {
          id: def.id,
          name: def.name,
          role: def.role,
          createdAt: Date.now(),
          updatedAt: Date.now(),
          hasReference: true,
          poses: {},
          guides: { style: 'stratum_bronze' },
          isPacked: true,
          packedSheet: {
            id: def.id,
            name: def.name,
            frameWidth: 64,
            frameHeight: 64,
            columns: 8,
            rows: 5,
            anchorX: 0.5,
            anchorY: 0.85,
            clips: [
              { state: 'IDLE', firstFrame: 0, frameCount: 8, frameRate: 8 },
              { state: 'WALK', firstFrame: 8, frameCount: 12, frameRate: 12 },
              { state: 'ATTACK', firstFrame: 20, frameCount: 10, frameRate: 14 },
              { state: 'HURT', firstFrame: 30, frameCount: 4, frameRate: 10 },
              { state: 'DIE', firstFrame: 34, frameCount: 6, frameRate: 8 },
            ]
          },
          sheetImageData: generateProceduralCharacterCanvas(def.role, def.name, 'IDLE', 0),
          weaponFit: { offsetX: 0, offsetY: 0, scale: 1.0, mirror: false }
        };

        // Populate initial poses
        POSE_SCRIPT_POSES.forEach(p => {
          char.poses[`${p.state}_${p.step}`] = {
            stepIndex: p.step,
            state: p.state,
            poseName: p.name,
            imageData: generateProceduralCharacterCanvas(def.role, def.name, p.state, p.step),
            createdAt: Date.now()
          };
        });

        this.characters.set(def.id, char);
        changed = true;
      }
    }

    if (!this.selectedHeroSheetId) {
      this.selectedHeroSheetId = 'hero:dike_ozo';
    }

    if (changed) {
      this.saveToStorage();
    }
  }

  // Reactive Subscriptions
  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify() {
    this.saveToStorage();
    this.listeners.forEach(fn => fn());
  }

  // Getters
  getAll(): CharacterAggregate[] {
    return Array.from(this.characters.values());
  }

  getHeroes(): CharacterAggregate[] {
    return this.getAll().filter(c => c.role === 'hero');
  }

  getEnemies(): CharacterAggregate[] {
    return this.getAll().filter(c => c.role === 'enemy');
  }

  getById(id: string): CharacterAggregate | undefined {
    return this.characters.get(id);
  }

  /**
   * Returns characters that have poses drawn but are NOT yet packed into a sprite sheet.
   * This directly addresses: "On Home, when pose sets exist with no packed sheet, show 'N characters drawn but not packed'"
   */
  getUnpackedCharacters(): CharacterAggregate[] {
    return this.getAll().filter(c => !c.isPacked && Object.keys(c.poses).length > 0);
  }

  // Selection persistence
  getSelectedHeroSheetId(): string {
    if (this.selectedHeroSheetId && this.characters.has(this.selectedHeroSheetId)) {
      return this.selectedHeroSheetId;
    }
    const firstHero = this.getHeroes().find(c => c.isPacked);
    return firstHero?.id || 'hero:dike_ozo';
  }

  setSelectedHeroSheetId(id: string) {
    this.selectedHeroSheetId = id;
    this.notify();
  }

  getSelectedHeroClassId(): string {
    return this.selectedHeroClassId;
  }

  setSelectedHeroClassId(id: string) {
    this.selectedHeroClassId = id;
    this.notify();
  }

  getSelectedWeaponId(): string {
    return this.selectedWeaponId;
  }

  setSelectedWeaponId(id: string) {
    this.selectedWeaponId = id;
    this.notify();
  }

  // Mutations
  saveCharacter(char: CharacterAggregate) {
    char.updatedAt = Date.now();
    this.characters.set(char.id, char);
    this.notify();
  }

  deleteCharacter(id: string) {
    this.characters.delete(id);
    if (this.selectedHeroSheetId === id) {
      this.selectedHeroSheetId = null;
    }
    this.notify();
  }

  /**
   * Builds and bakes the character's sprite sheet.
   * Promotes the character from raw pose set into world-ready sprite sheet immediately.
   */
  packCharacterSheet(id: string) {
    const char = this.characters.get(id);
    if (!char) return;

    // Build the sheet configuration
    const sheet: SpriteSheet = {
      id: char.id,
      name: char.name,
      frameWidth: 64,
      frameHeight: 64,
      columns: 8,
      rows: 5,
      anchorX: 0.5,
      anchorY: 0.85,
      clips: [
        { state: 'IDLE', firstFrame: 0, frameCount: 8, frameRate: 8 },
        { state: 'WALK', firstFrame: 8, frameCount: 12, frameRate: 12 },
        { state: 'ATTACK', firstFrame: 20, frameCount: 10, frameRate: 14 },
        { state: 'HURT', firstFrame: 30, frameCount: 4, frameRate: 10 },
        { state: 'DIE', firstFrame: 34, frameCount: 6, frameRate: 8 },
      ]
    };

    // If an idle pose exists, use it as sheet preview
    const idlePose = Object.values(char.poses).find(p => p.state === 'IDLE');
    char.isPacked = true;
    char.packedSheet = sheet;
    char.sheetImageData = idlePose?.imageData || generateProceduralCharacterCanvas(char.role, char.name, 'IDLE', 0);
    char.updatedAt = Date.now();

    this.notify();
  }

  /**
   * Adds a pose frame to a character.
   * If all 40 poses are drawn, automatically runs pack! (Phase 1 blueprint: Make the pack automatic)
   */
  addPoseFrame(id: string, pose: PoseFrameRecord) {
    let char = this.characters.get(id);
    if (!char) {
      const isEnemy = id.startsWith('enemy:');
      const role: CharacterRole = isEnemy ? 'enemy' : 'hero';
      const cleanName = id.replace(/^(hero|enemy):/, '').replace(/_/g, ' ');
      char = {
        id,
        name: cleanName.charAt(0).toUpperCase() + cleanName.slice(1),
        role,
        createdAt: Date.now(),
        updatedAt: Date.now(),
        hasReference: true,
        poses: {},
        guides: { style: 'stratum_bronze' },
        isPacked: false,
        weaponFit: { offsetX: 0, offsetY: 0, scale: 1.0, mirror: false }
      };
      this.characters.set(id, char);
    }

    char.poses[`${pose.state}_${pose.stepIndex}`] = pose;
    char.updatedAt = Date.now();

    // Auto-pack if all 40 poses completed or when 8 idle frames exist
    const poseCount = Object.keys(char.poses).length;
    if (poseCount >= 40 || (!char.isPacked && poseCount >= 8)) {
      this.packCharacterSheet(id);
    } else {
      this.notify();
    }
  }
}

export const CharacterRepository = new CharacterRepositoryImpl();
