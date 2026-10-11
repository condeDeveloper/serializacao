package br.com.conde.serializacao;

import java.util.ArrayList;
import java.util.List;

/**
 * O JUIZ: a IDA E VOLTA.
 *
 * <p>A pergunta que um formato de fio precisa responder e uma so: decodificar o que foi
 * codificado devolve o mesmo valor? Nao parecido, nao do mesmo tamanho, nao arredondado:
 * o MESMO.
 *
 * <p>Essa pergunta nao precisa de outra implementacao para ser julgada, e e por isso que
 * este repositorio tem o juiz mais barato da serie. O que ele tem de caro e a COBERTURA:
 * um formato que erra so nas fronteiras de sete bits passa em qualquer amostra que nao
 * inclua as fronteiras de sete bits.
 *
 * <p>Por isso ha tambem os BYTES ESCRITOS A MAO. A ida e volta confere que o formato e
 * consistente consigo mesmo; os bytes escritos a mao conferem que ele e o formato que eu
 * disse que era, e nao outro que tambem fecha.
 */
public final class Juiz {

    private Juiz() {
    }

    /** O resultado de uma varredura de ida e volta. */
    public record Veredito(long valores, long erros, String primeiroErro, long bytes) {

        public boolean bateu() {
            return erros == 0;
        }

        public double bytesPorValor() {
            return valores == 0 ? 0 : (double) bytes / valores;
        }
    }

    /**
     * OS VALORES QUE IMPORTAM: as fronteiras, e nao uma amostra.
     *
     * <p>Um varinteiro muda de tamanho a cada sete bits, e um formato com erro de um na
     * conta funciona em toda parte menos exatamente nessas fronteiras. Uma amostra
     * sorteada quase nunca cai nelas.
     *
     * <p>A lista tem, para cada potencia de dois de 0 a 63: o valor, ele menos um, ele
     * mais um, e os negativos correspondentes. Mais os extremos do long e alguns valores
     * a mao.
     */
    public static List<Long> valoresDeFronteira() {
        List<Long> valores = new ArrayList<>();

        for (int bit = 0; bit < 64; bit++) {
            long potencia = 1L << bit;

            valores.add(potencia);
            valores.add(potencia - 1);
            valores.add(potencia + 1);
            valores.add(-potencia);
            valores.add(-potencia - 1);
            valores.add(-potencia + 1);
        }

        valores.add(0L);
        valores.add(1L);
        valores.add(-1L);
        valores.add(Long.MAX_VALUE);
        valores.add(Long.MIN_VALUE);
        valores.add(Long.MIN_VALUE + 1);

        return valores;
    }

    /** Valores sorteados, para cobrir o que nao e fronteira. */
    public static List<Long> valoresSorteados(int quantos, long semente) {
        Sorteio sorteio = new Sorteio(semente);
        List<Long> valores = new ArrayList<>();

        for (int i = 0; i < quantos; i++) valores.add(sorteio.proximoLong());

        return valores;
    }

    /** Ida e volta de todos os valores, por um dos formatos de numero. */
    public static Veredito idaEVolta(String formato, List<Long> valores, Contador contador) {
        long erros = 0;
        String primeiro = null;
        long bytes = 0;

        for (long valor : valores) {
            Fio escrita = new Fio(contador);

            switch (formato) {
                case "fixo" -> escrita.poeFixo64(valor);
                case "varinteiro" -> escrita.poeVarinteiro(valor);
                case "zigue-zague" -> escrita.poeZigueZague(valor);
                default -> throw new IllegalArgumentException("formato desconhecido: " + formato);
            }

            byte[] fio = escrita.paraVetor();

            bytes += fio.length;

            Fio leitura = Fio.paraLer(fio, contador);

            long devolvido = switch (formato) {
                case "fixo" -> leitura.leFixo64();
                case "varinteiro" -> leitura.leVarinteiro();
                default -> leitura.leZigueZague();
            };

            if (devolvido != valor || !leitura.acabou()) {
                erros++;

                if (primeiro == null)
                    primeiro = valor + " voltou como " + devolvido + " em " + fio.length + " bytes";
            }
        }

        return new Veredito(valores.size(), erros, primeiro, bytes);
    }

    /** Ida e volta de um registro inteiro, pelo mesmo esquema dos dois lados. */
    public static boolean idaEVoltaDoRegistro(Registro registro, Esquema esquema, Contador contador) {
        return registro.equals(Registro.ler(registro.escrever(esquema, contador), esquema, contador));
    }

    /** Os bytes em hexadecimal, para a tabela poder mostrar o que saiu. */
    public static String emHexadecimal(byte[] bytes) {
        StringBuilder saida = new StringBuilder();

        for (byte b : bytes) saida.append(String.format("%02x", b));

        return saida.toString();
    }

    /**
     * O CONGRUENTE LINEAR do repositorio, com o descarte dos bits de baixo.
     *
     * <p>O deslocamento de 32 bits tira os bits que alternam par e impar a cada passo.
     * Sem ele, metade dos valores sorteados seria par e metade impar em xadrez, e o bit
     * mais baixo e justamente o que o zigue-zague usa para guardar o sinal.
     */
    public static final class Sorteio {

        private long estado;

        public Sorteio(long semente) {
            this.estado = semente == 0 ? 1 : semente;
        }

        public int proximo() {
            estado = estado * 6364136223846793005L + 1442695040888963407L;

            return (int) (estado >>> 32);
        }

        public long proximoLong() {
            return (long) proximo() << 32 | proximo() & 0xFFFFFFFFL;
        }

        public long ate(long limite) {
            if (limite <= 0) throw new IllegalArgumentException("o limite tem de ser positivo");

            return Integer.toUnsignedLong(proximo()) % limite;
        }
    }
}
