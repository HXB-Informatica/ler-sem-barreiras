# Editor visual de sincronização (Modo Professor)

O editor aparece no ecrã **Preparar conteúdo** depois de existir uma lista de palavras (em geral, após «Sincronizar automaticamente»). Destina-se a tablets, com botões grandes e sem necessidade de teclado.

## Cores das palavras

| Estado | Cor | Significado |
| --- | --- | --- |
| AUTO | azul claro | Tempo proposto pelo motor |
| REVIEW | laranja | Sem tempo, ou a rever |
| MANUAL | lilás | Ajuste do professor |
| VERIFIED | verde | Palavra validada |

A palavra que está a ser ouvida fica com fundo amarelo.

## Como corrigir uma frase (exemplo: «Olá um dois três»)

1. Grave ou importe a narração e toque em **Sincronizar automaticamente**.
2. Se só «Olá» e «um» tiverem tempo, «dois» e «três» aparecem a laranja.
3. Toque em **dois**.
4. Reproduza (**Ouvir tudo** ou avance na forma de onda) até ao instante certo e toque em **Início aqui**, depois **Fim aqui**. Também pode arrastar as barras azuis na forma de onda.
5. Use **±50** / **±100** ms para afinar início e fim.
6. **Ouvir palavra** reproduz só esse intervalo (0,5×, 0,75× ou 1×).
7. **Validar palavra** marca VERIFIED. A alteração é **guardada de imediato** neste tablet.
8. Repita para **três**.
9. **Desfazer** / **Refazer** recuperam correções da sessão. Fechar a aplicação mantém os tempos gravados; ao reabrir o conteúdo, as quatro palavras continuam com os intervalos guardados. O áudio original não é alterado.

## Notas

- Tudo corre offline. Nada é enviado para a Internet.
- A forma de onda é um resumo de picos calculado em segundo plano; gravações longas não ficam todas na memória da interface.
- Sobreposições são assinaladas a vermelho, mas o tempo manual não é apagado.

## Verificação da Fase 3

- Testes unitários: `SyncEditorTest` (cenário «Olá um dois três», persistência JSON, undo, repetidas, pausas, sobreposições) e `WaveformExtractorTest` (gravação de 12 s → 200 picos).
- APK: `:app:assembleDebug` concluído com sucesso.
- Sem tablet/emulador ligado: a reprodução Media3 e os gestos na forma de onda não foram ensaiados em hardware.

**Classificação: PASS WITH ISSUES**
