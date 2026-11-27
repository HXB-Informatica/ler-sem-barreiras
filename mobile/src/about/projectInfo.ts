import { NativeModules } from 'react-native';

export type Supporter = {
  id: string;
  name: string;
  supportType: string;
  logoAsset: string | null;
  url: string | null;
  approvedBySchool: boolean;
};

export const PROJECT = {
  name: 'Ler sem Barreiras',
  tagline: 'Textos e narração sincronizados, no próprio tablet, sem internet.',
  mission:
    'Apoiar alunos com dificuldades de leitura e comunicação, e os professores que os acompanham, com leitura guiada palavra a palavra.',
  school: 'Agrupamento de Escolas Gil Vicente',
  schoolShort: 'Escola Gil Vicente',
  schoolRole: 'Entidade promotora e responsável pela iniciativa pedagógica.',
  audience:
    'Alunos com dificuldades de leitura e comunicação, e os professores que preparam os conteúdos no próprio tablet.',
  howItWorks:
    'O professor escreve o texto, grava ou importa a narração e sincroniza as palavras com o áudio. O aluno ouve a voz original e vê a palavra destacada. Tudo corre no dispositivo, sem conta e sem internet.',
  techPartner: 'HXB Informática e Tecnologia, Unipessoal Lda.',
  techRole:
    'Parceiro tecnológico responsável pelo desenvolvimento voluntário e sem custo para a escola. A HXB não reivindica titularidade exclusiva do projeto nem dos conteúdos pedagógicos.',
  copyrightNote:
    'Os direitos de autor sobre o código pertencem aos respetivos autores e colaboradores, nos termos da GNU GPL v3.0. A responsabilidade pedagógica é da escola; o desenvolvimento técnico é da HXB. Não se presume cessão de direitos ainda não formalizada.',
  licenseName: 'GNU GPL v3.0',
  githubUrl: 'https://github.com/HXB-Informatica/ler-sem-barreiras',
  fallbackVersionName: '0.2.3-experimental',
  fallbackVersionCode: 4,
  /**
   * Área configurável. Só mostrar entidades com approvedBySchool === true.
   * A correspondência refere a Sonae, mas a identificação não está aprovada
   * pela escola para publicação — não inventar nomes nem logótipos.
   */
  supporters: [] as Supporter[],
};

export function approvedSupporters(supporters: Supporter[] = PROJECT.supporters): Supporter[] {
  return supporters.filter(item => item.approvedBySchool && item.name.trim().length > 0);
}

export function installedVersion(): { versionName: string; versionCode: number } {
  const files = NativeModules.LsbFiles as { versionName?: string; versionCode?: number } | undefined;
  return {
    versionName: files?.versionName || PROJECT.fallbackVersionName,
    versionCode: files?.versionCode ?? PROJECT.fallbackVersionCode,
  };
}
