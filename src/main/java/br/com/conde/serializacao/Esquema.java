package br.com.conde.serializacao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UM ESQUEMA: a lista de campos de um registro, cada um com uma ETIQUETA e um TIPO.
 *
 * <p>A etiqueta e um numero, e e ela que vai no fio, e nao o nome do campo. Essa escolha
 * decide tudo o que acontece quando o esquema muda: renomear um campo e de graca, porque
 * o nome nunca foi escrito; reaproveitar uma etiqueta e o desastre, porque o leitor velho
 * vai ler o campo novo como se fosse o antigo.
 *
 * <p>Cada campo e escrito como etiqueta, tipo e valor. O TIPO no fio nao e enfeite: e ele
 * que permite a um leitor PULAR um campo que ele nao conhece, e sem isso nao ha evolucao
 * de esquema nenhuma, so quebra.
 */
public final class Esquema {

    /** O que um campo guarda, e como ele vai para o fio. */
    public enum Tipo {

        /** Varinteiro sem sinal: barato para pequenos, dez bytes para negativos. */
        VARINTEIRO,

        /** Varinteiro com os negativos dobrados para perto do zero. */
        ZIGUE_ZAGUE,

        /** Oito bytes sempre, qualquer que seja o valor. */
        FIXO,

        /** Tamanho em varinteiro, depois os bytes. */
        CADEIA
    }

    /** Um campo do esquema. */
    public record Campo(int etiqueta, String nome, Tipo tipo) {

        public Campo {
            if (etiqueta <= 0) throw new IllegalArgumentException("a etiqueta tem de ser positiva");
        }
    }

    private final String nome;
    private final List<Campo> campos;
    private final Map<Integer, Campo> porEtiqueta = new LinkedHashMap<>();

    public Esquema(String nome, List<Campo> campos) {
        this.nome = nome;
        this.campos = List.copyOf(campos);

        for (Campo campo : campos)
            if (porEtiqueta.put(campo.etiqueta(), campo) != null)
                throw new IllegalArgumentException("etiqueta repetida: " + campo.etiqueta());
    }

    public String nome() {
        return nome;
    }

    public List<Campo> campos() {
        return campos;
    }

    public Campo porEtiqueta(int etiqueta) {
        return porEtiqueta.get(etiqueta);
    }

    /** O mesmo esquema sem um dos campos, para medir o que acontece ao remover. */
    public Esquema sem(int etiqueta) {
        List<Campo> sobraram = new ArrayList<>();

        for (Campo campo : campos) if (campo.etiqueta() != etiqueta) sobraram.add(campo);

        return new Esquema(nome + " sem " + etiqueta, sobraram);
    }

    /** O mesmo esquema com um campo a mais. */
    public Esquema com(Campo novo) {
        List<Campo> todos = new ArrayList<>(campos);

        todos.add(novo);

        return new Esquema(nome + " com " + novo.etiqueta(), todos);
    }

    /** O mesmo esquema com o TIPO de um campo trocado, que e a mudanca perigosa. */
    public Esquema comTipoTrocado(int etiqueta, Tipo novoTipo) {
        List<Campo> todos = new ArrayList<>();

        for (Campo campo : campos)
            todos.add(campo.etiqueta() == etiqueta
                    ? new Campo(campo.etiqueta(), campo.nome(), novoTipo) : campo);

        return new Esquema(nome + " com " + etiqueta + " virando " + novoTipo, todos);
    }

    /** O mesmo esquema com um campo RENOMEADO, que e a mudanca que nao custa nada. */
    public Esquema comNomeTrocado(int etiqueta, String novoNome) {
        List<Campo> todos = new ArrayList<>();

        for (Campo campo : campos)
            todos.add(campo.etiqueta() == etiqueta
                    ? new Campo(campo.etiqueta(), novoNome, campo.tipo()) : campo);

        return new Esquema(nome + " renomeando " + etiqueta, todos);
    }

    @Override
    public String toString() {
        return nome + " com " + campos.size() + " campos";
    }
}
