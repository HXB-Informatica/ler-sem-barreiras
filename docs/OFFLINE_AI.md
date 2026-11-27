# Motor de IA offline

A sincronização no tablet (app React Native, `mobile/`) estima **quando** cada palavra do texto do professor ocorre na gravação. O texto digitado não entra no modelo. O alinhador é que casa as palavras reconhecidas com a frase.

## Modelo

O motor é o **EBranch-w2vBERT2-EP** do projeto CAMÕES: um E-Branchformer treinado de raiz em 425 horas de português europeu, com o frontal w2v-BERT 2.0 congelado e treino conjunto CTC/atenção (peso CTC 0,3). No artigo é a linha *trained from scratch*, EBranch-w2vBERT2, sem modelo de língua de 4-gramas.

- Artigo: Carvalho et al., *CAMÕES: A Comprehensive Automatic Speech Recognition Benchmark for European Portuguese*, IEEE ASRU 2025. [PDF](https://arxiv.org/pdf/2508.19721)
- Ponto de controlo: [inesc-id/EBranch-w2vBERT2-EP](https://huggingface.co/inesc-id/EBranch-w2vBERT2-EP) (CC-BY-4.0)
- Grafo no APK: export ONNX da cabeça CTC, quantização int8, [OpenVoiceOS/inesc-id-ebranch-w2vbert2-ep-ctc-onnx](https://huggingface.co/OpenVoiceOS/inesc-id-ebranch-w2vbert2-ep-ctc-onnx)

O mesmo ponto de controlo tem um segundo export ONNX, com o descodificador de atenção (AED): [OpenVoiceOS/inesc-id-ebranch-w2vbert2-ep-aed-onnx](https://huggingface.co/OpenVoiceOS/inesc-id-ebranch-w2vbert2-ep-aed-onnx). Esse grafo acrescenta o descodificador Transformer e não entra neste APK. O tablet corre a cabeça CTC, com descodificação gananciosa.

O ONNX Runtime para Android 1.22 não implementa `ConvInteger` nem `MatMulInteger`. Na compilação, `rewrite_ctc_graph.py` substitui esses operadores por `DequantizeLinear` seguido de `Conv` ou `MatMul`, e o grafo continua a ler os pesos int8 (`model.int8.onnx.data`).

## O que entra no APK

A tarefa Gradle `fetchEbranchCtc` descarrega o grafo int8, os pesos e o vocabulário para `mobile/android/app/ctc-model/`. `stageEbranchCtcAssets` copia `model.onnx` e `model.int8.onnx.data` para `build/generated/ctcAssets` (não para `src/main/assets`, para o lint do `assembleRelease` não tratar os pesos como saída implícita da árvore de fontes). Os pesos **não** vão para o Git. O vocabulário e a matriz mel do frontal w2v-BERT ficam em `mobile/android/app/src/main/assets/ctc/`.

O APK de distribuição inclui `arm64-v8a` (tablet) e `x86_64` (emulador). `armeabi-v7a` e `x86` foram excluídos: duplicavam bibliotecas nativas sem benefício no HOTWAV TAB R9 Plus.

Na primeira sincronização a app copia o grafo e o ficheiro `.data` para o armazenamento privado, lado a lado, para o ONNX Runtime os abrir. A app instalada não descarrega o modelo.

Runtime: `com.microsoft.onnxruntime:onnxruntime-android:1.22.0` (MIT).

## Áudio, características e tempos

O WAV é lido em PCM mono a 16 kHz. As características seguem o pré-processador w2v-BERT do onnx-asr: janela de Povey de 400 amostras, salto 160, pré-ênfase 0,97, log-mel de Kaldi, normalização por enunciado, e empilhamento de dois quadros em 160 dimensões.

A descodificação CTC é gananciosa: argmax, remover o branco (`<blk>`, id 0) e colapsar repetições. Uma peça que começa por espaço abre uma palavra nova. O tempo de uma peça é o índice do quadro CTC vezes 80 ms (salto de 10 ms vezes o factor de subamostragem 8). O início da palavra é o tempo da primeira peça; o fim é o tempo da peça seguinte.

A saída é minúscula, sem pontuação, com números por extenso e com a grafia **pré-Acordo** (por exemplo `projecto`, não `projeto`). O alinhador já ignora maiúsculas e acentos. Uma diferença de uma letra, como `rou` frente a `roeu`, fica marcada para revisão.

## Alinhamento

`WordAligner` aplica Needleman–Wunsch entre as palavras do texto (grafia original preservada, comparação normalizada) e as palavras reconhecidas. Cobre:

- pontuação e maiúsculas
- acentuação (NFD + remoção de diacríticos)
- palavras repetidas
- omissões do narrador (lacunas curtas interpoladas para revisão; longas ficam sem tempo)
- hesitações extra reconhecidas (uh, hum, … ignoradas se não estiverem no texto)
- dígitos ASR mapeados a um/dois/três
- pequenos erros de ASR (distância de Levenshtein)
- sobreposições residuais recortadas pelo `TimeValidator` (excepto palavras MANUAL/VERIFIED)

Não são inventados timestamps com aparência de precisão de palavra quando o motor não devolve uma palavra. Palavras sem correspondência fiável ficam com `startMs`/`endMs` nulos e `needsReview = true`, excepto lacunas **curtas** entre duas âncoras: essas recebem um intervalo interpolado (`timestampOrigin` `ALIGNED`, estado REVIEW). Pausas longas e pontas da frase não são preenchidas.
