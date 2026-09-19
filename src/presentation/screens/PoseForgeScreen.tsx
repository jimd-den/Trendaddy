import React, { useState, useEffect } from 'react';
import { CharacterRepository, generateProceduralCharacterCanvas } from '../../data/repositories/CharacterRepository';
import { CharacterAggregate, CharacterRole } from '../../domain/models/Character';
import { POSE_SCRIPT_POSES, PoseDefinition } from '../../domain/services/PoseScript';
import { StratumButton } from '../components/StratumButton';
import { StratumPanel } from '../components/StratumPanel';
import { StratumChip } from '../components/StratumChip';
import { ArrowLeft, Sparkles, CheckCircle, Play, PackageCheck, AlertCircle, Trash2 } from 'lucide-react';

interface PoseForgeScreenProps {
  onNavigate: (screen: string) => void;
}

export const PoseForgeScreen: React.FC<PoseForgeScreenProps> = ({ onNavigate }) => {
  const [characters, setCharacters] = useState<CharacterAggregate[]>(CharacterRepository.getAll());
  const [selectedCharId, setSelectedCharId] = useState<string>(characters[0]?.id || 'hero:dike_ozo');
  const [charName, setCharName] = useState('New Warrior');
  const [role, setRole] = useState<CharacterRole>('hero');
  const [activeStep, setActiveStep] = useState(0);
  const [isGenerating, setIsGenerating] = useState(false);
  const [previewState, setPreviewState] = useState<'IDLE' | 'WALK' | 'ATTACK' | 'HURT' | 'DIE'>('IDLE');
  const [animFrame, setAnimFrame] = useState(0);

  useEffect(() => {
    const unsub = CharacterRepository.subscribe(() => {
      setCharacters(CharacterRepository.getAll());
    });
    return unsub;
  }, []);

  // Animation preview timer
  useEffect(() => {
    const timer = setInterval(() => {
      setAnimFrame(prev => (prev + 1) % 8);
    }, 130);
    return () => clearInterval(timer);
  }, []);

  const currentChar = characters.find(c => c.id === selectedCharId);
  const currentPoseDef = POSE_SCRIPT_POSES[activeStep] || POSE_SCRIPT_POSES[0];
  const poseKeys = currentChar ? Object.keys(currentChar.poses) : [];
  const drawnCount = poseKeys.length;

  const handleCreateNew = () => {
    const cleanId = `${role}:${charName.toLowerCase().replace(/[^a-z0-9]/g, '_')}_${Date.now().toString().slice(-4)}`;
    const newChar: CharacterAggregate = {
      id: cleanId,
      name: charName,
      role,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      hasReference: true,
      poses: {},
      guides: { style: 'stratum_bronze' },
      isPacked: false,
      weaponFit: { offsetX: 0, offsetY: 0, scale: 1.0, mirror: false }
    };
    CharacterRepository.saveCharacter(newChar);
    setSelectedCharId(newChar.id);
  };

  const handleDrawPose = (stepIndex: number) => {
    if (!currentChar) return;
    const def = POSE_SCRIPT_POSES[stepIndex];
    const imgData = generateProceduralCharacterCanvas(currentChar.role, currentChar.name, def.state, stepIndex);

    CharacterRepository.addPoseFrame(currentChar.id, {
      stepIndex,
      state: def.state,
      poseName: def.name,
      imageData: imgData,
      createdAt: Date.now(),
    });
  };

  const handleGenerateAllPoses = () => {
    if (!currentChar) return;
    setIsGenerating(true);

    let current = 0;
    const interval = setInterval(() => {
      if (current >= POSE_SCRIPT_POSES.length) {
        clearInterval(interval);
        setIsGenerating(false);
        // Automatic Packing (Blueprint Step 1)
        CharacterRepository.packCharacterSheet(currentChar.id);
        CharacterRepository.setSelectedHeroSheetId(currentChar.id);
        return;
      }
      handleDrawPose(current);
      setActiveStep(current);
      current++;
    }, 45);
  };

  const handleManualPack = () => {
    if (!currentChar) return;
    CharacterRepository.packCharacterSheet(currentChar.id);
    if (currentChar.role === 'hero') {
      CharacterRepository.setSelectedHeroSheetId(currentChar.id);
    }
  };

  const handleDelete = (id: string) => {
    CharacterRepository.deleteCharacter(id);
    const remaining = characters.filter(c => c.id !== id);
    if (remaining.length > 0) {
      setSelectedCharId(remaining[0].id);
    }
  };

  return (
    <div className="flex flex-col h-full bg-[#14110E] text-[#F4EBDC] p-4 sm:p-6 lg:p-8 max-w-6xl mx-auto overflow-y-auto select-none">
      {/* Top Header */}
      <header className="flex items-center justify-between border-b border-[#F4EBDC]/10 pb-4 mb-6">
        <div className="flex items-center gap-3">
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
              Sprite & Pose Forge
            </h1>
            <p className="text-xs text-[#A1907A]">
              40-Step Motion Pipeline • Unified Character Aggregate
            </p>
          </div>
        </div>

        {currentChar && (
          <div className="flex items-center gap-2">
            {currentChar.isPacked ? (
              <span className="flex items-center gap-1 text-xs text-[#00B8A9] font-bold">
                <CheckCircle className="w-4 h-4" /> Ready to Wear
              </span>
            ) : (
              <StratumButton
                variant="accent"
                size="sm"
                icon={<PackageCheck className="w-4 h-4" />}
                onClick={handleManualPack}
                disabled={drawnCount === 0}
              >
                Pack Sheet Now
              </StratumButton>
            )}
          </div>
        )}
      </header>

      {/* Main Studio Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left: Saved Characters & Create New */}
        <div className="flex flex-col gap-4">
          <StratumPanel title="Saved Characters" subtitle="Select character aggregate to inspect">
            <div className="flex flex-col gap-2 max-h-[260px] overflow-y-auto mb-4">
              {characters.map(char => (
                <div
                  key={char.id}
                  onClick={() => setSelectedCharId(char.id)}
                  className={`flex items-center justify-between p-2.5 cut-corner-sm border cursor-pointer transition-all ${
                    char.id === selectedCharId
                      ? 'bg-[#CD7F32]/20 border-[#CD7F32] text-[#F4EBDC]'
                      : 'bg-[#1A1612] border-[#F4EBDC]/10 hover:border-[#F4EBDC]/30 text-[#A1907A]'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="text-sm">{char.role === 'hero' ? '🛡️' : '👹'}</span>
                    <div>
                      <span className="font-bold text-xs block">{char.name}</span>
                      <span className="text-[10px] text-[#A1907A] font-mono">
                        {Object.keys(char.poses).length}/40 poses • {char.isPacked ? 'Packed' : 'Unpacked'}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5">
                    <span
                      className={`px-1.5 py-0.5 text-[9px] font-bold rounded ${
                        char.role === 'enemy' ? 'bg-red-900/40 text-red-300' : 'bg-teal-900/40 text-teal-300'
                      }`}
                    >
                      {char.role}
                    </span>
                    {!char.id.startsWith('hero:dike_ozo') && (
                      <button
                        onClick={e => {
                          e.stopPropagation();
                          handleDelete(char.id);
                        }}
                        className="p-1 hover:text-red-400 text-[#A1907A]"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {/* Create New Character */}
            <div className="pt-3 border-t border-[#F4EBDC]/10 flex flex-col gap-2.5">
              <label className="text-[10px] font-bold uppercase text-[#A1907A]">Create New Character</label>
              <input
                type="text"
                value={charName}
                onChange={e => setCharName(e.target.value)}
                placeholder="Character Name"
                className="bg-[#0D0B09] border border-[#F4EBDC]/20 px-3 py-1.5 text-xs text-[#F4EBDC] cut-corner-sm outline-none focus:border-[#CD7F32]"
              />

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setRole('hero')}
                  className={`flex-1 py-1.5 text-xs font-bold uppercase cut-corner-sm border ${
                    role === 'hero' ? 'bg-[#00B8A9] text-black border-transparent' : 'bg-[#1A1612] text-[#A1907A] border-[#F4EBDC]/20'
                  }`}
                >
                  Hero
                </button>
                <button
                  type="button"
                  onClick={() => setRole('enemy')}
                  className={`flex-1 py-1.5 text-xs font-bold uppercase cut-corner-sm border ${
                    role === 'enemy' ? 'bg-[#C1453B] text-white border-transparent' : 'bg-[#1A1612] text-[#A1907A] border-[#F4EBDC]/20'
                  }`}
                >
                  Enemy
                </button>
              </div>
              <p className="text-[10px] text-[#A1907A] italic">
                {role === 'hero' ? 'Hero characters are selectable on the Home screen.' : 'Enemy characters spawn in combat encounters.'}
              </p>

              <StratumButton variant="primary" size="sm" onClick={handleCreateNew}>
                Forge Character Rig
              </StratumButton>
            </div>
          </StratumPanel>
        </div>

        {/* Center: 40-Step Pose Keyframe Pipeline */}
        <div className="flex flex-col gap-4">
          <StratumPanel
            title="Pose Keyframe Sequence"
            subtitle={`${drawnCount} of 40 keyframes generated`}
            actions={
              <StratumButton
                variant="accent"
                size="sm"
                icon={<Sparkles className="w-3.5 h-3.5" />}
                onClick={handleGenerateAllPoses}
                disabled={isGenerating}
              >
                {isGenerating ? 'Drawing...' : 'Draw All 40 & Auto-Pack'}
              </StratumButton>
            }
          >
            {/* Pose steps grid */}
            <div className="grid grid-cols-5 gap-1.5 max-h-[300px] overflow-y-auto p-1 bg-[#0D0B09] border border-[#F4EBDC]/10 cut-corner-sm mb-4">
              {POSE_SCRIPT_POSES.map((p, idx) => {
                const isDrawn = currentChar?.poses[`${p.state}_${p.step}`] !== undefined;
                const isCurrent = activeStep === idx;
                return (
                  <button
                    key={p.step}
                    onClick={() => {
                      setActiveStep(idx);
                      handleDrawPose(idx);
                    }}
                    className={`h-10 cut-corner-sm flex flex-col items-center justify-center text-[10px] font-mono border transition-all cursor-pointer ${
                      isCurrent
                        ? 'border-[#CD7F32] bg-[#CD7F32]/30 text-white font-bold'
                        : isDrawn
                        ? 'border-[#00B8A9]/50 bg-[#00B8A9]/15 text-[#F4EBDC]'
                        : 'border-[#F4EBDC]/10 bg-[#1A1612] text-[#A1907A] hover:border-[#F4EBDC]/30'
                    }`}
                  >
                    <span>{idx + 1}</span>
                    <span className="text-[8px] truncate max-w-[40px] opacity-70">{p.state}</span>
                  </button>
                );
              })}
            </div>

            {/* Current Step Description */}
            <div className="bg-[#1A1612] p-3 cut-corner-sm border border-[#F4EBDC]/10">
              <div className="flex justify-between items-center mb-1">
                <span className="text-xs font-bold text-[#CD7F32] uppercase">
                  Step {activeStep + 1}: {currentPoseDef.name}
                </span>
                <span className="text-[10px] text-[#A1907A] font-mono">{currentPoseDef.state}</span>
              </div>
              <p className="text-xs text-[#A1907A] leading-relaxed mb-3">
                {currentPoseDef.description}
              </p>
              <StratumButton
                variant="secondary"
                size="sm"
                onClick={() => handleDrawPose(activeStep)}
              >
                Re-Draw Pose #{activeStep + 1}
              </StratumButton>
            </div>
          </StratumPanel>
        </div>

        {/* Right: Live Animation Preview & Baked Sheet */}
        <div className="flex flex-col gap-4">
          <StratumPanel title="Motion Preview" subtitle="Real-time test of rigged animations">
            <div className="flex flex-col items-center justify-center p-4 bg-[#0D0B09] cut-corner-md border border-[#F4EBDC]/10 mb-4">
              <div className="w-32 h-32 flex items-center justify-center relative">
                {currentChar?.sheetImageData ? (
                  <img
                    src={currentChar.sheetImageData}
                    alt="Preview"
                    className="w-full h-full object-contain pixelated scale-125"
                    style={{ imageRendering: 'pixelated' }}
                  />
                ) : (
                  <span className="text-xs text-[#A1907A]">No art generated yet</span>
                )}
              </div>
              <span className="text-xs font-mono text-[#CD7F32] mt-2">
                State: {previewState} ({animFrame})
              </span>
            </div>

            {/* State Switchers */}
            <div className="flex flex-wrap gap-1.5 justify-center mb-4">
              {(['IDLE', 'WALK', 'ATTACK', 'HURT', 'DIE'] as const).map(st => (
                <button
                  key={st}
                  onClick={() => setPreviewState(st)}
                  className={`px-2.5 py-1 text-[10px] font-bold uppercase cut-corner-sm border ${
                    previewState === st ? 'bg-[#CD7F32] text-black border-transparent' : 'bg-[#1A1612] text-[#A1907A] border-[#F4EBDC]/15'
                  }`}
                >
                  {st}
                </button>
              ))}
            </div>

            {/* Wear in session button */}
            {currentChar?.role === 'hero' && currentChar.isPacked && (
              <StratumButton
                variant="primary"
                size="md"
                className="w-full"
                onClick={() => {
                  CharacterRepository.setSelectedHeroSheetId(currentChar.id);
                  onNavigate('HOME');
                }}
              >
                Wear & Return Home
              </StratumButton>
            )}
          </StratumPanel>
        </div>
      </div>
    </div>
  );
};
