import React, { useState } from 'react';
import { HomeScreen } from './presentation/screens/HomeScreen';
import { PlayScreen } from './presentation/screens/PlayScreen';
import { PoseForgeScreen } from './presentation/screens/PoseForgeScreen';
import { ClassForgeScreen } from './presentation/screens/ClassForgeScreen';
import { ForgeScreen } from './presentation/screens/ForgeScreen';
import { WeaponForgeScreen } from './presentation/screens/WeaponForgeScreen';
import { CreatorStudioScreen } from './presentation/screens/CreatorStudioScreen';
import { SettingsScreen } from './presentation/screens/SettingsScreen';

export function App() {
  const [currentScreen, setCurrentScreen] = useState<string>('HOME');

  return (
    <main className="w-screen h-screen overflow-hidden bg-[#14110E] text-[#F4EBDC]">
      {currentScreen === 'HOME' && <HomeScreen onNavigate={setCurrentScreen} />}
      {currentScreen === 'PLAY' && <PlayScreen onNavigate={setCurrentScreen} />}
      {currentScreen === 'POSES' && <PoseForgeScreen onNavigate={setCurrentScreen} />}
      {currentScreen === 'CLASSES' && <ClassForgeScreen onNavigate={setCurrentScreen} />}
      {currentScreen === 'FORGE' && <ForgeScreen onNavigate={setCurrentScreen} />}
      {currentScreen === 'WEAPONS' && <WeaponForgeScreen onNavigate={setCurrentScreen} />}
      {currentScreen === 'STUDIO' && <CreatorStudioScreen onNavigate={setCurrentScreen} />}
      {currentScreen === 'SETTINGS' && <SettingsScreen onNavigate={setCurrentScreen} />}
    </main>
  );
}

export default App;
