import React, { useEffect, useState } from 'react';
import { ContentPackRepository } from '../../data/repositories/ContentPackRepository';
import { CharacterRepository } from '../../data/repositories/CharacterRepository';
import { CustomClassRepository } from '../../data/repositories/CustomClassRepository';
import { StratumButton } from '../components/StratumButton';
import { StratumPanel } from '../components/StratumPanel';
import { StratumChip } from '../components/StratumChip';
import { IdlePortrait } from '../components/IdlePortrait';
import { Shield, Sparkles, Wand2, Hammer, Settings, BookOpen, AlertCircle, Play } from 'lucide-react';

interface HomeScreenProps {
  onNavigate: (screen: string) => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({ onNavigate }) => {
  const [activePack, setActivePack] = useState(ContentPackRepository.getActivePack());
  const [classes, setClasses] = useState(CustomClassRepository.getAllClasses());
  const [characters, setCharacters] = useState(CharacterRepository.getAll());
  const [selectedClassId, setSelectedClassId] = useState(CharacterRepository.getSelectedHeroClassId());
  const [selectedSheetId, setSelectedSheetId] = useState(CharacterRepository.getSelectedHeroSheetId());
  const [unpackedCharacters, setUnpackedCharacters] = useState(CharacterRepository.getUnpackedCharacters());

  useEffect(() => {
    const unsubPack = ContentPackRepository.subscribe(() => {
      setActivePack(ContentPackRepository.getActivePack());
    });
    const unsubClass = CustomClassRepository.subscribe(() => {
      setClasses(CustomClassRepository.getAllClasses());
    });
    const unsubChar = CharacterRepository.subscribe(() => {
      setCharacters(CharacterRepository.getAll());
      setSelectedClassId(CharacterRepository.getSelectedHeroClassId());
      setSelectedSheetId(CharacterRepository.getSelectedHeroSheetId());
      setUnpackedCharacters(CharacterRepository.getUnpackedCharacters());
    });

    return () => {
      unsubPack();
      unsubClass();
      unsubChar();
    };
  }, []);

  const currentClass = classes.find(c => c.id === selectedClassId) || classes[0];
  const currentSheet = characters.find(c => c.id === selectedSheetId) || characters[0];
  const heroCharacters = characters.filter(c => c.role === 'hero');

  const handleSelectClass = (id: string) => {
    setSelectedClassId(id);
    CharacterRepository.setSelectedHeroClassId(id);
  };

  const handleSelectSheet = (id: string) => {
    setSelectedSheetId(id);
    CharacterRepository.setSelectedHeroSheetId(id);
  };

  const handleQuickPack = (charId: string) => {
    CharacterRepository.packCharacterSheet(charId);
    CharacterRepository.setSelectedHeroSheetId(charId);
  };

  return (
    <div className="flex flex-col h-full overflow-y-auto bg-[#14110E] text-[#F4EBDC] p-4 sm:p-6 lg:p-8 max-w-6xl mx-auto select-none">
      {/* Top Bar: Title & Pack Stats */}
      <header className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-[#F4EBDC]/10 pb-4 mb-6">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xl text-[#CD7F32]">❖</span>
            <h1 className="text-2xl sm:text-3xl font-black uppercase tracking-wider text-[#F4EBDC]">
              Stratum
            </h1>
            <span className="text-xs px-2 py-0.5 bg-[#CD7F32]/20 border border-[#CD7F32]/50 text-[#CD7F32] font-mono rounded">
              v2.0
            </span>
          </div>
          <p className="text-xs text-[#A1907A] mt-1 font-mono">
            {activePack.name} • {activePack.blocks.length} blocks • {activePack.biomes.length} regions • {classes.length} classes
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <StratumButton
            variant="secondary"
            size="sm"
            icon={<BookOpen className="w-4 h-4 text-[#CD7F32]" />}
            onClick={() => onNavigate('STUDIO')}
          >
            Studio
          </StratumButton>
          <StratumButton
            variant="secondary"
            size="sm"
            icon={<Settings className="w-4 h-4 text-[#A1907A]" />}
            onClick={() => onNavigate('SETTINGS')}
          >
            Settings
          </StratumButton>
        </div>
      </header>

      {/* BluePrint Banner: "N characters drawn but not packed" */}
      {unpackedCharacters.length > 0 && (
        <div className="mb-6 p-4 bg-amber-950/40 border-2 border-amber-600/50 cut-corner-md flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-lg shadow-black/40">
          <div className="flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-amber-400 shrink-0" />
            <div>
              <h4 className="text-sm font-bold text-amber-300 uppercase tracking-wide">
                {unpackedCharacters.length} Character{unpackedCharacters.length > 1 ? 's' : ''} Drawn But Not Packed
              </h4>
              <p className="text-xs text-amber-200/80">
                Poses exist on disk but aren't packed into sprite sheets yet. Pack them to wear them immediately.
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <StratumButton
              variant="accent"
              size="sm"
              onClick={() => handleQuickPack(unpackedCharacters[0].id)}
            >
              Pack & Wear "{unpackedCharacters[0].name}"
            </StratumButton>
            <StratumButton
              variant="quiet"
              size="sm"
              onClick={() => onNavigate('POSES')}
            >
              Open Forge
            </StratumButton>
          </div>
        </div>
      )}

      {/* Main Hero Selection & Appearance Area */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        {/* Left Column: Hero Class Selector */}
        <div className="lg:col-span-2 flex flex-col gap-4">
          <StratumPanel
            title="Hero Class"
            subtitle="Choose your lineage and combat discipline"
            actions={
              <button
                onClick={() => onNavigate('CLASSES')}
                className="text-xs text-[#CD7F32] hover:text-[#E5984A] font-bold uppercase tracking-wider cursor-pointer"
              >
                + Build Class
              </button>
            }
          >
            {/* Class Chips */}
            <div className="flex flex-wrap gap-2 mb-4">
              {classes.map(cls => (
                <StratumChip
                  key={cls.id}
                  label={cls.name}
                  selected={cls.id === selectedClassId}
                  onClick={() => handleSelectClass(cls.id)}
                />
              ))}
            </div>

            {/* Selected Class Details */}
            {currentClass && (
              <div className="bg-[#14110E] p-4 cut-corner-sm border border-[#F4EBDC]/10">
                <div className="flex items-center justify-between mb-2">
                  <h4 className="font-bold text-base text-[#F4EBDC] uppercase">
                    {currentClass.name}
                  </h4>
                  <span className="text-xs text-[#CD7F32] font-mono">
                    {currentClass.title}
                  </span>
                </div>
                <p className="text-xs text-[#A1907A] leading-relaxed mb-4">
                  {currentClass.description}
                </p>

                {/* Base Stats Matrix */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-2 border-t border-[#F4EBDC]/10 text-xs font-mono">
                  <div>
                    <span className="text-[#A1907A] block text-[10px]">HEALTH</span>
                    <span className="text-[#FF8E85] font-bold">{currentClass.baseHealth} HP</span>
                  </div>
                  <div>
                    <span className="text-[#A1907A] block text-[10px]">{currentClass.resourceName.toUpperCase()}</span>
                    <span className="text-[#70E7DC] font-bold">{currentClass.baseResource}</span>
                  </div>
                  <div>
                    <span className="text-[#A1907A] block text-[10px]">ATTACK POWER</span>
                    <span className="text-[#CD7F32] font-bold">{currentClass.baseStats.attackPower}</span>
                  </div>
                  <div>
                    <span className="text-[#A1907A] block text-[10px]">ARMOUR</span>
                    <span className="text-[#F4EBDC] font-bold">{currentClass.baseStats.armour}</span>
                  </div>
                </div>
              </div>
            )}
          </StratumPanel>

          {/* Character Appearance / Sprite Picker */}
          <StratumPanel
            title="Appearance"
            subtitle="Select character sprite or forged pose set"
            actions={
              <button
                onClick={() => onNavigate('POSES')}
                className="text-xs text-[#00B8A9] hover:text-[#00D4C3] font-bold uppercase tracking-wider cursor-pointer"
              >
                + Pose Forge
              </button>
            }
          >
            <div className="flex flex-wrap gap-2">
              {heroCharacters.map(char => (
                <StratumChip
                  key={char.id}
                  label={char.name}
                  selected={char.id === selectedSheetId}
                  roleBadge={char.role}
                  unpackedNotice={!char.isPacked && Object.keys(char.poses).length > 0}
                  onClick={() => handleSelectSheet(char.id)}
                />
              ))}
            </div>
          </StratumPanel>
        </div>

        {/* Right Column: Live Idle Portrait & Descend CTA */}
        <div className="flex flex-col gap-4">
          <StratumPanel title="Active Hero Rig" className="flex flex-col items-center justify-center text-center">
            <div className="my-2">
              <IdlePortrait character={currentSheet} size={150} />
            </div>
            <h3 className="font-bold text-lg text-[#F4EBDC] uppercase mt-2">
              {currentClass?.name}
            </h3>
            <p className="text-xs text-[#CD7F32] font-mono">
              Wearing: {currentSheet?.name}
            </p>

            <div className="w-full mt-6 pt-4 border-t border-[#F4EBDC]/10 flex flex-col gap-2">
              <StratumButton
                variant="primary"
                size="lg"
                className="w-full text-center py-4"
                icon={<Play className="w-5 h-5 fill-current" />}
                onClick={() => onNavigate('PLAY')}
              >
                Descend Into Ala Igbo
              </StratumButton>
            </div>
          </StratumPanel>

          {/* Quick Studio Forges */}
          <div className="grid grid-cols-2 gap-2">
            <StratumButton
              variant="secondary"
              size="sm"
              icon={<Wand2 className="w-3.5 h-3.5 text-[#00B8A9]" />}
              onClick={() => onNavigate('FORGE')}
            >
              Pack Forge
            </StratumButton>
            <StratumButton
              variant="secondary"
              size="sm"
              icon={<Hammer className="w-3.5 h-3.5 text-[#CD7F32]" />}
              onClick={() => onNavigate('WEAPONS')}
            >
              Weapon Forge
            </StratumButton>
          </div>
        </div>
      </div>

      {/* Footer Lore Teaser */}
      <footer className="mt-auto pt-6 border-t border-[#F4EBDC]/10 text-center text-xs text-[#A1907A]">
        <p className="font-serif italic">
          "Rope, insect, and leaf were pressed into wax and lost to the fire, and what came out was bronze that remembered them."
        </p>
      </footer>
    </div>
  );
};
