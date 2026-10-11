package br.com.conde.serializacao;

import java.util.Locale;

/**
 * O MEDIDOR: imprime as tabelas que o README mostra e que os testes conferem.
 *
 * <p>Toda tabela daqui tem um teste do lado que afirma os mesmos numeros. Texto nao
 * quebra quando deixa de ser verdade; teste quebra.
 */
public final class Medidor {

    private Medidor() {
    }

    public static void main(String[] args) {
        String comando = args.length > 0 ? args[0] : "tudo";

        if (comando.equals("volta") || comando.equals("tudo")) volta();
        if (comando.equals("tamanho") || comando.equals("tudo")) tamanho();
        if (comando.equals("negativos") || comando.equals("tudo")) negativos();
        if (comando.equals("evolucao") || comando.equals("tudo")) evolucao();
        if (comando.equals("real") || comando.equals("tudo")) real();
    }

    private static void volta() {
        System.out.println();
        System.out.println("TODA IDA E VOLTA devolve o mesmo valor, nas fronteiras e no sorteio.");
        System.out.println();
        System.out.printf("  %-14s %-20s %12s %10s %14s %18s%n",
                "formato", "conjunto", "valores", "erros", "bytes", "bytes por valor");

        for (var l : Medidas.aVolta())
            System.out.printf(Locale.ROOT, "  %-14s %-20s %12d %10d %14d %18.2f%n",
                    l.formato(), l.conjunto(), l.valores(), l.erros(), l.bytes(), l.bytesPorValor());

        System.out.println();
    }

    private static void tamanho() {
        System.out.println();
        System.out.println("QUANTOS BYTES cada formato gasta, por faixa de valor.");
        System.out.println();
        System.out.printf("  %-20s %24s %8s %12s %14s %-14s%n",
                "faixa", "exemplo", "fixo", "varinteiro", "zigue-zague", "o melhor");

        for (var l : Medidas.oTamanho())
            System.out.printf("  %-20s %24d %8d %12d %14d %-14s%n",
                    l.faixa(), l.exemplo(), l.fixo(), l.varinteiro(), l.zigueZague(), l.melhor());

        System.out.println();
    }

    private static void negativos() {
        System.out.println();
        System.out.println("O NUMERO NEGATIVO QUE CUSTA DEZ BYTES.");
        System.out.println();
        System.out.printf("  %24s %12s %14s %-24s %-24s%n",
                "valor", "varinteiro", "zigue-zague", "bytes do varinteiro", "bytes do zigue-zague");

        for (var l : Medidas.osNegativos())
            System.out.printf("  %24d %12d %14d %-24s %-24s%n",
                    l.valor(), l.varinteiro(), l.zigueZague(),
                    l.bytesDoVarinteiro(), l.bytesDoZigueZague());

        System.out.println();
    }

    private static void evolucao() {
        System.out.println();
        System.out.println("O QUE SOBREVIVE A UMA MUDANCA DE ESQUEMA.");
        System.out.println();
        System.out.printf("  %-28s %16s %26s %26s %20s %-34s%n",
                "mudanca", "tipo no fio", "leitor novo le fio velho", "leitor velho le fio novo",
                "valores intactos", "o que aconteceu");

        for (var l : Medidas.aEvolucao())
            System.out.printf("  %-28s %16s %26s %26s %20s %-34s%n",
                    l.mudanca(), l.comTipoNoFio() ? "sim" : "nao",
                    l.leitorNovoLeFioVelho() ? "sim" : "NAO",
                    l.leitorVelhoLeFioNovo() ? "sim" : "NAO",
                    l.valoresIntactos() ? "sim" : "NAO",
                    l.oQueAconteceu());

        System.out.println();
    }

    private static void real() {
        System.out.println();
        System.out.println("O TAMANHO num conjunto de " + Medidas.REGISTROS + " pedidos de verdade.");
        System.out.println();
        System.out.printf("  %-24s %12s %16s %22s %20s%n",
                "formato dos numeros", "registros", "bytes", "bytes por registro", "sobre o fixo");

        for (var l : Medidas.oReal())
            System.out.printf(Locale.ROOT, "  %-24s %12d %16d %22.2f %20.4f%n",
                    l.formatoDosNumeros(), l.registros(), l.bytes(), l.bytesPorRegistro(),
                    l.sobreOFixo());

        System.out.println();
    }
}
