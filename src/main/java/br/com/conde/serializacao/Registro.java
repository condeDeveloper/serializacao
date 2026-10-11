package br.com.conde.serializacao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.conde.serializacao.Esquema.Campo;
import br.com.conde.serializacao.Esquema.Tipo;

/**
 * UM REGISTRO: os valores de um esquema, por ETIQUETA.
 *
 * <p>Por etiqueta e nao por nome, de proposito. O nome do campo so existe no codigo-fonte
 * e nunca vai para o fio, e e por isso que renomear um campo e de graca.
 *
 * <p>Esta classe tambem guarda o que o leitor NAO ENTENDEU: campos que vieram no fio com
 * uma etiqueta que o esquema dele nao conhece. Guardar e nao jogar fora e o que permite
 * reescrever o registro sem perder o que um escritor mais novo mandou.
 */
public final class Registro {

    private final Map<Integer, Object> valores = new LinkedHashMap<>();
    private final Map<Integer, byte[]> naoEntendidos = new LinkedHashMap<>();

    public Registro poe(int etiqueta, Object valor) {
        valores.put(etiqueta, valor);

        return this;
    }

    public Object valor(int etiqueta) {
        return valores.get(etiqueta);
    }

    public Long numero(int etiqueta) {
        Object valor = valores.get(etiqueta);

        return valor instanceof Long numero ? numero : null;
    }

    public String texto(int etiqueta) {
        Object valor = valores.get(etiqueta);

        return valor instanceof String texto ? texto : null;
    }

    public Map<Integer, Object> valores() {
        return valores;
    }

    /** As etiquetas que vieram no fio e que o esquema do leitor nao conhece. */
    public Map<Integer, byte[]> naoEntendidos() {
        return naoEntendidos;
    }

    void guardarNaoEntendido(int etiqueta, byte[] bytes) {
        naoEntendidos.put(etiqueta, bytes);
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof Registro registro && valores.equals(registro.valores);
    }

    @Override
    public int hashCode() {
        return valores.hashCode();
    }

    @Override
    public String toString() {
        return valores.toString();
    }

    // ───────────────────────────── escrever e ler ─────────────────────────────

    /**
     * Escreve o registro no fio, campo por campo: ETIQUETA, TIPO, VALOR.
     *
     * <p>O tipo vai junto com o valor e nao so no esquema, e e isso que permite a um
     * leitor que nao conhece a etiqueta saber QUANTOS BYTES pular. Um formato que so
     * guardasse etiqueta e valor nao teria evolucao de esquema: teria quebra.
     *
     * <p>Um campo com valor nulo simplesmente nao e escrito. No fio, "ausente" e
     * "nulo" sao a mesma coisa, e e por isso que acrescentar um campo a um esquema e
     * seguro: o leitor novo le o registro velho e nao acha a etiqueta.
     */
    public byte[] escrever(Esquema esquema, Contador contador) {
        Fio fio = new Fio(contador);

        for (Campo campo : esquema.campos()) {
            Object valor = valores.get(campo.etiqueta());

            if (valor == null) continue;

            contador.campo();

            fio.poeVarinteiro(campo.etiqueta());
            fio.poeVarinteiro(campo.tipo().ordinal());

            switch (campo.tipo()) {
                case VARINTEIRO -> fio.poeVarinteiro((Long) valor);
                case ZIGUE_ZAGUE -> fio.poeZigueZague((Long) valor);
                case FIXO -> fio.poeFixo64((Long) valor);
                case CADEIA -> fio.poeCadeia((String) valor);
                default -> throw new IllegalStateException("tipo desconhecido: " + campo.tipo());
            }
        }

        return fio.paraVetor();
    }

    /**
     * Le um registro do fio com o esquema dado.
     *
     * <p>Uma etiqueta que o esquema nao conhece NAO quebra a leitura: o tipo que veio no
     * fio diz quantos bytes pular, e o campo e guardado como bytes crus.
     *
     * <p>Uma etiqueta conhecida com TIPO diferente do esperado e outra historia, e e a
     * mudanca perigosa que a tabela da evolucao mede. O que esta no fio e lido pelo tipo
     * do FIO, porque e o unico jeito de saber onde o campo acaba, e depois guardado como
     * veio. Se o tipo mudou, o valor que o leitor ve nao e o que o escritor mandou.
     */
    public static Registro ler(byte[] bytes, Esquema esquema, Contador contador) {
        Fio fio = Fio.paraLer(bytes, contador);
        Registro registro = new Registro();

        while (!fio.acabou()) {
            int etiqueta = (int) fio.leVarinteiro();
            int tipoNoFio = (int) fio.leVarinteiro();

            if (tipoNoFio < 0 || tipoNoFio >= Tipo.values().length)
                throw new IllegalStateException("tipo desconhecido no fio: " + tipoNoFio);

            Tipo tipo = Tipo.values()[tipoNoFio];
            Campo campo = esquema.porEtiqueta(etiqueta);

            int antes = fio.posicaoDeLeitura();
            Object valor = lerPeloTipo(fio, tipo);

            contador.campo();

            if (campo == null) {
                registro.guardarNaoEntendido(etiqueta, new byte[fio.posicaoDeLeitura() - antes]);
                continue;
            }

            registro.poe(etiqueta, valor);
        }

        return registro;
    }

    private static Object lerPeloTipo(Fio fio, Tipo tipo) {
        return switch (tipo) {
            case VARINTEIRO -> fio.leVarinteiro();
            case ZIGUE_ZAGUE -> fio.leZigueZague();
            case FIXO -> fio.leFixo64();
            case CADEIA -> fio.leCadeia();
        };
    }

    // ───────────────── o mesmo formato SEM o tipo no fio ─────────────────

    /**
     * Escreve SEM o tipo, so etiqueta e valor, guiado pelo esquema dos dois lados.
     *
     * <p>Economiza um byte por campo, e e assim que um formato guiado por esquema
     * funciona de verdade: o esquema nao vai junto, entao nao ha o que repetir em cada
     * registro.
     *
     * <p>O preco e que o leitor PRECISA do esquema certo. Sem o tipo no fio ele nao sabe
     * onde um campo acaba, entao nao pode pular o que nao conhece, e nao pode nem
     * desconfiar quando o tipo mudou. A tabela da evolucao mede os dois precos.
     */
    public byte[] escreverSemTipo(Esquema esquema, Contador contador) {
        Fio fio = new Fio(contador);

        for (Campo campo : esquema.campos()) {
            Object valor = valores.get(campo.etiqueta());

            if (valor == null) continue;

            contador.campo();
            fio.poeVarinteiro(campo.etiqueta());

            switch (campo.tipo()) {
                case VARINTEIRO -> fio.poeVarinteiro((Long) valor);
                case ZIGUE_ZAGUE -> fio.poeZigueZague((Long) valor);
                case FIXO -> fio.poeFixo64((Long) valor);
                case CADEIA -> fio.poeCadeia((String) valor);
                default -> throw new IllegalStateException("tipo desconhecido: " + campo.tipo());
            }
        }

        return fio.paraVetor();
    }

    /**
     * Le SEM o tipo, usando o tipo do esquema DO LEITOR.
     *
     * <p>Uma etiqueta desconhecida aqui nao da para pular: o leitor nao sabe quantos bytes
     * ela ocupa. Ele so pode parar, e entao o resto do registro se perde.
     *
     * <p>E um tipo trocado nao da erro: ele le os bytes do jeito errado e devolve um
     * numero plausivel. E esse o custo do byte economizado.
     */
    public static Registro lerSemTipo(byte[] bytes, Esquema esquema, Contador contador) {
        Fio fio = Fio.paraLer(bytes, contador);
        Registro registro = new Registro();

        while (!fio.acabou()) {
            int etiqueta = (int) fio.leVarinteiro();
            Campo campo = esquema.porEtiqueta(etiqueta);

            contador.campo();

            if (campo == null)
                throw new IllegalStateException("etiqueta " + etiqueta
                        + " desconhecida e sem tipo no fio: nao da para pular");

            registro.poe(etiqueta, lerPeloTipo(fio, campo.tipo()));
        }

        return registro;
    }

    /** As etiquetas que este registro tem, em ordem. */
    public List<Integer> etiquetas() {
        return new ArrayList<>(valores.keySet());
    }
}
