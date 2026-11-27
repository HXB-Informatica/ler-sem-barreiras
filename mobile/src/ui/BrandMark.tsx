import { Image } from 'react-native';

type Props = {
  size?: number;
};

export function BrandMark({ size = 112 }: Props) {
  return (
    <Image
      source={require('../assets/lsb_brand.png')}
      accessibilityRole="image"
      accessibilityLabel="Logótipo do Ler sem Barreiras"
      style={{ width: size, height: size, alignSelf: 'center' }}
      resizeMode="contain"
    />
  );
}
