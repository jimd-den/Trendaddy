import { HeroClassDefinition } from '../../domain/models/HeroClass';
import { IGBO_CLASSES } from '../builtin/IgboContentPackData';

type Listener = () => void;

class CustomClassRepositoryImpl {
  private classes: Map<string, HeroClassDefinition> = new Map();
  private listeners: Set<Listener> = new Set();

  constructor() {
    IGBO_CLASSES.forEach(c => this.classes.set(c.id, c));
    this.loadCustomClasses();
  }

  private loadCustomClasses() {
    try {
      const raw = localStorage.getItem('stratum:custom_classes');
      if (raw) {
        const list: HeroClassDefinition[] = JSON.parse(raw);
        list.forEach(c => this.classes.set(c.id, c));
      }
    } catch (e) {
      console.warn('Failed to load custom classes', e);
    }
  }

  private saveCustomClasses() {
    try {
      const customList = Array.from(this.classes.values()).filter(c => !c.id.startsWith('igbo:'));
      localStorage.setItem('stratum:custom_classes', JSON.stringify(customList));
    } catch (e) {
      console.warn('Failed to save custom classes', e);
    }
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify() {
    this.saveCustomClasses();
    this.listeners.forEach(fn => fn());
  }

  getAllClasses(): HeroClassDefinition[] {
    return Array.from(this.classes.values());
  }

  getClassById(id: string): HeroClassDefinition | undefined {
    return this.classes.get(id);
  }

  saveClass(cls: HeroClassDefinition) {
    this.classes.set(cls.id, cls);
    this.notify();
  }

  deleteClass(id: string) {
    if (!id.startsWith('igbo:')) {
      this.classes.delete(id);
      this.notify();
    }
  }
}

export const CustomClassRepository = new CustomClassRepositoryImpl();
