# Problemas encontrados no alinhador actual

Este texto resulta da validação cruzada (alinhador da aplicação vs segundo motor + VAD).
**Não** é uma taxa de precisão absoluta.

## Síntese

- Palavras do texto sem timestamp no motor actual: 104
- Intervalos inválidos (motor actual): 551 (sobreposições: 0)
- Casos de divergência elevada face ao segundo motor: 4
- Falhas técnicas: 0
- Possível incompatibilidade texto/áudio (VAD): 7
- Concordância elevada (não prova correcção): 41 / 200

## Interpretação fundamentada

1. O alinhador recusa inventar tempos quando o reconhecimento não corresponde ao texto (Needleman–Wunsch). As `palavra_ausente` são o comportamento esperado em omissões, mas no produto exigem revisão do professor.
2. Divergências face ao alinhador de energia/MFA concentram-se em pausas e palavras curtas. Nenhuma das duas escalas é fonética de referência, salvo MFA com modelo português validado.
3. O sinal mais frequente neste ensaio é **sobreposição de intervalos**: timestamps de palavras consecutivas partilham o mesmo instante. O editor de revisão continua a ser necessário.
4. Palavras do motor actual sobrepostas a silêncio (VAD) sugerem fronteiras deslocadas para pausas.
5. Falhas técnicas isoladas não abortam a bateria; cada `id` reproduz o caso.

## O que isto não mede

Sem alinhamento manual ou MFA português instalado, **não estimamos precisão real em milissegundos**.
