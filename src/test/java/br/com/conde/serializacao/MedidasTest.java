package br.com.conde.serializacao;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Os testes das TABELAS que o medidor imprime.
 *
 * <p>Toda frase do README que e um numero esta aqui como afirmacao. Texto nao quebra
 * quando deixa de ser verdade; teste quebra.
 */
class MedidasTest {

    private static final List<Medidas.LinhaVolta> VOLTA = Medidas.aVolta();
    private static final List<Medidas.LinhaTamanho> TAMANHO = Medidas.oTamanho();
    private static final List<Medidas.LinhaNegativo> NEGATIVOS = Medidas.osNegativos();
    private static final List<Medidas.LinhaEvolucao> EVOLUCAO = Medidas.aEvolucao();
    private static final List<Medidas.LinhaReal> REAL = Medidas.oReal();

    // ───────────────────── a ida e volta ─────────────────────

    @Test
    @DisplayName("nenhum dos tres formatos erra um valor, em nenhum dos tres conjuntos")
    void aTabelaDaVolta() {
        assertThat(VOLTA).hasSize(3 * 3);
        assertThat(VOLTA).allSatisfy(l ->
                assertThat(l.erros()).as("%s em %s", l.formato(), l.conjunto()).isZero());

        // o fixo gasta oito bytes por valor em todo conjunto, por definicao
        for (Medidas.LinhaVolta l : VOLTA)
            if (l.formato().equals("fixo")) assertThat(l.bytesPorValor()).as(l.conjunto())
                    .isEqualTo(8.0);
    }

    /**
     * O ACHADO DO TAMANHO MEDIO: num sorteio de longs inteiros, o varinteiro gasta MAIS
     * do que o fixo.
     *
     * <p>Um long sorteado tem metade de chance de ser negativo e, dos positivos, quase
     * todos passam de 2 elevado a 56. Varinteiro e para numeros PEQUENOS, e numero
     * sorteado de sessenta e quatro bits nao e pequeno.
     */
    @Test
    @DisplayName("em longs sorteados o varinteiro gasta 9,5 bytes e o fixo gasta 8")
    void oVarinteiroPerdeNoSorteio() {
        assertThat(acharVolta("varinteiro", "sorteados").bytesPorValor()).isGreaterThan(9.0);
        assertThat(acharVolta("zigue-zague", "sorteados").bytesPorValor()).isGreaterThan(9.0);
        assertThat(acharVolta("fixo", "sorteados").bytesPorValor()).isEqualTo(8.0);

        // e em numeros PEQUENOS ele ganha de quatro vezes
        assertThat(acharVolta("varinteiro", "pequenos").bytesPorValor()).isLessThan(2.0);
        assertThat(acharVolta("fixo", "pequenos").bytesPorValor()
                / acharVolta("varinteiro", "pequenos").bytesPorValor()).isGreaterThan(4.0);
    }

    // ───────────────────── o tamanho ─────────────────────

    /**
     * A COLUNA DO MELHOR muda tres vezes conforme a faixa cresce, e e isso que torna a
     * escolha de formato uma escolha sobre OS DADOS.
     */
    @Test
    @DisplayName("o melhor formato muda de varinteiro para fixo para zigue-zague conforme a faixa")
    void aTabelaDoTamanho() {
        assertThat(TAMANHO).hasSize(10);
        assertThat(TAMANHO).allSatisfy(l -> assertThat(l.fixo()).isEqualTo(8));

        assertThat(acharTamanho("zero").melhor()).isEqualTo("varinteiro");
        assertThat(acharTamanho("ate 127").melhor()).isEqualTo("varinteiro");
        assertThat(acharTamanho("o maior long").melhor()).isEqualTo("fixo");
        assertThat(acharTamanho("menos um").melhor()).isEqualTo("zigue-zague");
        assertThat(acharTamanho("o menor long").melhor()).isEqualTo("fixo");

        // e os tres formatos aparecem na coluna
        assertThat(TAMANHO).extracting(Medidas.LinhaTamanho::melhor)
                .contains("varinteiro", "fixo", "zigue-zague");
    }

    @Test
    @DisplayName("o varinteiro cresce de um em um byte a cada sete bits")
    void oVarinteiroCresceACadaSeteBits() {
        assertThat(acharTamanho("ate 127").varinteiro()).isEqualTo(1);
        assertThat(acharTamanho("ate 16 mil").varinteiro()).isEqualTo(2);
        assertThat(acharTamanho("ate 2 milhoes").varinteiro()).isEqualTo(3);
        assertThat(acharTamanho("ate 268 milhoes").varinteiro()).isEqualTo(4);
        assertThat(acharTamanho("bilhoes").varinteiro()).isEqualTo(5);
    }

    // ───────────────────── os negativos ─────────────────────

    /**
     * O NUMERO NEGATIVO QUE CUSTA DEZ BYTES: mais do que os oito do tamanho fixo, que nao
     * economiza nada.
     */
    @Test
    @DisplayName("todo negativo custa dez bytes no varinteiro, e dez e mais do que oito")
    void aTabelaDosNegativos() {
        assertThat(NEGATIVOS).hasSize(10);

        for (Medidas.LinhaNegativo l : NEGATIVOS)
            if (l.valor() < 0) {
                assertThat(l.varinteiro()).as("%d", l.valor()).isEqualTo(10);
                assertThat(l.varinteiro()).as("%d contra o fixo", l.valor()).isGreaterThan(8);
            }

        // e o zigue-zague cobra pelo MODULO e nao pelo sinal
        assertThat(acharNegativo(-1).zigueZague()).isEqualTo(1);
        assertThat(acharNegativo(-1_000).zigueZague()).isEqualTo(2);
        assertThat(acharNegativo(1_000).zigueZague()).isEqualTo(acharNegativo(-1_000).zigueZague());
    }

    @Test
    @DisplayName("os bytes de menos um sao os escritos a mao, nos dois formatos")
    void osBytesDeMenosUm() {
        Medidas.LinhaNegativo menosUm = acharNegativo(-1);

        assertThat(menosUm.bytesDoVarinteiro()).isEqualTo("ffffffffffffffffff01");
        assertThat(menosUm.bytesDoZigueZague()).isEqualTo("01");
    }

    // ───────────────────── a evolucao ─────────────────────

    /**
     * O ACHADO QUE ME CORRIGIU: com o TIPO NO FIO, as cinco mudancas passam, inclusive a
     * que eu tinha escrito como "a perigosa".
     */
    @Test
    @DisplayName("com o tipo no fio, as cinco mudancas de esquema passam nas duas direcoes")
    void comTipoNoFioTudoPassa() {
        for (Medidas.LinhaEvolucao l : EVOLUCAO) {
            if (!l.comTipoNoFio()) continue;

            assertThat(l.leitorNovoLeFioVelho()).as(l.mudanca()).isTrue();
            assertThat(l.leitorVelhoLeFioNovo()).as(l.mudanca()).isTrue();
            assertThat(l.valoresIntactos()).as(l.mudanca()).isTrue();
        }

        assertThat(EVOLUCAO).hasSize(5 * 2);
    }

    /**
     * E SEM O TIPO NO FIO, duas quebram e duas CORROMPEM em silencio.
     *
     * <p>Um byte por campo compra a evolucao de esquema inteira.
     */
    @Test
    @DisplayName("sem o tipo no fio, remover um campo quebra e trocar o tipo corrompe em silencio")
    void semTipoNoFioAMetadeQuebra() {
        assertThat(acharEvolucao("remover um campo", false).leitorNovoLeFioVelho()).isFalse();

        for (String mudanca : new String[] {"trocar o tipo de um campo", "trocar o tipo para fixo"}) {
            Medidas.LinhaEvolucao sem = acharEvolucao(mudanca, false);

            assertThat(sem.leitorNovoLeFioVelho()).as("%s: nao da erro", mudanca).isTrue();
            assertThat(sem.valoresIntactos()).as("%s: e corrompe", mudanca).isFalse();
            assertThat(sem.oQueAconteceu()).contains("sem ninguem avisar");
        }

        // e acrescentar e renomear continuam seguros sem o tipo
        assertThat(acharEvolucao("renomear um campo", false).valoresIntactos()).isTrue();
        assertThat(acharEvolucao("acrescentar um campo", false).valoresIntactos()).isTrue();

        // metade das linhas sem tipo esta quebrada ou corrompida
        long ruins = EVOLUCAO.stream()
                .filter(l -> !l.comTipoNoFio() && !l.valoresIntactos())
                .count();

        assertThat(ruins).isEqualTo(3);
    }

    // ───────────────────── o tamanho de verdade ─────────────────────

    /**
     * NUM CONJUNTO DE PEDIDOS DE VERDADE, o varinteiro economiza 22% e escolher o tipo
     * campo a campo economiza 28%.
     */
    @Test
    @DisplayName("em pedidos de verdade, escolher o tipo campo a campo economiza 28% sobre o fixo")
    void aTabelaDoReal() {
        assertThat(REAL).hasSize(3);

        Medidas.LinhaReal fixo = acharReal("tudo fixo");
        Medidas.LinhaReal varinteiro = acharReal("tudo varinteiro");
        Medidas.LinhaReal escolhido = acharReal("pedido");

        assertThat(fixo.sobreOFixo()).isEqualTo(1.0);
        assertThat(varinteiro.sobreOFixo()).isCloseTo(0.78, within(0.03));
        assertThat(escolhido.sobreOFixo()).isCloseTo(0.72, within(0.03));

        assertThat(escolhido.bytes()).isLessThan(varinteiro.bytes());
        assertThat(varinteiro.bytes()).isLessThan(fixo.bytes());

        assertThat(fixo.registros()).isEqualTo(Medidas.REGISTROS);
    }

    /**
     * E A ECONOMIA NAO VEM DE GRACA NEM E GRANDE: 28% com os numeros DESTE conjunto.
     *
     * <p>Num conjunto com identificadores acima de dois elevado a cinquenta e seis, ela
     * seria negativa.
     */
    @Test
    @DisplayName("a economia e de 15 bytes por registro, e nao de uma ordem de grandeza")
    void aEconomiaEModesta() {
        Medidas.LinhaReal fixo = acharReal("tudo fixo");
        Medidas.LinhaReal escolhido = acharReal("pedido");

        assertThat(fixo.bytesPorRegistro() - escolhido.bytesPorRegistro())
                .isCloseTo(15.5, within(2.0));

        assertThat(fixo.bytesPorRegistro() / escolhido.bytesPorRegistro()).isLessThan(1.5);
    }

    // ───────────────────── achadores ─────────────────────

    private static Medidas.LinhaVolta acharVolta(String formato, String conjunto) {
        return VOLTA.stream()
                .filter(l -> l.formato().equals(formato) && l.conjunto().equals(conjunto))
                .findFirst()
                .orElseThrow();
    }

    private static Medidas.LinhaTamanho acharTamanho(String faixa) {
        return TAMANHO.stream().filter(l -> l.faixa().equals(faixa)).findFirst().orElseThrow();
    }

    private static Medidas.LinhaNegativo acharNegativo(long valor) {
        return NEGATIVOS.stream().filter(l -> l.valor() == valor).findFirst().orElseThrow();
    }

    private static Medidas.LinhaEvolucao acharEvolucao(String mudanca, boolean comTipo) {
        return EVOLUCAO.stream()
                .filter(l -> l.mudanca().equals(mudanca) && l.comTipoNoFio() == comTipo)
                .findFirst()
                .orElseThrow();
    }

    private static Medidas.LinhaReal acharReal(String formato) {
        return REAL.stream()
                .filter(l -> l.formatoDosNumeros().equals(formato))
                .findFirst()
                .orElseThrow();
    }
}
