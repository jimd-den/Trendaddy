export type HudStyle = 'stratum' | 'diablo1' | 'diablo2' | 'diablo3' | 'diablo4';

export interface AppSettings {
  hudStyle: HudStyle;
  soundEnabled: boolean;
  hapticsEnabled: boolean;
  aiProvider: 'gemini' | 'openrouter' | 'mock';
  apiKey: string;
  apiEndpoint: string;
  selectedModel: string;
}

type Listener = () => void;

class SettingsRepositoryImpl {
  private settings: AppSettings = {
    hudStyle: 'stratum',
    soundEnabled: true,
    hapticsEnabled: true,
    aiProvider: 'gemini',
    apiKey: '',
    apiEndpoint: '',
    selectedModel: 'gemini-2.5-flash',
  };

  private listeners: Set<Listener> = new Set();

  constructor() {
    this.load();
  }

  private load() {
    try {
      const raw = localStorage.getItem('stratum:settings');
      if (raw) {
        this.settings = { ...this.settings, ...JSON.parse(raw) };
      }
    } catch (e) {
      console.warn('Failed to load settings', e);
    }
  }

  private save() {
    try {
      localStorage.setItem('stratum:settings', JSON.stringify(this.settings));
    } catch (e) {
      console.warn('Failed to save settings', e);
    }
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify() {
    this.save();
    this.listeners.forEach(fn => fn());
  }

  getSettings(): AppSettings {
    return { ...this.settings };
  }

  updateSettings(updates: Partial<AppSettings>) {
    this.settings = { ...this.settings, ...updates };
    this.notify();
  }
}

export const SettingsRepository = new SettingsRepositoryImpl();
