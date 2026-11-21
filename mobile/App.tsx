import { useMemo, useState } from 'react';
import { StatusBar } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AboutScreen } from './src/screens/AboutScreen';
import { HomeScreen } from './src/screens/HomeScreen';
import { ReaderListScreen } from './src/screens/ReaderListScreen';
import { ReaderScreen } from './src/screens/ReaderScreen';
import { TeacherEditScreen } from './src/screens/TeacherEditScreen';
import { TeacherListScreen } from './src/screens/TeacherListScreen';
import { createNativeFileBackend, nativeModulesPresent } from './src/native/bridge';
import { ContentRepository } from './src/storage/contentRepository';
import { createMemoryBackend } from './src/storage/fileBackend';

type Route =
  | { name: 'home' }
  | { name: 'about' }
  | { name: 'teacher' }
  | { name: 'teacher-edit'; id: string | null }
  | { name: 'reader' }
  | { name: 'reader-view'; id: string };

function App() {
  const repository = useMemo(
    () =>
      new ContentRepository(
        nativeModulesPresent ? createNativeFileBackend() : createMemoryBackend(),
      ),
    [],
  );
  const [route, setRoute] = useState<Route>({ name: 'home' });

  return (
    <SafeAreaProvider>
      <StatusBar barStyle="dark-content" />
      {route.name === 'home' ? (
        <HomeScreen
          onTeacher={() => setRoute({ name: 'teacher' })}
          onStudent={() => setRoute({ name: 'reader' })}
          onAbout={() => setRoute({ name: 'about' })}
        />
      ) : null}
      {route.name === 'about' ? <AboutScreen onBack={() => setRoute({ name: 'home' })} /> : null}
      {route.name === 'teacher' ? (
        <TeacherListScreen
          repository={repository}
          onBack={() => setRoute({ name: 'home' })}
          onCreate={() => setRoute({ name: 'teacher-edit', id: null })}
          onOpen={id => setRoute({ name: 'teacher-edit', id })}
        />
      ) : null}
      {route.name === 'teacher-edit' ? (
        <TeacherEditScreen
          repository={repository}
          contentId={route.id}
          onBack={() => setRoute({ name: 'teacher' })}
        />
      ) : null}
      {route.name === 'reader' ? (
        <ReaderListScreen
          repository={repository}
          onBack={() => setRoute({ name: 'home' })}
          onOpen={id => setRoute({ name: 'reader-view', id })}
        />
      ) : null}
      {route.name === 'reader-view' ? (
        <ReaderScreen
          repository={repository}
          contentId={route.id}
          onBack={() => setRoute({ name: 'reader' })}
        />
      ) : null}
    </SafeAreaProvider>
  );
}

export default App;
