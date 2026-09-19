export interface WeaponDefinition {
  id: string;
  name: string;
  kind: 'sword' | 'staff' | 'axe' | 'spear' | 'cleaver';
  glyph: string;
  attackPower: number;
  critChance: number;
  attackSpeed: number;
  sockets: number;
  slottedInserts: (string | null)[];
  description?: string;
  imageData?: string;
}

export interface WeaponAnchor {
  x: number;
  y: number;
  rotationDegrees: number;
  behindBody: boolean;
}

export interface WeaponFit {
  offsetX: number;
  offsetY: number;
  scale: number;
  mirror: boolean;
}

export interface WeaponRig {
  sheetId: string;
  anchors: Record<string, WeaponAnchor>; // frame key -> anchor
}
