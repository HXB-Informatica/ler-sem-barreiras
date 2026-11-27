import { Image, StyleSheet, View } from 'react-native';

type Props = {
  size?: number;
};

export function BrandMark({ size = 112 }: Props) {
  return (
    <View
      style={[styles.wrap, { width: size, height: size, borderRadius: size * 0.22 }]}
      accessibilityRole="image"
      accessibilityLabel="Ícone do Ler sem Barreiras">
      <Image source={require('../assets/lsb_brand.png')} style={{ width: size, height: size }} />
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    overflow: 'hidden',
    alignSelf: 'center',
  },
});
