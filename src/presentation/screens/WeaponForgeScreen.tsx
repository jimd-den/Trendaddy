import React, { useState } from 'react';
import { StratumButton } from '../components/StratumButton';
import { StratumPanel } from '../components/StratumPanel';
import { ArrowLeft, Hammer, Swords, Check } from 'lucide-react';
import { IGBO_WEAPONS } from '../../data/builtin/IgboContentPackData';
import { WeaponDefinition } from '../../domain/models/Weapon';
import { CharacterRepository } from '../../data/repositories/CharacterRepository';

interface WeaponForgeScreenProps {
  onNavigate: (screen: string) => void;
}

export const WeaponForgeScreen: React.FC<WeaponForgeScreenProps> = ({ onNavigate }) => {
  const [weapons, setWeapons] = useState<WeaponDefinition[]>(IGBO_WEAPONS);
  const [selectedWeaponId, setSelectedWeaponId] = useState(CharacterRepository.getSelectedWeaponId());
  const [weaponName, setWeaponName] = useState('Forged Bronze Glaive');
  const [kind, setKind] = useState<'cleaver' | 'spear' | 'axe' | 'staff'>('cleaver');
  const [attackPower, setAttackPower] = useState(20);
  const [critChance, setCritChance] = useState(0.12);
  const [sockets, setSockets] = useState(2);

  const selectedWeapon = weapons.find(w => w.id === selectedWeaponId) || weapons[0];

  const handleForge = () => {
    const glyphMap = { cleaver: '⚔', spear: '⤋', axe: '🪓', staff: '⚚' };
    const newWeapon: WeaponDefinition = {
      id: `weapon:${weaponName.toLowerCase().replace(/[^a-z0-9]/g, '_')}_${Date.now().toString().slice(-4)}`,
      name: weaponName,
      kind,
      glyph: glyphMap[kind] || '⚔',
      attackPower,
      critChance,
      attackSpeed: 1.0,
      sockets,
      slottedInserts: Array(sockets).fill(null),
      description: 'Newly cast weapon from the Igbo-Ukwu lost wax foundry.'
    };

    setWeapons(prev => [...prev, newWeapon]);
    setSelectedWeaponId(newWeapon.id);
    CharacterRepository.setSelectedWeaponId(newWeapon.id);
  };

  const handleEquip = (id: string) => {
    setSelectedWeaponId(id);
    CharacterRepository.setSelectedWeaponId(id);
  };

  return (
    <div className="flex flex-col h-full bg-[#14110E] text-[#F4EBDC] p-4 sm:p-6 lg:p-8 max-w-5xl mx-auto overflow-y-auto select-none">
      <header className="flex items-center gap-3 border-b border-[#F4EBDC]/10 pb-4 mb-6">
        <StratumButton
          variant="secondary"
          size="sm"
          icon={<ArrowLeft className="w-4 h-4" />}
          onClick={() => onNavigate('HOME')}
        >
          Home
        </StratumButton>
        <div>
          <h1 className="text-xl sm:text-2xl font-black uppercase tracking-wider text-[#F4EBDC]">
            Armory & Weapon Forge
          </h1>
          <p className="text-xs text-[#A1907A]">
            Lost-wax bronze metallurgy, socket counts, and combat DPS tuning
          </p>
        </div>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Available Weapons */}
        <StratumPanel title="Weapons in Armory">
          <div className="flex flex-col gap-2.5">
            {weapons.map(w => (
              <div
                key={w.id}
                onClick={() => handleEquip(w.id)}
                className={`flex items-center justify-between p-3 cut-corner-sm border cursor-pointer transition-all ${
                  w.id === selectedWeaponId
                    ? 'bg-[#CD7F32]/20 border-[#CD7F32] text-[#F4EBDC]'
                    : 'bg-[#1A1612] border-[#F4EBDC]/10 hover:border-[#F4EBDC]/30 text-[#A1907A]'
                }`}
              >
                <div className="flex items-center gap-3">
                  <span className="text-xl text-[#CD7F32]">{w.glyph}</span>
                  <div>
                    <span className="font-bold text-sm block">{w.name}</span>
                    <span className="text-xs text-[#A1907A] font-mono">
                      +{w.attackPower} AP • {Math.round(w.critChance * 100)}% Crit • {w.sockets} Sockets
                    </span>
                  </div>
                </div>

                {w.id === selectedWeaponId && (
                  <span className="text-xs text-[#00B8A9] font-bold uppercase">Equipped</span>
                )}
              </div>
            ))}
          </div>
        </StratumPanel>

        {/* Forge New Weapon */}
        <StratumPanel title="Foundry Crucible" subtitle="Cast bronze into new shapes">
          <div className="flex flex-col gap-3">
            <div>
              <label className="text-xs font-bold text-[#A1907A] uppercase block mb-1">Weapon Name</label>
              <input
                type="text"
                value={weaponName}
                onChange={e => setWeaponName(e.target.value)}
                className="w-full bg-[#0D0B09] border border-[#F4EBDC]/20 px-3 py-2 text-sm text-[#F4EBDC] cut-corner-sm outline-none focus:border-[#CD7F32]"
              />
            </div>

            <div>
              <label className="text-xs font-bold text-[#A1907A] uppercase block mb-1">Weapon Type</label>
              <div className="grid grid-cols-4 gap-2">
                {(['cleaver', 'spear', 'axe', 'staff'] as const).map(k => (
                  <button
                    key={k}
                    onClick={() => setKind(k)}
                    className={`py-2 text-xs font-bold uppercase cut-corner-sm border ${
                      kind === k ? 'bg-[#CD7F32] text-black border-transparent' : 'bg-[#14110E] text-[#A1907A] border-[#F4EBDC]/15'
                    }`}
                  >
                    {k}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs font-mono mb-1">
                <span className="text-[#CD7F32]">ATTACK POWER</span>
                <span>+{attackPower}</span>
              </div>
              <input
                type="range"
                min="12"
                max="40"
                value={attackPower}
                onChange={e => setAttackPower(Number(e.target.value))}
                className="w-full accent-[#CD7F32]"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs font-mono mb-1">
                <span className="text-[#00B8A9]">SOCKET CAPACITY</span>
                <span>{sockets} Sockets</span>
              </div>
              <input
                type="range"
                min="1"
                max="4"
                value={sockets}
                onChange={e => setSockets(Number(e.target.value))}
                className="w-full accent-[#00B8A9]"
              />
            </div>

            <div className="pt-3 border-t border-[#F4EBDC]/10">
              <StratumButton
                variant="primary"
                size="md"
                className="w-full"
                icon={<Hammer className="w-4 h-4" />}
                onClick={handleForge}
              >
                Pour Bronze Crucible
              </StratumButton>
            </div>
          </div>
        </StratumPanel>
      </div>
    </div>
  );
};
