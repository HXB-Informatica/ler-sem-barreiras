export type FileBackend = {
  writeText: (path: string, text: string) => Promise<void>;
  readText: (path: string) => Promise<string | null>;
  writeBase64: (path: string, data: string) => Promise<void>;
  readBase64: (path: string) => Promise<string | null>;
  exists: (path: string) => Promise<boolean>;
  deletePath: (path: string) => Promise<void>;
  list: (path: string) => Promise<string[]>;
};

export function createMemoryBackend(): FileBackend {
  const files = new Map<string, { kind: 'text' | 'bin'; value: string }>();

  const normalize = (path: string) => path.replace(/\\/g, '/').replace(/^\/+/, '');

  return {
    async writeText(path, text) {
      files.set(normalize(path), { kind: 'text', value: text });
    },
    async readText(path) {
      const entry = files.get(normalize(path));
      return entry?.kind === 'text' ? entry.value : entry?.value ?? null;
    },
    async writeBase64(path, data) {
      files.set(normalize(path), { kind: 'bin', value: data });
    },
    async readBase64(path) {
      const entry = files.get(normalize(path));
      return entry ? entry.value : null;
    },
    async exists(path) {
      const prefix = normalize(path);
      if (files.has(prefix)) {
        return true;
      }
      for (const key of files.keys()) {
        if (key.startsWith(`${prefix}/`)) {
          return true;
        }
      }
      return false;
    },
    async deletePath(path) {
      const prefix = normalize(path);
      for (const key of [...files.keys()]) {
        if (key === prefix || key.startsWith(`${prefix}/`)) {
          files.delete(key);
        }
      }
    },
    async list(path) {
      const prefix = normalize(path);
      const names = new Set<string>();
      for (const key of files.keys()) {
        if (prefix.length === 0) {
          names.add(key.split('/')[0]);
        } else if (key.startsWith(`${prefix}/`)) {
          names.add(key.slice(prefix.length + 1).split('/')[0]);
        }
      }
      return [...names];
    },
  };
}
