import React, { useState } from 'react';
import { SettingsRepository, HudStyle } from '../../data/repositories/SettingsRepository';
import { IGBO_LORE } from '../../data/builtin/IgboContentPackData';
import { StratumButton } from '../components/StratumButton';
import { StratumPanel } from '../components/StratumPanel';
import { ArrowLeft, BookOpen, Dice5, Swords, Eye } from 'lucide-react';

interface CreatorStudioScreenProps {
  onNavigate: (screen: string) => void;
}

export const CreatorStudioScreen: React.FC<CreatorStudioScreenProps> = ({ onNavigate }) => {
  const [hudStyle, setHudStyle] = useState<HudStyle>(SettingsRepository.getSettings().hudStyle);
  const [activeTab, setActiveTab] = useState<'hud' | 'codex' | 'penpaper' | 'arena'>('hud');

  // Pen & paper dice roll state
  const [diceRoll, setDiceRoll] = useState<number | null>(null);
  const [modifier, setModifier] = useState(3);
  const [diceLog, setDiceLog] = useState<string[]>([]);

  const handleHudChange = (style: HudStyle) => {
    setHudStyle(style);
    SettingsRepository.updateSettings({ hudStyle: style });
  };

  const handleRollDice = () => {
    const raw = Math.floor(Math.random() * 20) + 1;
    const total = raw + modifier;
    setDiceRoll(total);
    setDiceLog(prev => [`Rolled d20: ${raw} + ${modifier} = ${total} (${raw === 20 ? 'CRITICAL SUCCESS!' : raw === 1 ? 'CRITICAL FUMBLE!' : 'Checked'})`, ...prev.slice(0, 5)]);
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
            Creator Studio
          </h1>
          <p className="text-xs text-[#A1907A]">
            Diablo Chameleon HUD • Sacred Codex • Pen & Paper RPG Studio
          </p>
        </div>
      </header>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-[#F4EBDC]/10 pb-3 mb-6">
        {[
          { id: 'hud', label: 'Diablo Chameleon HUD', icon: <Eye className="w-4 h-4" /> },
          { id: 'codex', label: 'Sacred Codex', icon: <BookOpen className="w-4 h-4" /> },
          { id: 'penpaper', label: 'Pen & Paper RPG', icon: <Dice5 className="w-4 h-4" /> },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as any)}
            className={`flex items-center gap-2 px-3 py-2 text-xs font-bold uppercase cut-corner-sm border transition-all ${
              activeTab === tab.id
                ? 'bg-[#CD7F32] text-black border-transparent'
                : 'bg-[#1A1612] text-[#A1907A] border-[#F4EBDC]/10 hover:text-[#F4EBDC]'
            }`}
          >
            {tab.icon}
            <span>{tab.label}</span>
          </button>
        ))}
      </div>

      {/* Tab: Diablo Chameleon HUD */}
      {activeTab === 'hud' && (
        <StratumPanel title="Diablo Chameleon HUD Style" subtitle="Select interface layout archetype">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mb-4">
            {[
              { id: 'stratum', name: 'Stratum Minimal', desc: 'Voxel bronze meters with Igbo-Ukwu cut corners' },
              { id: 'diablo1', name: 'Diablo 1 Gothic', desc: 'Arched brass health and mana globes' },
              { id: 'diablo2', name: 'Diablo 2 Resurrected', desc: 'Dual angel/demon globes with stamina gauge' },
              { id: 'diablo3', name: 'Diablo 3 Ornate', desc: 'Baroque filigree with curved resource arcs' },
              { id: 'diablo4', name: 'Diablo 4 Dark Grim', desc: 'Dark grim minimal stone tablets with visceral blood' },
            ].map(item => (
              <div
                key={item.id}
                onClick={() => handleHudChange(item.id as HudStyle)}
                className={`p-3 cut-corner-sm border cursor-pointer transition-all ${
                  hudStyle === item.id
                    ? 'bg-[#CD7F32]/20 border-[#CD7F32] text-[#F4EBDC]'
                    : 'bg-[#14110E] border-[#F4EBDC]/10 hover:border-[#F4EBDC]/30 text-[#A1907A]'
                }`}
              >
                <span className="font-bold text-sm block mb-1">{item.name}</span>
                <p className="text-xs text-[#A1907A] leading-relaxed">{item.desc}</p>
                {hudStyle === item.id && (
                  <span className="text-[10px] text-[#00B8A9] font-bold uppercase mt-2 block">
                    Active HUD
                  </span>
                )}
              </div>
            ))}
          </div>
        </StratumPanel>
      )}

      {/* Tab: Sacred Codex */}
      {activeTab === 'codex' && (
        <div className="flex flex-col gap-4">
          <StratumPanel title="Sacred Lore of Ala Igbo" subtitle="Archaeology, Nsibidi, and Ritual Artefacts">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {IGBO_LORE.map(lore => (
                <div key={lore.id} className="p-4 bg-[#14110E] border border-[#F4EBDC]/10 cut-corner-sm">
                  <span className="text-[10px] font-bold uppercase text-[#CD7F32] block mb-1">
                    {lore.category}
                  </span>
                  <h4 className="font-bold text-base text-[#F4EBDC] mb-2">{lore.title}</h4>
                  <p className="text-xs text-[#A1907A] font-serif leading-relaxed italic">{lore.body}</p>
                </div>
              ))}
            </div>
          </StratumPanel>
        </div>
      )}

      {/* Tab: Pen & Paper RPG */}
      {activeTab === 'penpaper' && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <StratumPanel title="D20 Tabletop Roller" subtitle="Check combat actions and rituals against Ala Igbo rulebook">
            <div className="flex flex-col items-center justify-center p-6 bg-[#0D0B09] cut-corner-md border border-[#F4EBDC]/10 mb-4">
              <span className="text-5xl font-black text-[#CD7F32] font-mono mb-2">
                {diceRoll !== null ? diceRoll : '--'}
              </span>
              <span className="text-xs text-[#A1907A]">Total with modifier</span>
            </div>

            <div className="flex items-center gap-3 mb-4">
              <label className="text-xs font-bold text-[#A1907A] uppercase">Skill Modifier:</label>
              <input
                type="number"
                value={modifier}
                onChange={e => setModifier(Number(e.target.value))}
                className="w-16 bg-[#0D0B09] border border-[#F4EBDC]/20 px-2 py-1 text-sm text-center text-[#F4EBDC] cut-corner-sm"
              />
              <StratumButton variant="primary" size="md" onClick={handleRollDice}>
                Roll D20
              </StratumButton>
            </div>

            <div className="bg-[#14110E] p-3 cut-corner-sm border border-[#F4EBDC]/10 max-h-[140px] overflow-y-auto">
              <span className="text-[10px] font-bold uppercase text-[#A1907A] block mb-1">Roll History</span>
              {diceLog.map((log, idx) => (
                <p key={idx} className="text-xs font-mono text-[#F4EBDC]/80 py-0.5">{log}</p>
              ))}
            </div>
          </StratumPanel>

          <StratumPanel title="Ala Igbo Tabletop Primer">
            <div className="flex flex-col gap-2.5 text-xs text-[#A1907A] leading-relaxed">
              <p>
                <strong className="text-[#F4EBDC]">Ozo Title:</strong> Requires DC 15 Charisma/Endurance. Grants +2 to Armor checks and the ability to command spectral defenders.
              </p>
              <p>
                <strong className="text-[#F4EBDC]">Amadioha Invocation:</strong> DC 14 Insight check. On success, lightning strikes target area dealing 3d8 thunder damage.
              </p>
              <p>
                <strong className="text-[#F4EBDC]">Dibia Divination:</strong> DC 12 Communion check to reveal hidden veins of bronze or detect spirit ambush before it triggers.
              </p>
            </div>
          </StratumPanel>
        </div>
      )}
    </div>
  );
};
