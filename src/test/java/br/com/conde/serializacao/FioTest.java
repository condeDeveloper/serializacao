package br.com.conde.serializacao;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os testes do FIO: a ida e volta, e os BYTES ESCRITOS A MAO.
 *
 * <p>A ida e volta confere que o formato e consistente consigo mesmo. Os bytes escritos a
 * mao conferem que ele e o formato que eu disse que era, e nao outro que tambem fecha.
 */
class FioTest {

    private final Contador contador = new Contador();

    /**
     * AS FRONTEIRAS DE SETE BITS sao o conjunto que importa.
     *
     * <p>Um varinteiro muda de tamanho a cada sete bits, e um formato com erro de um na
     * conta funciona em toda parte menos exatamente ali. Uma amostra sorteada quase nunca
     * cai nessas fronteiras.
     */
    @Test
    @DisplayName("os tres formatos devolvem o mesmo valor em todas as fronteiras de bit")
    void aIdaEVoltaNasFronteiras() {
        for (String formato : new String[] {"fixo", "varinteiro", "zigue-zague"}) {
            Juiz.Veredito veredito = Juiz.idaEVolta(formato, Juiz.valoresDeFronteira(), contador);

            assertThat(veredito.erros()).as("%s: %s", formato, veredito.primeiroErro()).isZero();
            assertThat(veredito.valores()).isEqualTo(390);
        }
    }

    @Test
    @DisplayName("os tres formatos devolvem o mesmo valor em cem mil sorteados")
    void aIdaEVoltaNoSorteio() {
        List<Long> valores = Juiz.valoresSorteados(100_000, 7);

        for (String formato : new String[] {"fixo", "varinteiro", "zigue-zague"}) {
            Juiz.Veredito veredito = Juiz.idaEVolta(formato, valores, contador);

            assertThat(veredito.erros()).as("%s: %s", formato, veredito.primeiroErro()).isZero();
        }
    }

    /**
     * OS BYTES ESCRITOS A MAO. Um varinteiro de 300 e 0xAC 0x02, e nao outra coisa que
     * tambem volte como 300.
     */
    @Test
    @DisplayName("os bytes do varinteiro sao os bytes escritos a mao")
    void osBytesDoVarinteiro() {
        assertThat(hexDoVarinteiro(0)).isEqualTo("00");
        assertThat(hexDoVarinteiro(1)).isEqualTo("01");
        assertThat(hexDoVarinteiro(127)).isEqualTo("7f");
        assertThat(hexDoVarinteiro(128)).isEqualTo("8001");
        assertThat(hexDoVarinteiro(300)).isEqualTo("ac02");
        assertThat(hexDoVarinteiro(16_383)).isEqualTo("ff7f");
        assertThat(hexDoVarinteiro(16_384)).isEqualTo("808001");
    }

    @Test
    @DisplayName("os bytes do zigue-zague sao os bytes escritos a mao")
    void osBytesDoZigueZague() {
        assertThat(hexDoZigueZague(0)).isEqualTo("00");
        assertThat(hexDoZigueZague(-1)).isEqualTo("01");
        assertThat(hexDoZigueZague(1)).isEqualTo("02");
        assertThat(hexDoZigueZague(-2)).isEqualTo("03");
        assertThat(hexDoZigueZague(2)).isEqualTo("04");
    }

    @Test
    @DisplayName("os oito bytes do fixo saem do mais significativo para o menos")
    void osBytesDoFixo() {
        Fio fio = new Fio(contador);

        fio.poeFixo64(1);

        assertThat(Juiz.emHexadecimal(fio.paraVetor())).isEqualTo("0000000000000001");

        Fio outro = new Fio(contador);

        outro.poeFixo64(-1);

        assertThat(Juiz.emHexadecimal(outro.paraVetor())).isEqualTo("ffffffffffffffff");
    }

    /**
     * O ACHADO: menos um custa DEZ bytes no varinteiro cru e UM no zigue-zague.
     *
     * <p>Em complemento de dois, menos um e sessenta e quatro bits ligados. O varinteiro
     * escreve sete bits por byte e tem de escrever todos.
     */
    @Test
    @DisplayName("menos um custa dez bytes no varinteiro e um no zigue-zague")
    void oMenosUmQueCustaDezBytes() {
        assertThat(Fio.bytesDoVarinteiro(-1)).isEqualTo(10);
        assertThat(Fio.bytesDoZigueZague(-1)).isEqualTo(1);

        // e dez e MAIS do que os oito do tamanho fixo
        assertThat(Fio.bytesDoVarinteiro(-1)).isGreaterThan(8);

        // todo negativo custa dez no varinteiro cru, qualquer que seja o modulo
        for (long valor : new long[] {-1, -2, -127, -1_000, -1_000_000, Long.MIN_VALUE})
            assertThat(Fio.bytesDoVarinteiro(valor)).as("%d", valor).isEqualTo(10);
    }

    /**
     * E O PRECO DO ZIGUE-ZAGUE: todo positivo ganha um bit, e um bit a cada sete vira um
     * byte nas fronteiras.
     */
    @Test
    @DisplayName("o zigue-zague cobra um byte a mais nos positivos de fronteira")
    void oPrecoDoZigueZague() {
        assertThat(Fio.bytesDoVarinteiro(127)).isEqualTo(1);
        assertThat(Fio.bytesDoZigueZague(127)).isEqualTo(2);

        assertThat(Fio.bytesDoVarinteiro(Long.MAX_VALUE)).isEqualTo(9);
        assertThat(Fio.bytesDoZigueZague(Long.MAX_VALUE)).isEqualTo(10);

        // e nos valores dentro da faixa de sete bits ele empata
        assertThat(Fio.bytesDoZigueZague(63)).isEqualTo(1);
    }

    @Test
    @DisplayName("dobrar e desdobrar sao inversas uma da outra em toda fronteira")
    void dobrarEDesdobrar() {
        for (long valor : Juiz.valoresDeFronteira())
            assertThat(Fio.desdobrar(Fio.dobrar(valor))).as("%d", valor).isEqualTo(valor);

        // e a tabela do artigo: 0 vira 0, -1 vira 1, 1 vira 2, -2 vira 3
        assertThat(Fio.dobrar(0)).isZero();
        assertThat(Fio.dobrar(-1)).isEqualTo(1);
        assertThat(Fio.dobrar(1)).isEqualTo(2);
        assertThat(Fio.dobrar(-2)).isEqualTo(3);
    }

    @Test
    @DisplayName("a cadeia volta igual, inclusive vazia e com acento")
    void aCadeiaVolta() {
        for (String texto : new String[] {"", "a", "pedido numero 42", "acentuação e cedilha ç",
                "x".repeat(10_000)}) {

            Fio escrita = new Fio(contador);

            escrita.poeCadeia(texto);

            assertThat(Fio.paraLer(escrita.paraVetor(), contador).leCadeia()).isEqualTo(texto);
        }
    }

    @Test
    @DisplayName("varios valores seguidos voltam na ordem em que entraram")
    void variosValoresSeguidos() {
        Fio escrita = new Fio(contador);

        escrita.poeVarinteiro(300);
        escrita.poeCadeia("meio");
        escrita.poeZigueZague(-7);
        escrita.poeFixo64(Long.MIN_VALUE);

        Fio leitura = Fio.paraLer(escrita.paraVetor(), contador);

        assertThat(leitura.leVarinteiro()).isEqualTo(300);
        assertThat(leitura.leCadeia()).isEqualTo("meio");
        assertThat(leitura.leZigueZague()).isEqualTo(-7);
        assertThat(leitura.leFixo64()).isEqualTo(Long.MIN_VALUE);
        assertThat(leitura.acabou()).isTrue();
    }

    @Test
    @DisplayName("um fio que acabou reclama em vez de devolver lixo")
    void oFioQueAcabou() {
        Fio vazio = Fio.paraLer(new byte[0], contador);

        assertThat(vazio.acabou()).isTrue();
        assertThatThrownBy(vazio::leVarinteiro)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("acabou");

        assertThatThrownBy(() -> Fio.paraLer(new byte[] {5}, contador).leCadeia())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("nao cabe");

        assertThatThrownBy(() -> Fio.paraLer(new byte[] {1, 2}, contador).pular(5))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Um varinteiro que nunca termina tem de reclamar, e nao girar para sempre nem
     * devolver numero inventado.
     */
    @Test
    @DisplayName("um varinteiro com mais de dez bytes reclama")
    void oVarinteiroSemFim() {
        byte[] bytes = new byte[12];

        java.util.Arrays.fill(bytes, (byte) 0x80);

        assertThatThrownBy(() -> Fio.paraLer(bytes, contador).leVarinteiro())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dez bytes");
    }

    @Test
    @DisplayName("pular um varinteiro anda exatamente os bytes dele")
    void pularUmVarinteiro() {
        Fio escrita = new Fio(contador);

        escrita.poeVarinteiro(300_000);
        escrita.poeVarinteiro(7);

        Fio leitura = Fio.paraLer(escrita.paraVetor(), contador);

        leitura.pularVarinteiro();

        assertThat(leitura.leVarinteiro()).isEqualTo(7);
        assertThat(leitura.acabou()).isTrue();
    }

    @Test
    @DisplayName("o contador conta os bytes escritos, lidos e pulados separados")
    void oContador() {
        Contador proprio = new Contador();
        Fio fio = new Fio(proprio);

        fio.poeFixo64(1);

        assertThat(proprio.bytesEscritos()).isEqualTo(8);

        Fio leitura = Fio.paraLer(fio.paraVetor(), proprio);

        leitura.leFixo64();

        assertThat(proprio.bytesLidos()).isEqualTo(8);
        assertThat(proprio.toString()).contains("8 bytes escritos");

        proprio.zerar();

        assertThat(proprio.bytesEscritos()).isZero();
        assertThat(proprio.bytesPulados()).isZero();
    }

    /**
     * O DESLOCAMENTO DE 32 BITS: o bit mais baixo e justamente o que o zigue-zague usa
     * para guardar o sinal, e num congruente linear sem descarte ele alterna a cada passo.
     */
    @Test
    @DisplayName("o sorteio nao alterna par e impar, que e o bit do sinal do zigue-zague")
    void oSorteioNaoAlterna() {
        Juiz.Sorteio sorteio = new Juiz.Sorteio(12345);
        int alternou = 0;
        long anterior = sorteio.proximoLong() & 1;

        for (int i = 0; i < 1_000; i++) {
            long agora = sorteio.proximoLong() & 1;

            if (agora != anterior) alternou++;

            anterior = agora;
        }

        assertThat(alternou).isBetween(420, 580);
    }

    private String hexDoVarinteiro(long valor) {
        Fio fio = new Fio(contador);

        fio.poeVarinteiro(valor);

        return Juiz.emHexadecimal(fio.paraVetor());
    }

    private String hexDoZigueZague(long valor) {
        Fio fio = new Fio(contador);

        fio.poeZigueZague(valor);

        return Juiz.emHexadecimal(fio.paraVetor());
    }
}
