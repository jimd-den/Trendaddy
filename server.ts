import express from 'express';
import path from 'path';
import { createServer as createViteServer } from 'vite';
import { GoogleGenAI } from '@google/genai';

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json({ limit: '15mb' }));

  // Helper for lazy Gemini initialization
  function getGeminiClient(): GoogleGenAI | null {
    const key = process.env.GEMINI_API_KEY;
    if (!key) return null;
    return new GoogleGenAI({ apiKey: key });
  }

  // API Routes
  app.get('/api/health', (req, res) => {
    res.json({
      status: 'ok',
      hasApiKey: !!process.env.GEMINI_API_KEY,
      timestamp: new Date().toISOString()
    });
  });

  // AI Content Pack Generation Endpoint
  app.post('/api/generate-pack', async (req, res) => {
    try {
      const { prompt, theme = 'Igbo Bronze & Mythology' } = req.body;
      const ai = getGeminiClient();

      if (ai) {
        const response = await ai.models.generateContent({
          model: 'gemini-2.5-flash',
          contents: `You are a game designer creating a content pack for Stratum, an isometric voxel ARPG based on African fantasy and Igbo mythology.
The user wants: "${prompt || theme}".
Generate a JSON object with:
{
  "name": "Pack Name",
  "description": "Brief lore description",
  "palette": {
    "surface": "#14110E",
    "surfaceRaised": "#211C16",
    "ink": "#F4EBDC",
    "inkMuted": "#A1907A",
    "accent": "#CD7F32",
    "accentAlt": "#00B8A9",
    "danger": "#C1453B"
  },
  "blocks": [
    {"id": "stone_id", "displayName": "Stone Name", "topColor": "#7A583A", "sideColor": "#5C3E25", "hardness": 3, "glyph": "◆"}
  ],
  "biomes": [
    {"id": "biome_id", "name": "Biome Name", "description": "Atmospheric description", "surfaceBlock": "stone_id", "ambientColor": "#2A1F18"}
  ],
  "heroClasses": [
    {"id": "class_id", "name": "Hero Title", "title": "Sub-title", "description": "Combat style", "baseHealth": 240, "resourceName": "Fury", "baseResource": 100, "attackPower": 16}
  ],
  "enemies": [
    {"id": "enemy_id", "name": "Creature Name", "health": 120, "attackPower": 12, "glyph": "☠"}
  ],
  "lore": [
    {"id": "lore_1", "title": "Sacred Legend", "body": "Short mythical fragment.", "category": "ARTIFACT"}
  ]
}
Return ONLY valid JSON with no markdown formatting.`,
        });

        const text = response.text || '';
        const cleaned = text.replace(/```json/g, '').replace(/```/g, '').trim();
        const parsed = JSON.parse(cleaned);
        return res.json({ success: true, pack: parsed });
      }

      // Fallback procedural pack generator if no API key is provided
      const slug = (prompt || 'forged').toLowerCase().replace(/[^a-z0-9]/g, '_').slice(0, 16);
      const fallbackPack = {
        name: `${prompt ? prompt.charAt(0).toUpperCase() + prompt.slice(1) : 'Odimma'} Realm`,
        description: `Procedurally woven realm forged from bronze casting, sacred laterite, and ${theme}.`,
        palette: {
          surface: '#14110E',
          surfaceRaised: '#211C16',
          ink: '#F4EBDC',
          inkMuted: '#A1907A',
          accent: '#CD7F32',
          accentAlt: '#00B8A9',
          danger: '#C1453B'
        },
        blocks: [
          { id: `${slug}:carved_laterite`, displayName: 'Carved Laterite', topColor: '#8C3D26', sideColor: '#6B2B1B', hardness: 2, glyph: '■' },
          { id: `${slug}:verdigris_ore`, displayName: 'Verdigris Bronze Ore', topColor: '#2D8A75', sideColor: '#1E6354', hardness: 4, glyph: '✦' },
          { id: `${slug}:sacred_iroko`, displayName: 'Sacred Iroko Wood', topColor: '#5C4033', sideColor: '#422C23', hardness: 3, glyph: '▤' }
        ],
        biomes: [
          { id: `${slug}:hallowed_reach`, name: 'Hallowed Forest of Nri', description: 'Ancient groves where bronze offerings catch the canopy light.', surfaceBlock: `${slug}:carved_laterite`, ambientColor: '#2B1E17' },
          { id: `${slug}:copper_caverns`, name: 'Deep Copper Veins', description: 'Subterranean tunnels humming with the resonance of forged bells.', surfaceBlock: `${slug}:verdigris_ore`, ambientColor: '#192C28' }
        ],
        heroClasses: [
          { id: `${slug}:ozo_blacksmith`, name: 'Ozo Metal-Singer', title: 'Keeper of the Crucible', description: 'Wields heavy bronze hammers that shatter armor and awaken dormant earth.', baseHealth: 280, resourceName: 'Heat', baseResource: 100, attackPower: 18 }
        ],
        enemies: [
          { id: `${slug}:bronze_sentinel`, name: 'Bronze Sentinel', health: 150, attackPower: 14, glyph: '⛨' },
          { id: `${slug}:bush_phantom`, name: 'Bush Phantom', health: 90, attackPower: 16, glyph: '♨' }
        ],
        lore: [
          { id: `${slug}:lore_bell`, title: 'The Resonant Bell of Igbo-Ukwu', body: 'Cast in 9th century bronze with intricate spiral filigree, said to ring only when truth is spoken.', category: 'ARTIFACT' }
        ]
      };
      return res.json({ success: true, pack: fallbackPack, isFallback: true });
    } catch (err: any) {
      console.error('Pack generation error:', err);
      res.status(500).json({ error: err.message || 'Generation failed' });
    }
  });

  // AI Sprite Frame Description & Lore
  app.post('/api/generate-lore', async (req, res) => {
    try {
      const { topic = 'Igbo Bronzes' } = req.body;
      const ai = getGeminiClient();
      if (ai) {
        const response = await ai.models.generateContent({
          model: 'gemini-2.5-flash',
          contents: `Write a short, evocative archaeological lore entry (1-2 sentences) about: "${topic}" for an isometric action RPG. Return JSON: {"title": "Title", "body": "Lore text", "category": "ARTIFACT"}`
        });
        const text = response.text || '';
        const cleaned = text.replace(/```json/g, '').replace(/```/g, '').trim();
        return res.json(JSON.parse(cleaned));
      }
      return res.json({
        title: `The Echo of ${topic}`,
        body: 'Carved wax surrendered to boiling bronze a thousand years ago, preserving hands that shaped Ala Igbo.',
        category: 'ARTIFACT'
      });
    } catch (err: any) {
      res.status(500).json({ error: err.message });
    }
  });

  // Vite middleware in development
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Stratum Igbo ARPG Engine running at http://0.0.0.0:${PORT}`);
  });
}

startServer();
