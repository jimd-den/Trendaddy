import React, { useState } from 'react';
import { SettingsRepository, AppSettings } from '../../data/repositories/SettingsRepository';
import { StratumButton } from '../components/StratumButton';
import { StratumPanel } from '../components/StratumPanel';
import { ArrowLeft, Check, Cpu } from 'lucide-react';

interface SettingsScreenProps {
  onNavigate: (screen: string) => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({ onNavigate }) => {
  const [settings, setSettings] = useState<AppSettings>(SettingsRepository.getSettings());
  const [saved, setSaved] = useState(false);

  const handleSave = () => {
    SettingsRepository.updateSettings(settings);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
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
            Model Provider & Engine Settings
          </h1>
          <p className="text-xs text-[#A1907A]">
            Configure Gemini AI, OpenRouter endpoints, and audio feedback
          </p>
        </div>
      </header>

      <div className="flex flex-col gap-6">
        <StratumPanel title="AI Model Configuration">
          <div className="flex flex-col gap-4">
            <div>
              <label className="text-xs font-bold text-[#A1907A] uppercase block mb-1">Provider</label>
              <div className="flex gap-2">
                {(['gemini', 'openrouter', 'mock'] as const).map(p => (
                  <button
                    key={p}
                    onClick={() => setSettings({ ...settings, aiProvider: p })}
                    className={`flex-1 py-2 text-xs font-bold uppercase cut-corner-sm border ${
                      settings.aiProvider === p ? 'bg-[#CD7F32] text-black border-transparent' : 'bg-[#14110E] text-[#A1907A] border-[#F4EBDC]/15'
                    }`}
                  >
                    {p}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="text-xs font-bold text-[#A1907A] uppercase block mb-1">Model Name</label>
              <input
                type="text"
                value={settings.selectedModel}
                onChange={e => setSettings({ ...settings, selectedModel: e.target.value })}
                className="w-full bg-[#0D0B09] border border-[#F4EBDC]/20 px-3 py-2 text-sm text-[#F4EBDC] cut-corner-sm outline-none focus:border-[#CD7F32]"
              />
              <p className="text-[10px] text-[#A1907A] mt-1">
                Default: gemini-2.5-flash (handled through server-side secure routes).
              </p>
            </div>
          </div>
        </StratumPanel>

        <StratumPanel title="Audio & Haptics">
          <div className="flex flex-col gap-3">
            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={settings.soundEnabled}
                onChange={e => setSettings({ ...settings, soundEnabled: e.target.checked })}
                className="w-4 h-4 accent-[#CD7F32]"
              />
              <span className="text-xs font-bold text-[#F4EBDC]">Enable Audio Synthesizer & Combat SFX</span>
            </label>

            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={settings.hapticsEnabled}
                onChange={e => setSettings({ ...settings, hapticsEnabled: e.target.checked })}
                className="w-4 h-4 accent-[#CD7F32]"
              />
              <span className="text-xs font-bold text-[#F4EBDC]">Enable Haptic Vibration on Strike/Roll</span>
            </label>
          </div>
        </StratumPanel>

        <div className="flex justify-end">
          <StratumButton
            variant="primary"
            size="md"
            icon={saved ? <Check className="w-4 h-4" /> : <Cpu className="w-4 h-4" />}
            onClick={handleSave}
          >
            {saved ? 'Settings Saved' : 'Save Engine Settings'}
          </StratumButton>
        </div>
      </div>
    </div>
  );
};
