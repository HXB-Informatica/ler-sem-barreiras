const marks = new Map<string, number>();

export function markStart(name: string) {
  marks.set(name, Date.now());
}

export function markEnd(name: string): number | null {
  const started = marks.get(name);
  if (started == null) {
    return null;
  }
  const elapsed = Date.now() - started;
  if (__DEV__) {
    // eslint-disable-next-line no-console
    console.log(`[LsbPerf] ${name} ${elapsed}ms`);
  }
  return elapsed;
}
