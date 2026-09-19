import { ContentPack, BlockType, Insert } from '../domain/models/ContentPack';
import { HeroClassDefinition } from '../domain/models/HeroClass';
import { WeaponDefinition } from '../domain/models/Weapon';
import { CharacterAggregate } from '../domain/models/Character';
import { BlockPos, WorldPoint, EnemyEntity, GroundLoot, CombatFeedback, BuildTool } from '../domain/models/World';
import { SimplexNoise } from '../domain/services/Noise';
import { IsometricProjection } from '../domain/services/IsometricProjection';

export interface InventoryItem {
  id: string;
  name: string;
  glyph: string;
  kind: 'weapon' | 'block' | 'insert' | 'cowries' | 'item';
  color: string;
  count: number;
  data?: any;
}

export class WorldSession {
  public worldMap: Map<string, BlockType> = new Map();
  public highestZ: Map<string, number> = new Map(); // "x,y" -> top z

  public playerPos: WorldPoint = { x: 8, y: 8, z: 4 };
  public playerFacing: 'N' | 'S' | 'E' | 'W' | 'NE' | 'NW' | 'SE' | 'SW' = 'SE';
  public playerState: 'IDLE' | 'WALK' | 'ATTACK' | 'HURT' | 'DIE' = 'IDLE';
  public playerStateTime: number = 0;
  public playerVelocity: { x: number; y: number } = { x: 0, y: 0 };

  // Vitals & Progression
  public health: number = 240;
  public maxHealth: number = 240;
  public resource: number = 100;
  public maxResource: number = 100;
  public level: number = 1;
  public experience: number = 0;
  public experienceNext: number = 100;
  public cowries: number = 75;

  // Combat stats
  public isDodgeRolling: boolean = false;
  public dodgeCooldown: number = 0;
  public attackCooldown: number = 0;
  public skillCooldowns: Record<string, number> = {};

  // Mining & Building
  public miningTarget: BlockPos | null = null;
  public miningProgress: number = 0; // 0 to 1
  public selectedHotbarIndex: number = 0;
  public activeBuildTool: BuildTool = 'SINGLE';
  public isBuildMode: boolean = false;

  // Entities & FX
  public enemies: EnemyEntity[] = [];
  public groundLoot: GroundLoot[] = [];
  public combatPopups: CombatFeedback[] = [];

  // Inventory & Equipment
  public inventory: InventoryItem[] = [];
  public equippedWeapon: WeaponDefinition;
  public hotbarBlocks: BlockType[] = [];

  // Projection
  public projection: IsometricProjection = new IsometricProjection(72, 36, 26, 1.0);
  public cameraPos: { x: number; y: number } = { x: 0, y: 0 };

  private noise: SimplexNoise = new SimplexNoise(1337);
  private listeners: Set<() => void> = new Set();
  private lastUpdate: number = performance.now();

  constructor(
    public pack: ContentPack,
    public heroClass: HeroClassDefinition,
    public character: CharacterAggregate,
    weapon: WeaponDefinition
  ) {
    this.equippedWeapon = weapon;
    this.health = heroClass.baseStats.maxHealth;
    this.maxHealth = heroClass.baseStats.maxHealth;
    this.resource = heroClass.baseResource;
    this.maxResource = heroClass.baseResource;

    this.hotbarBlocks = pack.blocks.slice(0, 5);
    this.setupInventory();
    this.generateTerrain();
    this.spawnEnemies();
  }

  public subscribe(cb: () => void): () => void {
    this.listeners.add(cb);
    return () => this.listeners.delete(cb);
  }

  private notify() {
    this.listeners.forEach(fn => fn());
  }

  private setupInventory() {
    // Starting items
    this.inventory = [
      { id: 'cowries', name: 'Cowrie Shells', glyph: '𓆉', kind: 'cowries', color: '#F4EBDC', count: 75 },
      { id: this.hotbarBlocks[0]?.id || 'igbo:red_earth', name: this.hotbarBlocks[0]?.displayName || 'Red Earth', glyph: '■', kind: 'block', color: this.hotbarBlocks[0]?.topColor || '#8E3E2B', count: 48 },
      { id: this.hotbarBlocks[1]?.id || 'igbo:river_clay', name: this.hotbarBlocks[1]?.displayName || 'River Clay', glyph: '░', kind: 'block', color: this.hotbarBlocks[1]?.topColor || '#5C4A3B', count: 24 },
      { id: 'insert:bronze_ring', name: 'Igbo-Ukwu Bronze Ring', glyph: '◎', kind: 'insert', color: '#CD7F32', count: 2, data: this.pack.inserts.find(i => i.id === 'insert:bronze_ring') },
      { id: 'insert:nsibidi_rune', name: 'Nsibidi Warding Glyph', glyph: '§', kind: 'insert', color: '#00B8A9', count: 1, data: this.pack.inserts.find(i => i.id === 'insert:nsibidi_rune') },
    ];
  }

  private posKey(x: number, y: number, z: number): string {
    return `${x},${y},${z}`;
  }

  public getBlock(pos: BlockPos): BlockType | undefined {
    return this.worldMap.get(this.posKey(pos.x, pos.y, pos.z));
  }

  public setBlock(pos: BlockPos, block: BlockType) {
    this.worldMap.set(this.posKey(pos.x, pos.y, pos.z), block);
    const key2D = `${pos.x},${pos.y}`;
    const currentHigh = this.highestZ.get(key2D) ?? -1;
    if (pos.z > currentHigh) {
      this.highestZ.set(key2D, pos.z);
    }
  }

  public removeBlock(pos: BlockPos): BlockType | undefined {
    const key = this.posKey(pos.x, pos.y, pos.z);
    const old = this.worldMap.get(key);
    if (old) {
      this.worldMap.delete(key);
      const key2D = `${pos.x},${pos.y}`;
      // recompute highestZ
      let top = -1;
      for (let z = 15; z >= 0; z--) {
        if (this.worldMap.has(this.posKey(pos.x, pos.y, z))) {
          top = z;
          break;
        }
      }
      if (top >= 0) {
        this.highestZ.set(key2D, top);
      } else {
        this.highestZ.delete(key2D);
      }
    }
    return old;
  }

  public isSolidAt(pos: BlockPos): boolean {
    return this.worldMap.has(this.posKey(pos.x, pos.y, pos.z));
  }

  public getSurfaceZ(x: number, y: number): number {
    return this.highestZ.get(`${Math.floor(x)},${Math.floor(y)}`) ?? 0;
  }

  private generateTerrain() {
    const range = 24; // -12 to 12
    for (let x = -range; x <= range; x++) {
      for (let y = -range; y <= range; y++) {
        // Height formula using multi-octave simplex noise
        const n1 = this.noise.noise(x * 0.05, y * 0.05);
        const n2 = this.noise.noise(x * 0.12 + 10, y * 0.12 + 10) * 0.5;
        const rawHeight = (n1 + n2 + 1.5) * 1.8;
        // Step terracing (creates authentic voxel steps)
        const height = Math.min(12, Math.max(1, Math.floor(rawHeight)));

        for (let z = 0; z <= height; z++) {
          let block = this.pack.blocks[0]; // red earth
          if (z === height) {
            // Surface variation
            const oreNoise = this.noise.noise(x * 0.2, y * 0.2);
            if (oreNoise > 0.4 && this.pack.blocks[3]) {
              block = this.pack.blocks[3]; // bronze ore
            } else if (z >= 6 && this.pack.blocks[4]) {
              block = this.pack.blocks[4]; // obsidian
            } else if ((x + y) % 5 === 0 && this.pack.blocks[6]) {
              block = this.pack.blocks[6]; // grove turf
            } else {
              block = this.pack.blocks[0];
            }
          } else if (z > height - 2) {
            block = this.pack.blocks[1] || this.pack.blocks[0]; // river clay
          } else {
            block = this.pack.blocks[2] || this.pack.blocks[0]; // granite stone
          }
          this.setBlock({ x, y, z }, block);
        }
      }
    }

    // Set player starting height on ground
    const startSurfaceZ = this.getSurfaceZ(0, 0);
    this.playerPos = { x: 0.5, y: 0.5, z: startSurfaceZ + 1 };
  }

  private spawnEnemies() {
    const enemyTypes = this.pack.enemies.length > 0 ? this.pack.enemies : [
      { id: 'igbo:mmuo_spectre', name: 'Mmuo Mask Spectre', glyph: '🎭', health: 120, maxHealth: 120, attackPower: 12, moveSpeed: 0.035 },
      { id: 'igbo:agwo_python', name: 'Agwo Python Shade', glyph: '🐍', health: 90, maxHealth: 90, attackPower: 15, moveSpeed: 0.05 },
      { id: 'igbo:bush_phantom', name: 'Bush Phantom', glyph: '♨', health: 110, maxHealth: 110, attackPower: 14, moveSpeed: 0.04 },
    ];

    const spawnOffsets = [
      { x: 5, y: 4 }, { x: -6, y: 7 }, { x: 8, y: -6 }, { x: -8, y: -7 },
      { x: 10, y: 10 }, { x: -11, y: -5 }, { x: 7, y: 12 }, { x: -5, y: 12 }
    ];

    this.enemies = spawnOffsets.map((offset, idx) => {
      const type = enemyTypes[idx % enemyTypes.length];
      const z = this.getSurfaceZ(offset.x, offset.y);
      return {
        id: `enemy_${idx}_${Date.now()}`,
        definitionId: type.id,
        name: type.name,
        glyph: type.glyph || '☠',
        position: { x: offset.x + 0.5, y: offset.y + 0.5, z: z + 1 },
        facing: 'SW',
        health: type.health || 120,
        maxHealth: type.health || 120,
        attackPower: type.attackPower || 12,
        moveSpeed: type.moveSpeed || 0.035,
        state: 'IDLE',
        stateTime: 0,
        cooldown: 0,
        isDead: false,
      };
    });
  }

  // Combat Actions
  public attack() {
    if (this.attackCooldown > 0 || this.health <= 0) return;
    this.playerState = 'ATTACK';
    this.playerStateTime = 0;
    this.attackCooldown = 0.45;

    // Calculate attack stats including socketed inserts
    let bonusAttack = 0;
    let bonusCrit = 0;
    this.equippedWeapon.slottedInserts.forEach(insertId => {
      if (!insertId) return;
      const found = this.pack.inserts.find(i => i.id === insertId);
      if (found?.statBonus.attackPower) bonusAttack += found.statBonus.attackPower;
      if (found?.statBonus.critChance) bonusCrit += found.statBonus.critChance;
    });

    const totalAttack = this.equippedWeapon.attackPower + this.heroClass.baseStats.attackPower + bonusAttack;
    const totalCritChance = this.heroClass.baseStats.critChance + this.equippedWeapon.critChance + bonusCrit;

    // Reach arc test against all alive enemies
    const attackRange = 2.4;
    let hitCount = 0;

    this.enemies.forEach(enemy => {
      if (enemy.isDead) return;
      const dx = enemy.position.x - this.playerPos.x;
      const dy = enemy.position.y - this.playerPos.y;
      const dist = Math.hypot(dx, dy);

      if (dist <= attackRange) {
        hitCount++;
        const isCrit = Math.random() < totalCritChance;
        const multiplier = isCrit ? 1.8 : 1.0;
        const damage = Math.round(totalAttack * multiplier * (0.9 + Math.random() * 0.2));

        enemy.health -= damage;
        enemy.state = 'HURT';
        enemy.stateTime = 0;

        // Pushback
        enemy.position.x += (dx / dist) * 0.6;
        enemy.position.y += (dy / dist) * 0.6;

        this.addCombatPopup(enemy.position, `${damage}`, isCrit ? '#FFD166' : '#CD7F32', isCrit);

        if (enemy.health <= 0) {
          enemy.health = 0;
          enemy.isDead = true;
          enemy.state = 'DIE';
          this.handleEnemyDefeat(enemy);
        }
      }
    });

    // Resource gain on attack
    this.resource = Math.min(this.maxResource, this.resource + 12);
  }

  public castSkill(skillIndex: number) {
    const skill = this.pack.skills[skillIndex];
    if (!skill || this.resource < skill.cost || (this.skillCooldowns[skill.id] ?? 0) > 0) return;

    this.resource -= skill.cost;
    this.skillCooldowns[skill.id] = skill.cooldown;
    this.playerState = 'ATTACK';
    this.playerStateTime = 0;

    // Area of effect damage
    this.enemies.forEach(enemy => {
      if (enemy.isDead) return;
      const dist = Math.hypot(enemy.position.x - this.playerPos.x, enemy.position.y - this.playerPos.y);
      if (dist <= (skill.radius || 3.0)) {
        const damage = Math.round(this.equippedWeapon.attackPower * skill.damageMultiplier);
        enemy.health -= damage;
        this.addCombatPopup(enemy.position, `${skill.name} -${damage}!`, skill.color, true);

        if (enemy.health <= 0) {
          enemy.health = 0;
          enemy.isDead = true;
          enemy.state = 'DIE';
          this.handleEnemyDefeat(enemy);
        }
      }
    });
  }

  public dodgeRoll() {
    if (this.dodgeCooldown > 0 || this.isDodgeRolling || this.health <= 0) return;
    this.isDodgeRolling = true;
    this.dodgeCooldown = 1.2;

    // Boost velocity in facing direction
    const angleMap: Record<string, number> = {
      N: -Math.PI / 2, S: Math.PI / 2, E: 0, W: Math.PI,
      NE: -Math.PI / 4, NW: -3 * Math.PI / 4, SE: Math.PI / 4, SW: 3 * Math.PI / 4
    };
    const angle = angleMap[this.playerFacing] ?? 0;
    this.playerVelocity.x = Math.cos(angle) * 0.35;
    this.playerVelocity.y = Math.sin(angle) * 0.35;

    setTimeout(() => {
      this.isDodgeRolling = false;
    }, 320);
  }

  private handleEnemyDefeat(enemy: EnemyEntity) {
    // XP gain
    this.addExperience(35);

    // Drop loot at enemy location
    const lootRoll = Math.random();
    if (lootRoll > 0.4) {
      // Cowries
      const amount = 5 + Math.floor(Math.random() * 15);
      this.groundLoot.push({
        id: `loot_${Date.now()}_${Math.random()}`,
        name: 'Cowrie Shells',
        glyph: '𓆉',
        color: '#F4EBDC',
        position: { ...enemy.position },
        kind: 'cowries',
        itemRefId: 'cowries',
        amount,
      });
    }

    if (lootRoll > 0.6) {
      // Bronze ore or insert
      const insert = this.pack.inserts[Math.floor(Math.random() * this.pack.inserts.length)];
      if (insert) {
        this.groundLoot.push({
          id: `loot_${Date.now()}_${Math.random()}`,
          name: insert.name,
          glyph: insert.glyph,
          color: insert.color,
          position: { ...enemy.position, x: enemy.position.x + 0.3 },
          kind: 'insert',
          itemRefId: insert.id,
          amount: 1,
        });
      }
    }
  }

  public addExperience(amount: number) {
    this.experience += amount;
    if (this.experience >= this.experienceNext) {
      this.level++;
      this.experience -= this.experienceNext;
      this.experienceNext = Math.round(this.experienceNext * 1.5);
      this.maxHealth += 20;
      this.health = this.maxHealth;
      this.addCombatPopup(this.playerPos, `LEVEL ${this.level}!`, '#FFD166', true);
    }
  }

  public addCombatPopup(pos: WorldPoint, text: string, color: string, isCrit: boolean = false) {
    this.combatPopups.push({
      id: `popup_${Date.now()}_${Math.random()}`,
      position: { ...pos },
      text,
      color,
      isCrit,
      createdAt: performance.now(),
      durationMs: 1200,
    });
  }

  // Mining & Block Placement
  public mineBlock(pos: BlockPos) {
    const dist = Math.hypot(pos.x - this.playerPos.x, pos.y - this.playerPos.y);
    if (dist > 4.5) return;

    this.miningTarget = pos;
    this.miningProgress += 0.35;

    if (this.miningProgress >= 1.0) {
      const removed = this.removeBlock(pos);
      if (removed) {
        this.addItemToInventory(removed.id, removed.displayName, 'block', removed.topColor, 1);
        this.addExperience(5);
        this.addCombatPopup({ x: pos.x, y: pos.y, z: pos.z }, `+1 ${removed.displayName}`, removed.topColor);
      }
      this.miningTarget = null;
      this.miningProgress = 0;
    }
  }

  public placeBlock(pos: BlockPos) {
    const block = this.hotbarBlocks[this.selectedHotbarIndex];
    if (!block) return;

    // Check if player has this block in inventory
    const invItem = this.inventory.find(i => i.id === block.id && i.count > 0);
    if (!invItem) return;

    // Cannot place directly inside player
    if (Math.floor(this.playerPos.x) === pos.x && Math.floor(this.playerPos.y) === pos.y && Math.floor(this.playerPos.z) === pos.z) {
      return;
    }

    invItem.count--;
    if (invItem.count <= 0) {
      this.inventory = this.inventory.filter(i => i !== invItem);
    }

    this.setBlock(pos, block);
    this.addCombatPopup({ x: pos.x, y: pos.y, z: pos.z }, `Placed`, '#00B8A9');
  }

  public addItemToInventory(id: string, name: string, kind: 'weapon' | 'block' | 'insert' | 'cowries' | 'item', color: string, count: number, data?: any) {
    const existing = this.inventory.find(i => i.id === id);
    if (existing) {
      existing.count += count;
    } else {
      this.inventory.push({ id, name, glyph: kind === 'block' ? '■' : '✦', kind, color, count, data });
    }
    this.notify();
  }

  public revive() {
    this.health = this.maxHealth;
    this.playerState = 'IDLE';
    this.playerPos = { x: 0.5, y: 0.5, z: this.getSurfaceZ(0, 0) + 1 };
    this.notify();
  }

  // Game Loop Tick
  public update(dt: number) {
    const now = performance.now();

    // Cooldown decrements
    if (this.attackCooldown > 0) this.attackCooldown = Math.max(0, this.attackCooldown - dt);
    if (this.dodgeCooldown > 0) this.dodgeCooldown = Math.max(0, this.dodgeCooldown - dt);
    Object.keys(this.skillCooldowns).forEach(k => {
      if (this.skillCooldowns[k] > 0) {
        this.skillCooldowns[k] = Math.max(0, this.skillCooldowns[k] - dt);
      }
    });

    // Player animation state
    this.playerStateTime += dt;
    if (this.playerState === 'ATTACK' && this.playerStateTime > 0.45) {
      this.playerState = 'IDLE';
    }

    // Apply movement physics
    if (this.health > 0) {
      this.playerPos.x += this.playerVelocity.x;
      this.playerPos.y += this.playerVelocity.y;

      // Friction
      this.playerVelocity.x *= 0.82;
      this.playerVelocity.y *= 0.82;
      if (Math.abs(this.playerVelocity.x) < 0.001) this.playerVelocity.x = 0;
      if (Math.abs(this.playerVelocity.y) < 0.001) this.playerVelocity.y = 0;

      // Snap to ground surface
      const surfaceZ = this.getSurfaceZ(this.playerPos.x, this.playerPos.y);
      this.playerPos.z = surfaceZ + 1;
    }

    // Smooth camera tracking
    const targetScreen = this.projection.projectPoint(this.playerPos);
    this.cameraPos.x += (-targetScreen.x - this.cameraPos.x) * 0.12;
    this.cameraPos.y += (-targetScreen.y - this.cameraPos.y) * 0.12;

    // Update Enemies AI
    this.enemies.forEach(enemy => {
      if (enemy.isDead) return;
      enemy.stateTime += dt;
      if (enemy.cooldown > 0) enemy.cooldown = Math.max(0, enemy.cooldown - dt);

      const dx = this.playerPos.x - enemy.position.x;
      const dy = this.playerPos.y - enemy.position.y;
      const dist = Math.hypot(dx, dy);

      // Aggro range: 7 blocks
      if (dist < 7.0 && dist > 1.2 && this.health > 0) {
        enemy.state = 'WALK';
        const speed = enemy.moveSpeed;
        enemy.position.x += (dx / dist) * speed;
        enemy.position.y += (dy / dist) * speed;
        enemy.position.z = this.getSurfaceZ(enemy.position.x, enemy.position.y) + 1;
      } else if (dist <= 1.2 && this.health > 0) {
        // Enemy attack
        if (enemy.cooldown <= 0) {
          enemy.state = 'ATTACK';
          enemy.cooldown = 1.2;
          if (!this.isDodgeRolling) {
            const damage = Math.max(2, enemy.attackPower - this.heroClass.baseStats.armour);
            this.health = Math.max(0, this.health - damage);
            this.addCombatPopup(this.playerPos, `-${damage}`, '#C1453B');
            if (this.health <= 0) {
              this.playerState = 'DIE';
            }
          }
        }
      } else {
        enemy.state = 'IDLE';
      }
    });

    // Loot pickup check
    this.groundLoot = this.groundLoot.filter(loot => {
      const dist = Math.hypot(loot.position.x - this.playerPos.x, loot.position.y - this.playerPos.y);
      if (dist < 1.4) {
        if (loot.kind === 'cowries') {
          this.cowries += loot.amount;
          this.addCombatPopup(this.playerPos, `+${loot.amount} Cowries`, '#FFD166');
        } else {
          this.addItemToInventory(loot.itemRefId, loot.name, loot.kind, loot.color, loot.amount);
          this.addCombatPopup(this.playerPos, `+${loot.name}`, loot.color);
        }
        return false;
      }
      return true;
    });

    // Clean up combat popups
    this.combatPopups = this.combatPopups.filter(p => now - p.createdAt < p.durationMs);

    this.notify();
  }
}
