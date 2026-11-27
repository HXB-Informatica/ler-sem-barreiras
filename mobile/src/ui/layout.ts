import { useWindowDimensions } from 'react-native';

export function useLayout() {
  const { width, height, fontScale } = useWindowDimensions();
  const isLandscape = width > height;
  const isTablet = Math.min(width, height) >= 600;
  const maxContent = isTablet ? (isLandscape ? 980 : 720) : width;
  const gutter = isTablet ? 32 : 20;
  const columns = isTablet && isLandscape ? 2 : isTablet && width >= 800 ? 2 : 1;
  return { width, height, fontScale, isLandscape, isTablet, maxContent, gutter, columns };
}
