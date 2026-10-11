package br.com.conde.serializacao;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * O FIO: um vetor de bytes que cresce, e as formas de pôr um numero dentro dele.
 *
 * <p>Sao quatro formas, e elas nao sao melhor e pior. Cada uma e a melhor para uma faixa
 * de valores e a pior para outra, e o repositorio inteiro e medir onde fica a fronteira.
 *
 * <ul>
 *   <li>TAMANHO FIXO: oito bytes, sempre, para qualquer valor. Previsivel e caro.
 *   <li>VARINTEIRO: sete bits por byte, com um bit de continuacao. Barato para numeros
 *       pequenos e CARO para numeros grandes.
 *   <li>ZIGUE-ZAGUE: o varinteiro com os negativos dobrados para perto do zero. Existe
 *       porque, no varinteiro cru, menos um custa dez bytes.
 *   <li>CADEIA: o tamanho na frente, em varinteiro, e os bytes depois.
 * </ul>
 */
public final class Fio {

    private byte[] bytes;
    private int tamanho;
    private int posicaoDeLeitura;
    private final Contador contador;

    public Fio(Contador contador) {
        this(new byte[16], 0, contador);
    }

    private Fio(byte[] bytes, int tamanho, Contador contador) {
        this.bytes = bytes;
        this.tamanho = tamanho;
        this.contador = contador;
    }

    /** Um fio para LER, montado sobre bytes que ja existem. */
    public static Fio paraLer(byte[] bytes, Contador contador) {
        return new Fio(bytes.clone(), bytes.length, contador);
    }

    public int tamanho() {
        return tamanho;
    }

    public byte[] paraVetor() {
        return Arrays.copyOf(bytes, tamanho);
    }

    public boolean acabou() {
        return posicaoDeLeitura >= tamanho;
    }

    public int posicaoDeLeitura() {
        return posicaoDeLeitura;
    }

    // ───────────────────────────── tamanho fixo ─────────────────────────────

    /**
     * OITO BYTES, sempre, do mais significativo para o menos.
     *
     * <p>Nao depende do valor, e e essa a virtude: o tamanho de um registro da para
     * calcular antes de escrever, e um campo da para alterar no lugar sem mexer no resto.
     */
    public void poeFixo64(long valor) {
        for (int i = 56; i >= 0; i -= 8) poeByte((byte) (valor >>> i));

        contador.escreveu(8);
    }

    public long leFixo64() {
        long valor = 0;

        for (int i = 0; i < 8; i++) valor = valor << 8 | leByte() & 0xFFL;

        contador.leu(8);

        return valor;
    }

    // ───────────────────────────── varinteiro ─────────────────────────────

    /**
     * O VARINTEIRO: sete bits de valor por byte, e o oitavo diz se vem mais.
     *
     * <p>Um numero pequeno cabe num byte e um numero grande gasta ate dez. Dez, e nao
     * nove: um long tem sessenta e quatro bits, sessenta e quatro dividido por sete da
     * nove e sobra um, e o bit que sobra precisa de um decimo byte.
     *
     * <p>E e por isso que um numero NEGATIVO custa o maximo. Em complemento de dois, menos
     * um e sessenta e quatro bits ligados, e o varinteiro cru tem de escrever todos.
     */
    public void poeVarinteiro(long valor) {
        int bytesEscritos = 0;

        while ((valor & ~0x7FL) != 0) {
            poeByte((byte) (valor & 0x7F | 0x80));
            valor >>>= 7;
            bytesEscritos++;
        }

        poeByte((byte) valor);
        bytesEscritos++;

        contador.escreveu(bytesEscritos);
    }

    public long leVarinteiro() {
        long valor = 0;
        int deslocamento = 0;
        int bytesLidos = 0;

        while (true) {
            byte b = leByte();

            bytesLidos++;
            valor |= (b & 0x7FL) << deslocamento;

            if ((b & 0x80) == 0) break;

            deslocamento += 7;

            if (deslocamento > 63) throw new IllegalStateException("varinteiro de mais de dez bytes");
        }

        contador.leu(bytesLidos);

        return valor;
    }

    /** Quantos bytes este valor gastaria como varinteiro, sem escrever nada. */
    public static int bytesDoVarinteiro(long valor) {
        int quantos = 1;

        while ((valor & ~0x7FL) != 0) {
            valor >>>= 7;
            quantos++;
        }

        return quantos;
    }

    // ───────────────────────────── zigue-zague ─────────────────────────────

    /**
     * O ZIGUE-ZAGUE: dobra os negativos para perto do zero antes de virar varinteiro.
     *
     * <p>A conta e deslocar para a esquerda e fazer ou-exclusivo com o sinal estendido.
     * Ela mapeia 0 em 0, menos 1 em 1, 1 em 2, menos 2 em 3, e assim por diante: valores
     * de modulo pequeno, de qualquer sinal, viram numeros pequenos.
     *
     * <p>O preco e que todo valor POSITIVO passa a gastar um bit a mais, e um bit a mais a
     * cada sete vira um byte a mais nas fronteiras. Nao e de graca: e a troca certa quando
     * ha negativos e a errada quando nao ha.
     */
    public static long dobrar(long valor) {
        return valor << 1 ^ valor >> 63;
    }

    public static long desdobrar(long codificado) {
        return codificado >>> 1 ^ -(codificado & 1);
    }

    public void poeZigueZague(long valor) {
        poeVarinteiro(dobrar(valor));
    }

    public long leZigueZague() {
        return desdobrar(leVarinteiro());
    }

    public static int bytesDoZigueZague(long valor) {
        return bytesDoVarinteiro(dobrar(valor));
    }

    // ───────────────────────────── cadeia ─────────────────────────────

    /** O tamanho na frente, em varinteiro, e os bytes em UTF-8 depois. */
    public void poeCadeia(String texto) {
        byte[] conteudo = texto.getBytes(StandardCharsets.UTF_8);

        poeVarinteiro(conteudo.length);

        for (byte b : conteudo) poeByte(b);

        contador.escreveu(conteudo.length);
    }

    public String leCadeia() {
        int quantos = (int) leVarinteiro();

        if (quantos < 0 || posicaoDeLeitura + quantos > tamanho)
            throw new IllegalStateException("cadeia de " + quantos + " bytes nao cabe no que sobrou");

        String texto = new String(bytes, posicaoDeLeitura, quantos, StandardCharsets.UTF_8);

        posicaoDeLeitura += quantos;
        contador.leu(quantos);

        return texto;
    }

    // ───────────────────────────── os bytes ─────────────────────────────

    private void poeByte(byte b) {
        if (tamanho == bytes.length) bytes = Arrays.copyOf(bytes, bytes.length * 2);

        bytes[tamanho++] = b;
    }

    private byte leByte() {
        if (posicaoDeLeitura >= tamanho)
            throw new IllegalStateException("o fio acabou na posicao " + posicaoDeLeitura);

        return bytes[posicaoDeLeitura++];
    }

    /** Pula {@code quantos} bytes, para um leitor ignorar um campo que nao conhece. */
    public void pular(int quantos) {
        if (posicaoDeLeitura + quantos > tamanho)
            throw new IllegalStateException("nao ha " + quantos + " bytes para pular");

        posicaoDeLeitura += quantos;
        contador.pulou(quantos);
    }

    /** Pula um varinteiro inteiro, sem precisar saber quantos bytes ele tem. */
    public void pularVarinteiro() {
        int quantos = 0;

        while ((leByte() & 0x80) != 0) quantos++;

        contador.pulou(quantos + 1);
    }
}
