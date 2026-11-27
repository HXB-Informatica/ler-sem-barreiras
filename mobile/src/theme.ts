export const colors = {
  primary: '#0E5CA8',
  primaryDeep: '#0B4F8F',
  teal: '#0FAF9F',
  sky: '#A9D8F7',
  yellow: '#FFC24D',
  purple: '#8B6BD9',
  text: '#1F2937',
  muted: '#4B5563',
  background: '#F7FAFC',
  surface: '#FFFFFF',
  primaryText: '#FFFFFF',
  secondary: '#D9EEF9',
  danger: '#B42318',
  success: '#0B7A70',
  warningBg: '#FFF6E0',
  highlight: '#FFC24D',
  contrastBg: '#000000',
  contrastFg: '#FFFFFF',
  contrastHighlight: '#FFEB3B',
  studentAccent: '#0FAF9F',
  teacherAccent: '#0E5CA8',
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
};

export const radii = {
  sm: 10,
  md: 16,
  lg: 24,
};

export const type = {
  title: { fontSize: 28, fontWeight: '700' as const, color: colors.text },
  heading: { fontSize: 22, fontWeight: '700' as const, color: colors.text },
  body: { fontSize: 17, lineHeight: 26, color: colors.text },
  meta: { fontSize: 15, lineHeight: 22, color: colors.muted },
};
