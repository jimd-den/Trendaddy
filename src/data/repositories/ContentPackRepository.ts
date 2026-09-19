import { ContentPack } from '../../domain/models/ContentPack';
import { BUILTIN_IGBO_PACK } from '../builtin/IgboContentPackData';

type Listener = () => void;

class ContentPackRepositoryImpl {
  private packs: Map<string, ContentPack> = new Map();
  private activePackId: string = 'igbo';
  private listeners: Set<Listener> = new Set();

  constructor() {
    this.packs.set(BUILTIN_IGBO_PACK.id, BUILTIN_IGBO_PACK);
    this.loadCustomPacks();
  }

  private loadCustomPacks() {
    try {
      const raw = localStorage.getItem('stratum:custom_packs');
      if (raw) {
        const list: ContentPack[] = JSON.parse(raw);
        list.forEach(p => this.packs.set(p.id, p));
      }
      const savedActive = localStorage.getItem('stratum:active_pack_id');
      if (savedActive && this.packs.has(savedActive)) {
        this.activePackId = savedActive;
      }
    } catch (e) {
      console.warn('Failed to load custom packs', e);
    }
  }

  private saveCustomPacks() {
    try {
      const customPacks = Array.from(this.packs.values()).filter(p => p.id !== 'igbo');
      localStorage.setItem('stratum:custom_packs', JSON.stringify(customPacks));
      localStorage.setItem('stratum:active_pack_id', this.activePackId);
    } catch (e) {
      console.warn('Failed to save custom packs', e);
    }
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify() {
    this.saveCustomPacks();
    this.listeners.forEach(fn => fn());
  }

  getAllPacks(): ContentPack[] {
    return Array.from(this.packs.values());
  }

  getActivePack(): ContentPack {
    return this.packs.get(this.activePackId) || BUILTIN_IGBO_PACK;
  }

  setActivePack(id: string) {
    if (this.packs.has(id)) {
      this.activePackId = id;
      this.notify();
    }
  }

  savePack(pack: ContentPack) {
    this.packs.set(pack.id, pack);
    this.activePackId = pack.id;
    this.notify();
  }
}

export const ContentPackRepository = new ContentPackRepositoryImpl();
