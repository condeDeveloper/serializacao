package br.com.conde.serializacao;

import java.util.ArrayList;
import java.util.List;

import br.com.conde.serializacao.Esquema.Campo;
import br.com.conde.serializacao.Esquema.Tipo;

/**
 * As MEDIDAS, em BYTES, nunca em segundos.
 *
 * <p>Byte e a medida certa aqui e nao so a conveniente: byte e o que vai pela rede e o que
 * fica no disco, e a escolha de formato e sobre isso.
 */
public final class Medidas {

    private Medidas() {
    }

    /** O esquema de um pedido, que e o registro de verdade das tabelas. */
    public static Esquema pedido() {
        return new Esquema("pedido", List.of(
                new Campo(1, "numero", Tipo.VARINTEIRO),
                new Campo(2, "cliente", Tipo.VARINTEIRO),
                new Campo(3, "centavos", Tipo.ZIGUE_ZAGUE),
                new Campo(4, "carimbo", Tipo.VARINTEIRO),
                new Campo(5, "descricao", Tipo.CADEIA)));
    }

    // ───────────────────── 1. a ida e volta ─────────────────────

    public record LinhaVolta(String formato, String conjunto, long valores, long erros,
                             long bytes, double bytesPorValor) {
    }

    /**
     * TODA IDA E VOLTA DEVOLVE O MESMO VALOR, nas fronteiras e no sorteio.
     *
     * <p>As FRONTEIRAS sao o conjunto que importa. Um varinteiro muda de tamanho a cada
     * sete bits, e um formato com erro de um na conta funciona em toda parte menos
     * exatamente ali. Uma amostra sorteada quase nunca cai nessas fronteiras.
     */
    public static List<LinhaVolta> aVolta() {
        List<LinhaVolta> linhas = new ArrayList<>();
        Contador contador = new Contador();

        record Conjunto(String nome, List<Long> valores) {
        }

        List<Conjunto> conjuntos = List.of(
                new Conjunto("fronteiras de bit", Juiz.valoresDeFronteira()),
                new Conjunto("sorteados", Juiz.valoresSorteados(20_000, 1)),
                new Conjunto("pequenos", pequenos(20_000)));

        for (Conjunto conjunto : conjuntos)
            for (String formato : new String[] {"fixo", "varinteiro", "zigue-zague"}) {
                Juiz.Veredito veredito = Juiz.idaEVolta(formato, conjunto.valores(), contador);

                linhas.add(new LinhaVolta(formato, conjunto.nome(), veredito.valores(),
                        veredito.erros(), veredito.bytes(), veredito.bytesPorValor()));
            }

        return linhas;
    }

    private static List<Long> pequenos(int quantos) {
        Juiz.Sorteio sorteio = new Juiz.Sorteio(2);
        List<Long> valores = new ArrayList<>();

        for (int i = 0; i < quantos; i++) valores.add(sorteio.ate(1_000));

        return valores;
    }

    // ───────────────────── 2. o tamanho ─────────────────────

    public record LinhaTamanho(String faixa, long exemplo, int fixo, int varinteiro,
                               int zigueZague, String melhor) {
    }

    /**
     * QUANTOS BYTES cada formato gasta, por faixa de valor.
     *
     * <p>A coluna da direita muda tres vezes conforme a faixa cresce, e e isso que torna a
     * escolha de formato uma escolha sobre OS DADOS e nao sobre o formato.
     */
    public static List<LinhaTamanho> oTamanho() {
        List<LinhaTamanho> linhas = new ArrayList<>();

        record Faixa(String nome, long exemplo) {
        }

        List<Faixa> faixas = List.of(
                new Faixa("zero", 0),
                new Faixa("ate 127", 100),
                new Faixa("ate 16 mil", 10_000),
                new Faixa("ate 2 milhoes", 1_000_000),
                new Faixa("ate 268 milhoes", 200_000_000),
                new Faixa("bilhoes", 5_000_000_000L),
                new Faixa("o maior long", Long.MAX_VALUE),
                new Faixa("menos um", -1),
                new Faixa("menos mil", -1_000),
                new Faixa("o menor long", Long.MIN_VALUE));

        for (Faixa faixa : faixas) {
            int varinteiro = Fio.bytesDoVarinteiro(faixa.exemplo());
            int zigueZague = Fio.bytesDoZigueZague(faixa.exemplo());
            int menor = Math.min(8, Math.min(varinteiro, zigueZague));

            String melhor = menor == varinteiro ? "varinteiro"
                    : menor == zigueZague ? "zigue-zague" : "fixo";

            linhas.add(new LinhaTamanho(faixa.nome(), faixa.exemplo(), 8, varinteiro,
                    zigueZague, melhor));
        }

        return linhas;
    }

    // ───────────────────── 3. os negativos ─────────────────────

    public record LinhaNegativo(long valor, int varinteiro, int zigueZague,
                                String bytesDoVarinteiro, String bytesDoZigueZague) {
    }

    /**
     * O NUMERO NEGATIVO QUE CUSTA DEZ BYTES.
     *
     * <p>Em complemento de dois, menos um e sessenta e quatro bits ligados. O varinteiro
     * cru escreve sete bits por byte e tem de escrever todos os sessenta e quatro, e
     * sessenta e quatro dividido por sete da nove e sobra um: dez bytes para guardar menos
     * um.
     *
     * <p>Oito bytes de tamanho fixo guardam QUALQUER long. O varinteiro, que existe para
     * economizar, gasta DOIS A MAIS do que gastaria sem economizar nada.
     */
    public static List<LinhaNegativo> osNegativos() {
        List<LinhaNegativo> linhas = new ArrayList<>();
        Contador contador = new Contador();

        for (long valor : new long[] {0, 1, -1, 2, -2, 127, -127, 1_000, -1_000, Long.MIN_VALUE}) {
            Fio comVarinteiro = new Fio(contador);
            Fio comZigueZague = new Fio(contador);

            comVarinteiro.poeVarinteiro(valor);
            comZigueZague.poeZigueZague(valor);

            linhas.add(new LinhaNegativo(valor,
                    Fio.bytesDoVarinteiro(valor), Fio.bytesDoZigueZague(valor),
                    Juiz.emHexadecimal(comVarinteiro.paraVetor()),
                    Juiz.emHexadecimal(comZigueZague.paraVetor())));
        }

        return linhas;
    }

    // ───────────────────── 4. a evolucao ─────────────────────

    public record LinhaEvolucao(String mudanca, boolean comTipoNoFio,
                                boolean leitorNovoLeFioVelho, boolean leitorVelhoLeFioNovo,
                                boolean valoresIntactos, int naoEntendidos,
                                String oQueAconteceu) {
    }

    /**
     * O QUE SOBREVIVE A UMA MUDANCA DE ESQUEMA, com e sem o TIPO no fio.
     *
     * <p>As duas metades da tabela sao o achado. COM o tipo no fio, as cinco mudancas
     * passam: o leitor sabe onde cada campo acaba, entao pula o que nao conhece e le pelo
     * tipo que veio e nao pelo que esperava.
     *
     * <p>SEM o tipo, que e um byte a menos por campo, duas delas quebram e uma CORROMPE em
     * silencio. Um byte por campo compra a evolucao de esquema inteira.
     *
     * <p>A tabela mede as duas direcoes, porque elas nao sao a mesma: um leitor novo lendo
     * um fio velho e um leitor velho lendo um fio novo sao problemas diferentes, e o
     * segundo acontece em toda implantacao gradual.
     */
    public static List<LinhaEvolucao> aEvolucao() {
        List<LinhaEvolucao> linhas = new ArrayList<>();
        Contador contador = new Contador();

        Esquema velho = pedido();
        Registro registro = new Registro()
                .poe(1, 42L)
                .poe(2, 7_000L)
                .poe(3, -2_500L)
                .poe(4, 1_760_000_000L)
                .poe(5, "um pedido");

        record Mudanca(String nome, Esquema novo) {
        }

        List<Mudanca> mudancas = List.of(
                new Mudanca("acrescentar um campo",
                        velho.com(new Campo(6, "desconto", Tipo.ZIGUE_ZAGUE))),
                new Mudanca("remover um campo", velho.sem(5)),
                new Mudanca("renomear um campo", velho.comNomeTrocado(2, "comprador")),
                new Mudanca("trocar o tipo de um campo", velho.comTipoTrocado(3, Tipo.VARINTEIRO)),
                new Mudanca("trocar o tipo para fixo", velho.comTipoTrocado(3, Tipo.FIXO)));

        for (boolean comTipo : new boolean[] {true, false})
            for (Mudanca mudanca : mudancas)
                linhas.add(conferir(mudanca.nome(), comTipo, velho, mudanca.novo(), registro, contador));

        return linhas;
    }

    private static LinhaEvolucao conferir(String mudanca, boolean comTipo, Esquema velho,
                                          Esquema novo, Registro registro, Contador contador) {

        byte[] fioVelho = comTipo ? registro.escrever(velho, contador)
                : registro.escreverSemTipo(velho, contador);

        byte[] fioNovo = comTipo ? registro.escrever(novo, contador)
                : registro.escreverSemTipo(novo, contador);

        boolean novoLeVelho = true;
        boolean velhoLeNovo = true;
        int naoEntendidos = 0;

        Registro peloNovo = null;
        Registro peloVelho = null;

        try {
            peloNovo = comTipo ? Registro.ler(fioVelho, novo, contador)
                    : Registro.lerSemTipo(fioVelho, novo, contador);
        } catch (RuntimeException erro) {
            novoLeVelho = false;
        }

        try {
            peloVelho = comTipo ? Registro.ler(fioNovo, velho, contador)
                    : Registro.lerSemTipo(fioNovo, velho, contador);

            naoEntendidos = peloVelho.naoEntendidos().size();
        } catch (RuntimeException erro) {
            velhoLeNovo = false;
        }

        if (peloNovo == null || peloVelho == null)
            return new LinhaEvolucao(mudanca, comTipo, novoLeVelho, velhoLeNovo, false,
                    naoEntendidos, "a leitura quebrou");

        // os campos que os DOIS esquemas conhecem tem de ter o mesmo valor
        boolean intactos = true;

        for (Campo campo : velho.campos()) {
            if (novo.porEtiqueta(campo.etiqueta()) == null) continue;

            Object original = registro.valor(campo.etiqueta());

            if (original != null && !original.equals(peloNovo.valor(campo.etiqueta()))) {
                intactos = false;
                break;
            }
        }

        String oQue = intactos
                ? naoEntendidos > 0 ? "o leitor velho pulou o campo novo" : "nada se perdeu"
                : "o valor mudou sem ninguem avisar";

        return new LinhaEvolucao(mudanca, comTipo, novoLeVelho, velhoLeNovo, intactos,
                naoEntendidos, oQue);
    }

    // ───────────────────── 5. o tamanho de verdade ─────────────────────

    public record LinhaReal(String formatoDosNumeros, int registros, long bytes,
                            double bytesPorRegistro, double sobreOFixo) {
    }

    /** Quantos registros as tabelas de tamanho usam. */
    public static final int REGISTROS = 50_000;

    /**
     * O TAMANHO num conjunto de registros de VERDADE.
     *
     * <p>Um pedido tem um numero que cresce, um cliente que e um identificador grande, um
     * valor em centavos que pode ser negativo e um carimbo de tempo que passa de um bilhao
     * e meio.
     *
     * <p>A coluna da direita e o que importa: quanto o formato esperto economiza sobre o
     * burro, NESTES dados. Num conjunto com outros numeros, ela e outra.
     */
    public static List<LinhaReal> oReal() {
        List<LinhaReal> linhas = new ArrayList<>();
        List<Registro> registros = registrosDeVerdade(REGISTROS, 3);

        Esquema comFixo = new Esquema("tudo fixo", List.of(
                new Campo(1, "numero", Tipo.FIXO),
                new Campo(2, "cliente", Tipo.FIXO),
                new Campo(3, "centavos", Tipo.FIXO),
                new Campo(4, "carimbo", Tipo.FIXO),
                new Campo(5, "descricao", Tipo.CADEIA)));

        Esquema comVarinteiro = new Esquema("tudo varinteiro", List.of(
                new Campo(1, "numero", Tipo.VARINTEIRO),
                new Campo(2, "cliente", Tipo.VARINTEIRO),
                new Campo(3, "centavos", Tipo.VARINTEIRO),
                new Campo(4, "carimbo", Tipo.VARINTEIRO),
                new Campo(5, "descricao", Tipo.CADEIA)));

        Esquema escolhido = pedido();

        long doFixo = 0;

        for (Esquema esquema : List.of(comFixo, comVarinteiro, escolhido)) {
            Contador contador = new Contador();
            long bytes = 0;

            for (Registro registro : registros) bytes += registro.escrever(esquema, contador).length;

            if (esquema == comFixo) doFixo = bytes;

            linhas.add(new LinhaReal(esquema.nome(), registros.size(), bytes,
                    (double) bytes / registros.size(),
                    doFixo == 0 ? 1 : (double) bytes / doFixo));
        }

        return linhas;
    }

    /** Pedidos de verdade: numero que cresce, cliente grande, centavos com sinal. */
    public static List<Registro> registrosDeVerdade(int quantos, long semente) {
        Juiz.Sorteio sorteio = new Juiz.Sorteio(semente);
        List<Registro> registros = new ArrayList<>();

        for (int i = 0; i < quantos; i++)
            registros.add(new Registro()
                    .poe(1, (long) i)
                    .poe(2, 100_000_000L + sorteio.ate(900_000_000L))
                    .poe(3, sorteio.ate(2) == 0 ? sorteio.ate(500_000) : -sorteio.ate(500_000))
                    .poe(4, 1_760_000_000_000L + sorteio.ate(30L * 24 * 3_600_000))
                    .poe(5, "pedido " + i));

        return registros;
    }
}
