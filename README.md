# serializacao

Formatos de fio do zero em Java 21: número de tamanho fixo, varinteiro e zigue-zague,
mais um formato de registro com etiquetas e evolução de esquema.

O juiz é a **ida e volta**. Decodificar o que foi codificado tem de devolver o mesmo
valor: não parecido, não do mesmo tamanho, o mesmo.

Essa pergunta não precisa de outra implementação para ser julgada, e é por isso que este é
o juiz mais barato da série. O que ele tem de caro é a **cobertura**: um formato que erra
só nas fronteiras de sete bits passa em qualquer amostra que não inclua as fronteiras de
sete bits.

Por isso há também os **bytes escritos à mão**. A ida e volta confere que o formato é
consistente consigo mesmo; os bytes escritos à mão conferem que ele é o formato que eu
disse que era, e não outro que também fecha.

## Menos um custa dez bytes

```
$ mvn exec:java -Dexec.args="negativos"

                     valor   varinteiro    zigue-zague bytes do varinteiro      bytes do zigue-zague
                         0            1              1 00                       00
                         1            1              1 01                       02
                        -1           10              1 ffffffffffffffffff01     01
                      1000            2              2 e807                     d00f
                     -1000           10              2 98f8ffffffffffffff01     cf0f
```

Em complemento de dois, menos um é 64 bits ligados. O varinteiro escreve sete bits por
byte e tem de escrever todos os 64, e 64 dividido por 7 dá 9 e sobra 1: **dez bytes para
guardar menos um**.

Oito bytes de tamanho fixo guardam qualquer long. O varinteiro, que existe para economizar,
gasta **dois a mais** do que gastaria sem economizar nada.

O zigue-zague dobra os negativos para perto do zero antes de virar varinteiro: 0 vira 0,
−1 vira 1, 1 vira 2, −2 vira 3. O preço é que todo positivo ganha um bit, e um bit a cada
sete vira um byte nas fronteiras.

## Nenhum formato ganha sempre

```
$ mvn exec:java -Dexec.args="tamanho"

  faixa                                 exemplo     fixo   varinteiro    zigue-zague o melhor
  zero                                        0        8            1              1 varinteiro
  ate 127                                   100        8            1              2 varinteiro
  ate 268 milhoes                     200000000        8            4              5 varinteiro
  bilhoes                            5000000000        8            5              5 varinteiro
  o maior long              9223372036854775807        8            9             10 fixo
  menos um                                   -1        8           10              1 zigue-zague
  o menor long             -9223372036854775808        8           10             10 fixo
```

A coluna da direita muda três vezes. A escolha de formato é uma escolha sobre **os dados**,
e não sobre o formato.

## Como rodar

```
mvn test
mvn exec:java -Dexec.args="tudo"
```

Os comandos do medidor são `volta`, `tamanho`, `negativos`, `evolucao`, `real` e `tudo`.

## O que as medidas me corrigiram

**A mudança de esquema que eu tinha escrito como "a perigosa" é inofensiva.**

A tabela da evolução ia mostrar quatro mudanças: acrescentar um campo, remover, renomear e
trocar o tipo. As três primeiras seguras e a quarta corrompendo o valor.

As quatro passaram.

```
$ mvn exec:java -Dexec.args="evolucao"

  mudanca                           tipo no fio   leitor novo le fio velho   leitor velho le fio novo     valores intactos o que aconteceu
  acrescentar um campo                      sim                        sim                        sim                  sim nada se perdeu
  remover um campo                          sim                        sim                        sim                  sim nada se perdeu
  renomear um campo                         sim                        sim                        sim                  sim nada se perdeu
  trocar o tipo de um campo                 sim                        sim                        sim                  sim nada se perdeu
  trocar o tipo para fixo                   sim                        sim                        sim                  sim nada se perdeu
  acrescentar um campo                      nao                        sim                        sim                  sim nada se perdeu
  remover um campo                          nao                        NAO                        sim                  NAO a leitura quebrou
  renomear um campo                         nao                        sim                        sim                  sim nada se perdeu
  trocar o tipo de um campo                 nao                        sim                        sim                  NAO o valor mudou sem ninguem avisar
  trocar o tipo para fixo                   nao                        sim                        sim                  NAO o valor mudou sem ninguem avisar
```

O meu formato escreve **etiqueta, tipo, valor**, e o leitor decodifica pelo tipo que
**chegou** e não pelo que esperava. Trocar o tipo no esquema não corrompe nada: ele lê o
valor do jeito que ele foi escrito.

A tabela ficou com duas metades. A de baixo é o mesmo formato **sem o byte do tipo**, que
é como um formato guiado por esquema funciona de verdade.

Sem o tipo, remover um campo **quebra a leitura inteira**: o leitor não sabe quantos bytes
a etiqueta desconhecida ocupa, então não pode pular, só parar. E trocar o tipo **corrompe
em silêncio**: ele lê os bytes do jeito errado e devolve um número plausível.

Um byte por campo compra a evolução de esquema inteira. Eu ia escrever que a tabela media
quais mudanças são seguras; ela mede o que o byte do tipo compra.

**Num sorteio de longs, o varinteiro gasta mais do que o tamanho fixo.**

```
$ mvn exec:java -Dexec.args="volta"

  formato        conjunto                  valores      erros          bytes    bytes por valor
  fixo           sorteados                   20000          0         160000               8.00
  varinteiro     sorteados                   20000          0         190010               9.50
  zigue-zague    sorteados                   20000          0         189921               9.50
  fixo           pequenos                    20000          0         160000               8.00
  varinteiro     pequenos                    20000          0          37479               1.87
```

Um long sorteado tem metade de chance de ser negativo, e dos positivos quase todos passam
de 2⁵⁶. Varinteiro é para números **pequenos**, e número sorteado de 64 bits não é pequeno.

A primeira versão desta tabela só tinha a linha dos sorteados, porque sortear é o jeito
óbvio de gerar valores de teste. Ela mostrava o varinteiro perdendo em tudo.

**E a economia em dados de verdade é modesta.**

```
$ mvn exec:java -Dexec.args="real"

  formato dos numeros         registros            bytes     bytes por registro         sobre o fixo
  tudo fixo                       50000          2738890                  54.78               1.0000
  tudo varinteiro                 50000          2136872                  42.74               0.7802
  pedido                          50000          1961562                  39.23               0.7162
```

Cinquenta mil pedidos, cada um com um número que cresce, um identificador de cliente de
nove dígitos, um valor em centavos com sinal e um carimbo de tempo em milissegundos.

Escolher o tipo campo a campo economiza **28%**, ou 15,5 bytes por registro. Não é uma
ordem de grandeza: é 1,4 vez.

E ela depende inteiramente de **quais** números estão ali. Num conjunto com identificadores
acima de 2⁵⁶, a mesma escolha custaria bytes em vez de economizar.

## Licença

MIT.
