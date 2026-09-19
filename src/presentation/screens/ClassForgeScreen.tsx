import React, { useState } from 'react';
import { CustomClassRepository } from '../../data/repositories/CustomClassRepository';
import { HeroClassDefinition } from '../../domain/models/HeroClass';
import { StratumButton } from '../components/StratumButton';
import { StratumPanel } from '../components/StratumPanel';
import { ArrowLeft, Shield, Swords, Zap, Check } from 'lucide-react';

interface ClassForgeScreenProps {
  onNavigate: (screen: string) => void;
}

export const ClassForgeScreen: React.FC<ClassForgeScreenProps> = ({ onNavigate }) => {
  const [name, setName] = useState('Ozo Vanguard');
  const [title, setTitle] = useState('Shield of the Sacred Mound');
  const [description, setDescription] = useState('Stands immovable before invading spirits, channeling the weight of ancestral bronze.');
  const [resourceName, setResourceName] = useState('Resolve');
  const [baseHealth, setBaseHealth] = useState(250);
  const [baseResource, setBaseResource] = useState(100);
  const [attackPower, setAttackPower] = useState(18);
  const [armour, setArmour] = useState(6);
  const [saved, setSaved] = useState(false);

  const handleSave = () => {
    const slug = name.toLowerCase().replace(/[^a-z0-9]/g, '_');
    const newClass: HeroClassDefinition = {
      id: `custom:${slug}_${Date.now().toString().slice(-4)}`,
      name,
      title,
      description,
      baseHealth,
      baseResource,
      resourceName,
      strength: 14,
      agility: 10,
      insight: 12,
      startingBlockIds: ['igbo:red_earth'],
      abilityIds: ['igbo:mma_cleave'],
      baseStats: {
        maxHealth: baseHealth,
        attackPower,
        armour,
        critChance: 0.10,
        critMultiplier: 1.5,
        attackSpeed: 1.0,
        attackRange: 1.6,
      },
      startingWeaponId: 'weapon:mma_nkwu'
    };

    CustomClassRepository.saveClass(newClass);
    setSaved(true);
    setTimeout(() => {
      onNavigate('HOME');
    }, 600);
  };

  return (
    <div className="flex flex-col h-full bg-[#14110E] text-[#F4EBDC] p-4 sm:p-6 lg:p-8 max-w-4xl mx-auto overflow-y-auto select-none">
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
            Hero Class Forge
          </h1>
          <p className="text-xs text-[#A1907A]">
            Define combat stats, resources, and warrior identity
          </p>
        </div>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Identity Panel */}
        <StratumPanel title="Identity & Title">
          <div className="flex flex-col gap-3">
            <div>
              <label className="text-xs font-bold text-[#A1907A] uppercase block mb-1">Class Name</label>
              <input
                type="text"
                value={name}
                onChange={e => setName(e.target.value)}
                className="w-full bg-[#0D0B09] border border-[#F4EBDC]/20 px-3 py-2 text-sm text-[#F4EBDC] cut-corner-sm outline-none focus:border-[#CD7F32]"
              />
            </div>

            <div>
              <label className="text-xs font-bold text-[#A1907A] uppercase block mb-1">Title / Honorific</label>
              <input
                type="text"
                value={title}
                onChange={e => setTitle(e.target.value)}
                className="w-full bg-[#0D0B09] border border-[#F4EBDC]/20 px-3 py-2 text-sm text-[#F4EBDC] cut-corner-sm outline-none focus:border-[#CD7F32]"
              />
            </div>

            <div>
              <label className="text-xs font-bold text-[#A1907A] uppercase block mb-1">Lore & Description</label>
              <textarea
                value={description}
                onChange={e => setDescription(e.target.value)}
                rows={3}
                className="w-full bg-[#0D0B09] border border-[#F4EBDC]/20 px-3 py-2 text-xs text-[#F4EBDC] cut-corner-sm outline-none focus:border-[#CD7F32] resize-none"
              />
            </div>

            <div>
              <label className="text-xs font-bold text-[#A1907A] uppercase block mb-1">Resource Name</label>
              <input
                type="text"
                value={resourceName}
                onChange={e => setResourceName(e.target.value)}
                className="w-full bg-[#0D0B09] border border-[#F4EBDC]/20 px-3 py-2 text-sm text-[#F4EBDC] cut-corner-sm outline-none focus:border-[#CD7F32]"
              />
            </div>
          </div>
        </StratumPanel>

        {/* Combat Stats Matrix */}
        <StratumPanel title="Combat Attribute Tuning">
          <div className="flex flex-col gap-4">
            <div>
              <div className="flex justify-between text-xs font-mono mb-1">
                <span className="text-[#FF8E85]">VITALITY (HP)</span>
                <span>{baseHealth}</span>
              </div>
              <input
                type="range"
                min="120"
                max="400"
                step="10"
                value={baseHealth}
                onChange={e => setBaseHealth(Number(e.target.value))}
                className="w-full accent-[#C1453B]"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs font-mono mb-1">
                <span className="text-[#70E7DC]">{resourceName.toUpperCase()}</span>
                <span>{baseResource}</span>
              </div>
              <input
                type="range"
                min="50"
                max="200"
                step="10"
                value={baseResource}
                onChange={e => setBaseResource(Number(e.target.value))}
                className="w-full accent-[#00B8A9]"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs font-mono mb-1">
                <span className="text-[#CD7F32]">ATTACK POWER</span>
                <span>{attackPower}</span>
              </div>
              <input
                type="range"
                min="10"
                max="35"
                value={attackPower}
                onChange={e => setAttackPower(Number(e.target.value))}
                className="w-full accent-[#CD7F32]"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs font-mono mb-1">
                <span className="text-[#F4EBDC]">ARMOUR DEFENCE</span>
                <span>{armour}</span>
              </div>
              <input
                type="range"
                min="0"
                max="15"
                value={armour}
                onChange={e => setArmour(Number(e.target.value))}
                className="w-full accent-[#A1907A]"
              />
            </div>

            <div className="pt-4 border-t border-[#F4EBDC]/10">
              <StratumButton
                variant="primary"
                size="lg"
                className="w-full"
                icon={saved ? <Check className="w-4 h-4" /> : <Shield className="w-4 h-4" />}
                onClick={handleSave}
              >
                {saved ? 'Class Forged!' : 'Forge & Register Class'}
              </StratumButton>
            </div>
          </div>
        </StratumPanel>
      </div>
    </div>
  );
};
