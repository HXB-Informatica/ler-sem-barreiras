import { approvedSupporters, PROJECT } from '../projectInfo';

describe('Sobre o projeto', () => {
  it('não publica apoiantes sem aprovação da escola', () => {
    expect(PROJECT.supporters).toEqual([]);
    expect(approvedSupporters()).toEqual([]);
  });

  it('ignora rascunhos não aprovados', () => {
    expect(
      approvedSupporters([
        {
          id: 'draft',
          name: 'Entidade por validar',
          supportType: 'financiamento',
          logoAsset: null,
          url: null,
          approvedBySchool: false,
        },
      ]),
    ).toEqual([]);
  });

  it('identifica escola, parceiro tecnológico e repositório', () => {
    expect(PROJECT.school).toContain('Gil Vicente');
    expect(PROJECT.techPartner).toContain('HXB');
    expect(PROJECT.githubUrl).toBe('https://github.com/HXB-Informatica/ler-sem-barreiras');
    expect(PROJECT.licenseName).toContain('GPL');
  });
});
