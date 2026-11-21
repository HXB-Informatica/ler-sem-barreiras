# Formato `.lsb` — Ler sem Barreiras

Pacote **aberto**, **versionado** e **offline** para transferir um exercício entre o Studio Windows e a aplicação Android (USB, pasta partilhada, Bluetooth, cartão). Não usa servidores nem contas.

Extensão: `.lsb`  
Contentor: ZIP (deflate)  
Identificador: `lersembarreiras-lsb`  
Versão de esquema actual: **1**  
Codificação de texto: UTF-8, mudanças de linha `LF`

## Estrutura

```
exercicio.lsb
├── manifest.json
├── metadata.json
├── texto.txt
├── sync/palavras.json
└── audio/leitura.wav
```

Não há caminhos absolutos. Só estes nomes relativos, com `/`.

### `manifest.json`

```json
{
  "format": "lersembarreiras-lsb",
  "schemaVersion": 1,
  "contentId": "uuid",
  "createdAtEpochMs": 0,
  "files": {
    "metadata.json": "<sha-256 hex>",
    "texto.txt": "<sha-256 hex>",
    "sync/palavras.json": "<sha-256 hex>",
    "audio/leitura.wav": "<sha-256 hex>"
  }
}
```

O manifesto **não** entra na tabela de checksums. Cada outro ficheiro é obrigatório e a soma SHA-256 tem de coincidir.

### `metadata.json`

Título, `durationMs`, `syncStatus`, datas, `originalContentId`, `source` (`studio` ou `android`) e nomes internos dos ficheiros. **Não** duplica o texto nem a lista de palavras.

### `texto.txt`

Texto canónico do professor, incluindo parágrafos. É a fonte da ordem das palavras.

### `sync/palavras.json`

Lista `WordTiming` já usada na app (id, índice, texto, `startChar`/`endChar`, `startMs`/`endMs`, estado `AUTO|REVIEW|MANUAL|VERIFIED`, origem do tempo). O número e o texto das entradas têm de coincidir com a tokenização de `texto.txt` (separação por espaços em branco, incluindo quebras de linha).

### `audio/leitura.wav`

Narração **PCM** (WAV). É a mesma gravação que o professor exportou; a importação Android copia-a para `contents/<id>/audio.wav` sem recompressão destrutiva.

## Regras de validação

- Ficheiros em falta, checksum errado, WAV que não é PCM, duração com desvio > 250 ms, palavras desalinhadas do texto: rejeição com mensagem em português.
- `schemaVersion` **maior** do que a app: pedir actualização. Versões antigas serão migradas neste leitor (hoje só existe v1).
- Segurança: rejeitar `..`, caminhos absolutos, mais de 16 entradas, mais de 80 MB por ficheiro, mais de 100 MB no total, razões de compressão abusivas.

## Importação

1. Ler o ZIP para memória com os limites acima (nunca extrair para um caminho vindo da entrada).
2. Validar.
3. Se o `contentId` já existir com o mesmo texto e os mesmos tempos: não duplicar.
4. Se o id existir com conteúdo diferente: gerar um UUID novo.
5. Escrever numa pasta temporária; só depois substituir o destino. Uma falha restaura a pasta anterior.

## Exportação

Studio: botão **Exportar .lsb**.  
Reproduzível: a mesma entrada gera o mesmo ZIP (tempos das entradas ZIP fixos na data de criação do conteúdo).

## Transferência

Copiar o `.lsb` por USB, pasta de rede local ou partilha Android. Abrir na app (Modo Professor → Importar .lsb) ou partilhar o ficheiro para o Ler sem Barreiras. Tudo funciona em modo avião.

## Classificação da Fase 6

**PASS WITH ISSUES**

Comprovado em testes JVM/Robolectric: exportação reproduzível, quatro palavras de «Olá um dois três» com estados e tempos, parágrafos, checksum, versão incompatível, path traversal, ZIP abusivo, importação Android que não apaga conteúdos existentes, reabertura após importação.

Não comprovado em tablet físico: reprodução com destaque, modo avião, partilha USB/SAF com um `.lsb` real gerado no Studio desta máquina.
