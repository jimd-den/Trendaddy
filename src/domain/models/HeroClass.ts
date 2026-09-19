export interface CombatStats {
  maxHealth: number;
  attackPower: number;
  armour: number;
  critChance: number;
  critMultiplier: number;
  attackSpeed: number;
  attackRange: number;
  lifeSteal?: number;
  resistances?: Record<string, number>;
}

export interface SkillDefinition {
  id: string;
  name: string;
  cost: number;
  cooldown: number; // in seconds
  color: string;
  range: number;
  damageMultiplier: number;
  radius?: number;
  description: string;
}

export interface HeroClassDefinition {
  id: string;
  name: string;
  title: string;
  description: string;
  baseHealth: number;
  baseResource: number;
  resourceName: string;
  strength: number;
  agility: number;
  insight: number;
  startingBlockIds: string[];
  abilityIds: string[];
  baseStats: CombatStats;
  startingWeaponId?: string;
}
