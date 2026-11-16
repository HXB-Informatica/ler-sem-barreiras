# Testes

## Testes unitários (executados nesta versão)

Cobertura inicial, sem gravar vozes de crianças:

- separação e normalização de palavras (`WordTokenizerTest`)
- correspondência com timestamps, palavras repetidas, pontuação, omissões, hesitações, pausas e falhas totais (`WordAlignerTest`)
- validação de intervalos temporais (`TimeValidatorTest`)
- leitura/escrita WAV e reamostragem (`WavPcmTest`)
- persistência Room em memória e formato JSON (`ContentRepositoryTest`, Robolectric)

Executar:

```bash
./gradlew test
```

## Testes de integração

Preparados no código da aplicação (fluxo real, não simuladores silenciosos):

- importação de áudio via `AudioImporter`
- gravação via `AudioRecorder`
- carregamento do modelo e JNI
- extração de timestamps e alinhamento
- reprodução Media3 com destaque pela posição do leitor
- reabertura de conteúdos a partir da base local

Estes testes exigem dispositivo ou emulador. **Não foram executados automaticamente neste ambiente de desenvolvimento** até haver um equipamento ligado. Não devem ser apresentados como aprovados sem corrida real.

## Testes no HOTWAV TAB R9 Plus

Ainda **não realizados**. Quando o tablet estiver disponível, registar:

| Métrica | Objetivo de engenharia | Resultado |
| --- | --- | --- |
| Palavras a ≤ 250 ms do instante real | ≥ 95 % | por medir |
| Tempo de processamento / duração do áudio | < 3× | por medir |
| RAM durante `tiny` | compatível com 8 GB físicos | por medir |
| Estabilidade (sem crash, cancelamento) | estável | por medir |
| Fluidez da reprodução | sem desvio acumulado | por medir |

Usar apenas áudio sintético ou narrações de adultos com autorização. **Nunca** publicar gravações de alunos.

Procedimento sugerido:

1. Instalar o APK de *debug*.
2. Ativar modo avião.
3. Criar um texto curto em português europeu (por exemplo 20–40 palavras).
4. Gravar com ritmo de leitura escolar.
5. Sincronizar e anotar o tempo, com `adb shell dumpsys meminfo pt.lersembarreiras.app.debug`.
6. Reproduzir nos modos A, B e C.
7. Encerrar a aplicação, reabrir e confirmar que a sincronização não volta a correr.
8. Repetir com um áudio importado.

## CI

O *workflow* `.github/workflows/android.yml` instala NDK/CMake, corre `./gradlew test` e gera o APK de *debug* como artefacto.
