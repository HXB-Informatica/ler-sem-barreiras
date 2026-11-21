# Limitações e problemas pendentes

Actualizado na **v0.2.0**. Não são desculpas: são restrições reais do produto.

## Motivo da v0.2.0

A app Kotlin **não está a funcionar correctamente** para o tablet escolar. Isso motivou a reescrita em React Native ([ADR 0001](adr/0001-migrar-para-react-native.md)). A pasta `mobile/` contém a v0.2.0: UI, alinhador, `.lsb` e whisper.cpp no APK React Native. A precisão no HOTWAV TAB R9 Plus ainda não foi medida.

O restante deste ficheiro descreve a linha 0.1 (Fase 9), que permanece verdadeira para o código Kotlin.

## Não comprovado neste ambiente

- Tablet Android físico (HOTWAV TAB R9 Plus): sincronização, RAM, modo avião, editor, reprodução e importação `.lsb` **não** foram executados em equipamento.
- Gravação USB no Studio com microfone real: só testes automáticos de WAV/captura breve se existir mixer.
- Exercício completo «Olá um dois três. Hoje vamos aprender a ler.» gravado + `.lsb` + Android: **não executado** (falta tablet e sessão de gravação autorizada).
- Precisão temporal abaixo de 200 ms face a anotação humana: **não medida** (não há referência fonética fiável).
- Meta de 95 % de palavras alinhadas em gravações limpas: no laboratório real pt-PT a cobertura passou de 89,9 % para **90,3 %**. Não se presume o alvo.

## Motor

- Whisper `tiny` erra em fala rápida, sobreposta ou ruidosa.
- Interpolação ALIGNED em lacunas curtas é uma ajuda ao professor, não uma prova de que a palavra foi dita.
- Palavras nas pontas sem ASR continuam sem timestamp.
- MFA português não faz parte da aplicação.

## Distribuição

- APK de testes usa a chave de depuração.
- Não há publicação automática de Release nesta fase.
- Gravações de Common Voice **não** vão para o Git.

## Offline

A aplicação instalada não precisa de Internet. A compilação e o laboratório (modelo GGML, parquet de teste) precisam dela na máquina de desenvolvimento.
