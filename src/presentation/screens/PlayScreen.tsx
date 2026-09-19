import React, { useEffect, useRef, useState, useCallback } from 'react';
import { WorldSession } from '../../engine/WorldSession';
import { ContentPackRepository } from '../../data/repositories/ContentPackRepository';
import { CustomClassRepository } from '../../data/repositories/CustomClassRepository';
import { CharacterRepository } from '../../data/repositories/CharacterRepository';
import { SettingsRepository } from '../../data/repositories/SettingsRepository';
import { DiabloChameleonHUD } from '../components/DiabloChameleonHUD';
import { VirtualJoystick } from '../components/VirtualJoystick';
import { StratumButton } from '../components/StratumButton';
import { StratumPanel } from '../components/StratumPanel';
import { ArrowLeft, Backpack, Hammer, ZoomIn, ZoomOut, RotateCcw, Box } from 'lucide-react';
import { BlockPos } from '../../domain/models/World';

interface PlayScreenProps {
  onNavigate: (screen: string) => void;
}

export const PlayScreen: React.FC<PlayScreenProps> = ({ onNavigate }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const sessionRef = useRef<WorldSession | null>(null);

  const [hudStyle, setHudStyle] = useState(SettingsRepository.getSettings().hudStyle);
  const [showSatchel, setShowSatchel] = useState(false);
  const [showAnvil, setShowAnvil] = useState(false);
  const [isDead, setIsDead] = useState(false);
  const [selectedHotbar, setSelectedHotbar] = useState(0);

  // Engine state mirroring
  const [vitals, setVitals] = useState({
    health: 240,
    maxHealth: 240,
    resource: 100,
    maxResource: 100,
    resourceName: 'Resolve',
    level: 1,
    cowries: 75,
    cooldowns: {} as Record<string, number>,
    dodgeCooldown: 0,
  });

  // Initialize Session
  useEffect(() => {
    const pack = ContentPackRepository.getActivePack();
    const classId = CharacterRepository.getSelectedHeroClassId();
    const heroClass = CustomClassRepository.getClassById(classId) || pack.heroClasses[0];
    const sheetId = CharacterRepository.getSelectedHeroSheetId();
    const character = CharacterRepository.getById(sheetId) || CharacterRepository.getHeroes()[0];
    const weapon = pack.weapons[0];

    const session = new WorldSession(pack, heroClass, character, weapon);
    sessionRef.current = session;

    const unsubSession = session.subscribe(() => {
      setVitals({
        health: session.health,
        maxHealth: session.maxHealth,
        resource: session.resource,
        maxResource: session.maxResource,
        resourceName: session.heroClass.resourceName,
        level: session.level,
        cowries: session.cowries,
        cooldowns: { ...session.skillCooldowns },
        dodgeCooldown: session.dodgeCooldown,
      });
      setIsDead(session.health <= 0);
    });

    const unsubSettings = SettingsRepository.subscribe(() => {
      setHudStyle(SettingsRepository.getSettings().hudStyle);
    });

    return () => {
      unsubSession();
      unsubSettings();
    };
  }, []);

  // Main Render & Animation Loop
  useEffect(() => {
    let animId: number;
    let lastTime = performance.now();

    const render = (time: number) => {
      const dt = Math.min(0.1, (time - lastTime) / 1000);
      lastTime = time;

      const session = sessionRef.current;
      const canvas = canvasRef.current;
      if (session && canvas) {
        session.update(dt);

        const ctx = canvas.getContext('2d');
        if (ctx) {
          drawWorld(ctx, canvas, session);
        }
      }

      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);
    return () => cancelAnimationFrame(animId);
  }, []);

  // Keyboard controls
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const s = sessionRef.current;
      if (!s || s.health <= 0) return;

      const speed = 0.12;
      switch (e.key.toLowerCase()) {
        case 'w':
        case 'arrowup':
          s.playerVelocity.y = -speed;
          s.playerFacing = 'N';
          s.playerState = 'WALK';
          break;
        case 's':
        case 'arrowdown':
          s.playerVelocity.y = speed;
          s.playerFacing = 'S';
          s.playerState = 'WALK';
          break;
        case 'a':
        case 'arrowleft':
          s.playerVelocity.x = -speed;
          s.playerFacing = 'W';
          s.playerState = 'WALK';
          break;
        case 'd':
        case 'arrowright':
          s.playerVelocity.x = speed;
          s.playerFacing = 'E';
          s.playerState = 'WALK';
          break;
        case ' ':
          s.attack();
          break;
        case 'shift':
          s.dodgeRoll();
          break;
        case '1':
          s.castSkill(0);
          break;
        case '2':
          s.castSkill(1);
          break;
        case '3':
          s.castSkill(2);
          break;
        case 'b':
          setShowSatchel(prev => !prev);
          break;
        case 'n':
          setShowAnvil(prev => !prev);
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Resize canvas to container
  useEffect(() => {
    const handleResize = () => {
      const canvas = canvasRef.current;
      if (canvas) {
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
      }
    };
    handleResize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Canvas Drawing
  const drawWorld = (ctx: CanvasRenderingContext2D, canvas: HTMLCanvasElement, s: WorldSession) => {
    const width = canvas.width;
    const height = canvas.height;
    const proj = s.projection;

    ctx.clearRect(0, 0, width, height);

    // Background atmospheric fog
    ctx.fillStyle = s.pack.biomes[0]?.ambientColor || '#14110E';
    ctx.fillRect(0, 0, width, height);

    ctx.save();
    // Center camera on screen
    ctx.translate(width / 2 + s.cameraPos.x, height / 2 + s.cameraPos.y);

    // 1. Render Voxel Grid in depth order (Painter's Algorithm)
    const renderRange = 16;
    const px = Math.floor(s.playerPos.x);
    const py = Math.floor(s.playerPos.y);

    for (let sum = (px - renderRange) + (py - renderRange); sum <= (px + renderRange) + (py + renderRange); sum++) {
      for (let x = Math.max(px - renderRange, sum - (py + renderRange)); x <= Math.min(px + renderRange, sum - (py - renderRange)); x++) {
        const y = sum - x;
        const topZ = s.getSurfaceZ(x, y);

        for (let z = Math.max(0, topZ - 4); z <= topZ; z++) {
          const block = s.getBlock({ x, y, z });
          if (!block) continue;

          const screen = proj.project(x, y, z);
          drawVoxelBlock(ctx, screen.x, screen.y, proj.halfWidth, proj.halfHeight, proj.liftPerLevel, block);

          // Mining crack overlay
          if (s.miningTarget && s.miningTarget.x === x && s.miningTarget.y === y && s.miningTarget.z === z) {
            ctx.fillStyle = `rgba(255, 255, 255, ${s.miningProgress * 0.6})`;
            ctx.beginPath();
            ctx.moveTo(screen.x, screen.y - proj.halfHeight);
            ctx.lineTo(screen.x + proj.halfWidth, screen.y);
            ctx.lineTo(screen.x, screen.y + proj.halfHeight);
            ctx.lineTo(screen.x - proj.halfWidth, screen.y);
            ctx.closePath();
            ctx.fill();
          }
        }
      }
    }

    // 2. Render Ground Loot
    s.groundLoot.forEach(loot => {
      const scr = proj.projectPoint(loot.position);
      ctx.fillStyle = 'rgba(0,0,0,0.3)';
      ctx.beginPath();
      ctx.ellipse(scr.x, scr.y, 8, 4, 0, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = loot.color;
      ctx.font = '14px monospace';
      ctx.textAlign = 'center';
      ctx.fillText(loot.glyph, scr.x, scr.y - 4 + Math.sin(performance.now() * 0.005) * 3);
    });

    // 3. Render Enemies
    s.enemies.forEach(enemy => {
      if (enemy.isDead && enemy.stateTime > 2.0) return;
      const scr = proj.projectPoint(enemy.position);

      // Shadow
      ctx.fillStyle = 'rgba(0,0,0,0.4)';
      ctx.beginPath();
      ctx.ellipse(scr.x, scr.y, 14, 6, 0, 0, Math.PI * 2);
      ctx.fill();

      // Enemy Glyph / Sprite
      ctx.fillStyle = enemy.state === 'HURT' ? '#FFFFFF' : '#FF8E85';
      ctx.font = 'bold 22px monospace';
      ctx.textAlign = 'center';
      ctx.fillText(enemy.glyph, scr.x, scr.y - 12);

      // Health bar
      if (!enemy.isDead && enemy.health < enemy.maxHealth) {
        const hpPct = enemy.health / enemy.maxHealth;
        ctx.fillStyle = 'rgba(0,0,0,0.6)';
        ctx.fillRect(scr.x - 16, scr.y - 36, 32, 4);
        ctx.fillStyle = '#C1453B';
        ctx.fillRect(scr.x - 15, scr.y - 35, 30 * hpPct, 2);
      }
    });

    // 4. Render Player
    const playerScr = proj.projectPoint(s.playerPos);

    // Player shadow
    ctx.fillStyle = 'rgba(0,0,0,0.45)';
    ctx.beginPath();
    ctx.ellipse(playerScr.x, playerScr.y, 16, 7, 0, 0, Math.PI * 2);
    ctx.fill();

    // Player Sprite
    const char = s.character;
    const idlePoses = Object.values(char.poses).filter(p => p.state === s.playerState);
    const pose = idlePoses[Math.floor((performance.now() / 140) % (idlePoses.length || 1))];

    if (pose?.imageData) {
      const img = new Image();
      img.src = pose.imageData;
      ctx.drawImage(img, playerScr.x - 32, playerScr.y - 52, 64, 64);
    } else {
      // Fallback warrior representation
      ctx.fillStyle = '#CD7F32';
      ctx.beginPath();
      ctx.arc(playerScr.x, playerScr.y - 28, 12, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#F4EBDC';
      ctx.font = '10px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('HERO', playerScr.x, playerScr.y - 44);
    }

    // Weapon slash arc
    if (s.playerState === 'ATTACK') {
      ctx.strokeStyle = '#CD7F32';
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.arc(playerScr.x + 10, playerScr.y - 20, 26, -Math.PI / 3, Math.PI / 2);
      ctx.stroke();
    }

    // 5. Render Combat Popups (Damage Numbers)
    s.combatPopups.forEach(popup => {
      const scr = proj.projectPoint(popup.position);
      const age = performance.now() - popup.createdAt;
      const progress = age / popup.durationMs;
      const alpha = 1 - progress;
      const riseY = progress * 35;

      ctx.fillStyle = popup.color;
      ctx.globalAlpha = Math.max(0, alpha);
      ctx.font = popup.isCrit ? 'bold 16px sans-serif' : '13px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(popup.text, scr.x, scr.y - 45 - riseY);
      ctx.globalAlpha = 1.0;
    });

    ctx.restore();
  };

  // Helper to draw single 3D Voxel Block
  const drawVoxelBlock = (
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    hw: number,
    hh: number,
    lift: number,
    block: any
  ) => {
    // 1. Top Face
    ctx.fillStyle = block.topColor || '#8E3E2B';
    ctx.beginPath();
    ctx.moveTo(x, y - hh);
    ctx.lineTo(x + hw, y);
    ctx.lineTo(x, y + hh);
    ctx.lineTo(x - hw, y);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.stroke();

    // 2. Right (South-East) Face
    ctx.fillStyle = block.sideColor || '#6B2B1B';
    ctx.beginPath();
    ctx.moveTo(x, y + hh);
    ctx.lineTo(x + hw, y);
    ctx.lineTo(x + hw, y + lift);
    ctx.lineTo(x, y + hh + lift);
    ctx.closePath();
    ctx.fill();

    // 3. Left (South-West) Face (Darker shadow)
    ctx.fillStyle = 'rgba(0, 0, 0, 0.22)';
    ctx.beginPath();
    ctx.moveTo(x, y + hh);
    ctx.lineTo(x - hw, y);
    ctx.lineTo(x - hw, y + lift);
    ctx.lineTo(x, y + hh + lift);
    ctx.closePath();
    ctx.fill();
  };

  // Pointer Canvas Click for Mining / Building
  const handleCanvasClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const s = sessionRef.current;
    const canvas = canvasRef.current;
    if (!s || !canvas || s.health <= 0) return;

    const rect = canvas.getBoundingClientRect();
    const screenX = e.clientX - rect.left - canvas.width / 2 - s.cameraPos.x;
    const screenY = e.clientY - rect.top - canvas.height / 2 - s.cameraPos.y;

    const picked = s.projection.pickColumn(screenX, screenY, pos => s.isSolidAt(pos));

    if (picked) {
      if (e.button === 2 || s.isBuildMode) {
        // Build block on top
        s.placeBlock({ x: picked.x, y: picked.y, z: picked.z + 1 });
      } else {
        // Mine targeted block
        s.mineBlock(picked);
      }
    }
  };

  return (
    <div className="relative w-screen h-screen overflow-hidden bg-[#14110E] select-none touch-none">
      {/* 3D Canvas */}
      <canvas
        ref={canvasRef}
        onClick={handleCanvasClick}
        onContextMenu={e => {
          e.preventDefault();
          handleCanvasClick(e);
        }}
        className="w-full h-full block cursor-crosshair"
      />

      {/* Top Header Bar: Back, Satchel, Anvil, Build Mode */}
      <div className="absolute top-4 left-4 right-4 flex items-center justify-between pointer-events-none">
        <div className="flex items-center gap-2 pointer-events-auto">
          <StratumButton
            variant="secondary"
            size="sm"
            icon={<ArrowLeft className="w-4 h-4" />}
            onClick={() => onNavigate('HOME')}
          >
            Surface
          </StratumButton>

          <StratumButton
            variant="secondary"
            size="sm"
            icon={<Backpack className="w-4 h-4 text-[#CD7F32]" />}
            onClick={() => setShowSatchel(prev => !prev)}
          >
            Satchel [B]
          </StratumButton>

          <StratumButton
            variant="secondary"
            size="sm"
            icon={<Hammer className="w-4 h-4 text-[#00B8A9]" />}
            onClick={() => setShowAnvil(prev => !prev)}
          >
            Anvil [N]
          </StratumButton>
        </div>

        {/* Hotbar Blocks for Building */}
        <div className="hidden sm:flex items-center gap-1.5 bg-[#14110E]/90 border border-[#F4EBDC]/20 p-1.5 cut-corner-sm pointer-events-auto shadow-lg">
          {sessionRef.current?.hotbarBlocks.map((blk, idx) => (
            <button
              key={blk.id}
              onClick={() => {
                if (sessionRef.current) sessionRef.current.selectedHotbarIndex = idx;
                setSelectedHotbar(idx);
              }}
              className={`w-9 h-9 cut-corner-sm flex items-center justify-center font-bold text-xs border transition-all cursor-pointer ${
                selectedHotbar === idx ? 'border-[#CD7F32] bg-[#CD7F32]/20 scale-105' : 'border-transparent hover:border-[#F4EBDC]/30'
              }`}
              style={{ backgroundColor: blk.topColor }}
            >
              <span className="text-white drop-shadow">{idx + 1}</span>
            </button>
          ))}
          <button
            onClick={() => {
              if (sessionRef.current) sessionRef.current.isBuildMode = !sessionRef.current.isBuildMode;
            }}
            className={`px-2 py-1 text-[10px] font-bold uppercase cut-corner-sm border ${
              sessionRef.current?.isBuildMode ? 'bg-[#00B8A9] text-black border-transparent' : 'bg-[#211C16] text-[#A1907A] border-[#F4EBDC]/20'
            }`}
          >
            Place Mode
          </button>
        </div>

        {/* Zoom Controls */}
        <div className="flex items-center gap-1 pointer-events-auto bg-[#14110E]/80 border border-[#F4EBDC]/20 p-1 cut-corner-sm">
          <button
            onClick={() => {
              if (sessionRef.current) sessionRef.current.projection.zoom = Math.min(1.6, sessionRef.current.projection.zoom + 0.15);
            }}
            className="p-1 text-[#A1907A] hover:text-[#F4EBDC]"
          >
            <ZoomIn className="w-4 h-4" />
          </button>
          <button
            onClick={() => {
              if (sessionRef.current) sessionRef.current.projection.zoom = Math.max(0.6, sessionRef.current.projection.zoom - 0.15);
            }}
            className="p-1 text-[#A1907A] hover:text-[#F4EBDC]"
          >
            <ZoomOut className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Touch Virtual Joystick (Bottom Left) */}
      <div className="absolute bottom-6 left-6 pointer-events-auto sm:hidden">
        <VirtualJoystick
          onMove={(dx, dy) => {
            const s = sessionRef.current;
            if (s && s.health > 0) {
              s.playerVelocity.x = dx * 0.12;
              s.playerVelocity.y = dy * 0.12;
              s.playerState = 'WALK';
            }
          }}
          onStop={() => {
            const s = sessionRef.current;
            if (s && s.health > 0) {
              s.playerState = 'IDLE';
            }
          }}
        />
      </div>

      {/* Bottom Diablo Chameleon HUD */}
      <div className="absolute bottom-3 left-0 right-0 pointer-events-none flex justify-center">
        <DiabloChameleonHUD
          style={hudStyle}
          health={vitals.health}
          maxHealth={vitals.maxHealth}
          resource={vitals.resource}
          maxResource={vitals.maxResource}
          resourceName={vitals.resourceName}
          level={vitals.level}
          cowries={vitals.cowries}
          skills={sessionRef.current?.pack.skills || []}
          cooldowns={vitals.cooldowns}
          onCastSkill={idx => sessionRef.current?.castSkill(idx)}
          onAttack={() => sessionRef.current?.attack()}
          onDodge={() => sessionRef.current?.dodgeRoll()}
          dodgeCooldown={vitals.dodgeCooldown}
        />
      </div>

      {/* Satchel (Inventory) Modal */}
      {showSatchel && (
        <div className="absolute inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 z-40">
          <StratumPanel
            title="Satchel & Supplies"
            subtitle={`${sessionRef.current?.inventory.length} slots occupied`}
            actions={
              <StratumButton variant="quiet" size="sm" onClick={() => setShowSatchel(false)}>
                ✕ Close
              </StratumButton>
            }
            className="w-full max-w-lg"
          >
            <div className="grid grid-cols-3 sm:grid-cols-4 gap-2.5 max-h-[60vh] overflow-y-auto">
              {sessionRef.current?.inventory.map(item => (
                <div
                  key={item.id}
                  className="bg-[#14110E] border border-[#F4EBDC]/15 p-2 cut-corner-sm flex flex-col items-center justify-center text-center hover:border-[#CD7F32]/50"
                >
                  <span className="text-2xl mb-1" style={{ color: item.color }}>{item.glyph}</span>
                  <span className="text-[11px] font-bold text-[#F4EBDC] truncate w-full">{item.name}</span>
                  <span className="text-[10px] font-mono text-[#A1907A]">×{item.count}</span>
                </div>
              ))}
            </div>
          </StratumPanel>
        </div>
      )}

      {/* Anvil (Sockets) Modal */}
      {showAnvil && (
        <div className="absolute inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 z-40">
          <StratumPanel
            title="Igbo-Ukwu Bronze Anvil"
            subtitle="Socket ancient rings and runes into weapon sockets"
            actions={
              <StratumButton variant="quiet" size="sm" onClick={() => setShowAnvil(false)}>
                ✕ Close
              </StratumButton>
            }
            className="w-full max-w-lg"
          >
            <div className="p-3 bg-[#14110E] cut-corner-sm border border-[#F4EBDC]/10 mb-4">
              <div className="flex items-center justify-between mb-2">
                <span className="font-bold text-sm text-[#CD7F32]">
                  {sessionRef.current?.equippedWeapon.name}
                </span>
                <span className="text-xs font-mono text-[#A1907A]">
                  +{sessionRef.current?.equippedWeapon.attackPower} Attack Power
                </span>
              </div>

              {/* Sockets */}
              <div className="flex items-center gap-3 mt-3">
                {sessionRef.current?.equippedWeapon.slottedInserts.map((slot, idx) => (
                  <div
                    key={idx}
                    className="w-14 h-14 bg-[#211C16] border-2 border-dashed border-[#CD7F32]/40 rounded-lg flex flex-col items-center justify-center text-center relative"
                  >
                    {slot ? (
                      <>
                        <span className="text-lg text-[#00B8A9]">✦</span>
                        <span className="text-[8px] text-[#F4EBDC] truncate max-w-[48px]">Slotted</span>
                      </>
                    ) : (
                      <span className="text-[10px] text-[#A1907A]">Empty</span>
                    )}
                  </div>
                ))}
              </div>
            </div>

            <p className="text-xs text-[#A1907A] leading-relaxed">
              Defeat spirits and mine veins in the deep strata to harvest rare inserts and star sapphires.
            </p>
          </StratumPanel>
        </div>
      )}

      {/* Death & Revive Overlay */}
      {isDead && (
        <div className="absolute inset-0 bg-black/85 flex flex-col items-center justify-center p-6 z-50 text-center animate-fade-in">
          <h2 className="text-4xl font-black text-[#C1453B] uppercase tracking-widest mb-2 drop-shadow-lg">
            You Have Fallen
          </h2>
          <p className="text-sm text-[#A1907A] font-serif max-w-md mb-6">
            The soil of Ala Igbo reclaims your breath. Return to the surface or rise to continue the descent.
          </p>
          <div className="flex items-center gap-3">
            <StratumButton
              variant="primary"
              size="lg"
              icon={<RotateCcw className="w-5 h-5" />}
              onClick={() => {
                sessionRef.current?.revive();
                setIsDead(false);
              }}
            >
              Rise & Rekindle
            </StratumButton>
            <StratumButton
              variant="secondary"
              size="lg"
              onClick={() => onNavigate('HOME')}
            >
              Surface To Camp
            </StratumButton>
          </div>
        </div>
      )}
    </div>
  );
};
