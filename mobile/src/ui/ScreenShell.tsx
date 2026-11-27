import type { ReactNode } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors } from '../theme';
import { useLayout } from './layout';

type Props = {
  children: ReactNode;
  scroll?: boolean;
  padded?: boolean;
};

export function ScreenShell({ children, scroll = false, padded = true }: Props) {
  const layout = useLayout();
  const inner = (
    <View
      style={[
        styles.inner,
        {
          maxWidth: layout.maxContent,
          paddingHorizontal: padded ? layout.gutter : 0,
          width: '100%',
        },
      ]}>
      {children}
    </View>
  );
  return (
    <SafeAreaView style={styles.safe}>
      {scroll ? (
        <ScrollView
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={styles.scroll}
          contentInsetAdjustmentBehavior="automatic">
          {inner}
        </ScrollView>
      ) : (
        <View style={styles.fill}>{inner}</View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  fill: { flex: 1, alignItems: 'center' },
  scroll: { flexGrow: 1, alignItems: 'center', paddingBottom: 40 },
  inner: { flexGrow: 1, alignSelf: 'center' },
});
