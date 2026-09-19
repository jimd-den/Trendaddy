import React, { useState } from 'react';
import { ContentPackRepository } from '../../data/repositories/ContentPackRepository';
import { StratumButton } from '../components/StratumButton';
import { StratumPanel } from '../components/StratumPanel';
import { ArrowLeft, Sparkles, Check, Wand2 } from 'lucide-react';
import { ContentPack } from '../../domain/models/ContentPack';
import { BUILTIN_IGBO_PACK } from '../../data/builtin/IgboContentPackData';

interface ForgeScreenProps {
  onNavigate: (screen: string) => void;
}

export const ForgeScreen: React.FC<ForgeScreenProps> = ({ onNavigate }) => {
  const [prompt, setPrompt] = useState('Nri Sunken Shrines and Sacred Python Caves');
  const [isWeaving, setIsWeaving] = useState(false);
  const [generatedPack, setGeneratedPack] = useState<any | null>(null);
  const [activeTab, setActiveTab] = useState<'blocks' | 'biomes' | 'lore'>('blocks');

  const handleWeavePack = async () => {
    setIsWeaving(true);
    try {
      const res = await fetch('/api/generate-pack', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt }),
      });
      const data = await res.json();
      if (data.pack) {
        setGeneratedPack(data.pack);
      }
    } catch (e) {
      console.error('Failed to generate pack', e);
    } finally {
      setIsWeaving(false);
    }
  };

  const handleAcceptPack = () => {
    if (!generatedPack) return;
    const fullPack: ContentPack = {
      ...BUILTIN_IGBO_PACK,
      id: `pack_${Date.now()}`,
      name: generatedPack.name || 'Forged Realm',
      description: generatedPack.description || '',
      palette: generatedPack.palette || BUILTIN_IGBO_PACK.palette,
      blocks: generatedPack.blocks || BUILTIN_IGBO_PACK.blocks,
      biomes: generatedPack.biomes || BUILTIN_IGBO_PACK.biomes,
      heroClasses: generatedPack.heroClasses || BUILTIN_IGBO_PACK.heroClasses,
      loreEntries: generatedPack.lore || BUILTIN_IGBO_PACK.loreEntries,
    };

    ContentPackRepository.savePack(fullPack);
    onNavigate('HOME');
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
            AI Theme Weaver
          </h1>
          <p className="text-xs text-[#A1907A]">
            Generate mythological content packs, blocks, biomes, and lore fragments
          </p>
        </div>
      </header>

      {/* Input Prompt Panel */}
      <StratumPanel title="Weave Configuration" className="mb-6">
        <div className="flex flex-col gap-3">
          <label className="text-xs font-bold text-[#A1907A] uppercase">
            Realm Theme & Mythology Prompt
          </label>
          <div className="flex flex-col sm:flex-row gap-2">
            <input
              type="text"
              value={prompt}
              onChange={e => setPrompt(e.target.value)}
              placeholder="e.g. Copper Mines of Ala Igbo with Sacred Bells"
              className="flex-1 bg-[#0D0B09] border border-[#F4EBDC]/20 px-3 py-2 text-sm text-[#F4EBDC] cut-corner-sm outline-none focus:border-[#CD7F32]"
            />
            <StratumButton
              variant="accent"
              size="md"
              icon={<Sparkles className="w-4 h-4" />}
              onClick={handleWeavePack}
              disabled={isWeaving}
            >
              {isWeaving ? 'Weaving...' : 'Weave Content Pack'}
            </StratumButton>
          </div>

          {/* Preset Prompts */}
          <div className="flex flex-wrap gap-1.5 mt-2">
            {[
              'Ancient Benin Castings',
              'Ogbunike Cave Shadows',
              'Thunder Shrines of Amadioha',
              'Idemili Python Sanctum',
            ].map(preset => (
              <button
                key={preset}
                onClick={() => setPrompt(preset)}
                className="text-[11px] px-2.5 py-1 bg-[#1A1612] hover:bg-[#25201A] border border-[#F4EBDC]/10 cut-corner-sm text-[#A1907A] hover:text-[#F4EBDC]"
              >
                {preset}
              </button>
            ))}
          </div>
        </div>
      </StratumPanel>

      {/* Generated Result Preview */}
      {generatedPack && (
        <StratumPanel
          title={generatedPack.name}
          subtitle={generatedPack.description}
          actions={
            <StratumButton
              variant="primary"
              size="sm"
              icon={<Check className="w-4 h-4" />}
              onClick={handleAcceptPack}
            >
              Equip & Load Realm
            </StratumButton>
          }
        >
          {/* Tabs */}
          <div className="flex gap-2 border-b border-[#F4EBDC]/10 pb-2 mb-4">
            {(['blocks', 'biomes', 'lore'] as const).map(tab => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`px-3 py-1 text-xs font-bold uppercase cut-corner-sm border ${
                  activeTab === tab ? 'bg-[#CD7F32] text-black border-transparent' : 'bg-[#14110E] text-[#A1907A] border-[#F4EBDC]/15'
                }`}
              >
                {tab}
              </button>
            ))}
          </div>

          {/* Tab Content */}
          {activeTab === 'blocks' && (
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              {generatedPack.blocks?.map((b: any) => (
                <div
                  key={b.id}
                  className="p-3 bg-[#14110E] border border-[#F4EBDC]/10 cut-corner-sm flex items-center gap-3"
                >
                  <div
                    className="w-10 h-10 cut-corner-sm border border-black/50 shrink-0 flex items-center justify-center font-bold text-white shadow"
                    style={{ backgroundColor: b.topColor }}
                  >
                    {b.glyph || '■'}
                  </div>
                  <div>
                    <span className="font-bold text-xs block text-[#F4EBDC]">{b.displayName}</span>
                    <span className="text-[10px] text-[#A1907A] font-mono">Hardness: {b.hardness}</span>
                  </div>
                </div>
              ))}
            </div>
          )}

          {activeTab === 'biomes' && (
            <div className="flex flex-col gap-3">
              {generatedPack.biomes?.map((bio: any) => (
                <div key={bio.id} className="p-3 bg-[#14110E] border border-[#F4EBDC]/10 cut-corner-sm">
                  <h4 className="font-bold text-sm text-[#00B8A9] mb-1">{bio.name}</h4>
                  <p className="text-xs text-[#A1907A] leading-relaxed">{bio.description}</p>
                </div>
              ))}
            </div>
          )}

          {activeTab === 'lore' && (
            <div className="flex flex-col gap-3">
              {generatedPack.lore?.map((l: any, i: number) => (
                <div key={i} className="p-3 bg-[#14110E] border border-[#F4EBDC]/10 cut-corner-sm">
                  <span className="text-[10px] font-bold uppercase text-[#CD7F32] block mb-1">
                    {l.category || 'ARTIFACT'}
                  </span>
                  <h4 className="font-bold text-sm text-[#F4EBDC] mb-1">{l.title}</h4>
                  <p className="text-xs text-[#A1907A] font-serif leading-relaxed italic">{l.body}</p>
                </div>
              ))}
            </div>
          )}
        </StratumPanel>
      )}
    </div>
  );
};
