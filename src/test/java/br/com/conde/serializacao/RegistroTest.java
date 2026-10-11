package br.com.conde.serializacao;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.conde.serializacao.Esquema.Campo;
import br.com.conde.serializacao.Esquema.Tipo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Os testes do REGISTRO, do ESQUEMA e do que acontece quando o esquema muda. */
class RegistroTest {

    private final Contador contador = new Contador();

    @Test
    @DisplayName("um registro volta igual a si mesmo, pelo mesmo esquema")
    void aIdaEVoltaDoRegistro() {
        Esquema esquema = Medidas.pedido();

        for (Registro registro : Medidas.registrosDeVerdade(2_000, 5))
            assertThat(Juiz.idaEVoltaDoRegistro(registro, esquema, contador))
                    .as(registro.toString()).isTrue();
    }

    @Test
    @DisplayName("um campo nulo simplesmente nao e escrito, e ausente e nulo sao a mesma coisa")
    void oCampoNuloNaoVaiProFio() {
        Esquema esquema = Medidas.pedido();

        Registro cheio = new Registro().poe(1, 1L).poe(5, "texto");
        Registro vazio = new Registro().poe(1, 1L);

        byte[] fioCheio = cheio.escrever(esquema, contador);
        byte[] fioVazio = vazio.escrever(esquema, contador);

        assertThat(fioVazio.length).isLessThan(fioCheio.length);
        assertThat(Registro.ler(fioVazio, esquema, contador).valor(5)).isNull();
    }

    @Test
    @DisplayName("um registro vazio da um fio vazio e volta vazio")
    void oRegistroVazio() {
        byte[] fio = new Registro().escrever(Medidas.pedido(), contador);

        assertThat(fio).isEmpty();
        assertThat(Registro.ler(fio, Medidas.pedido(), contador).valores()).isEmpty();
    }

    // ───────────────────── a evolucao, com o tipo no fio ─────────────────────

    /**
     * ACRESCENTAR UM CAMPO e seguro nas duas direcoes.
     *
     * <p>O leitor novo le o fio velho e nao acha a etiqueta, que e o mesmo que nulo. O
     * leitor velho le o fio novo, nao conhece a etiqueta, e PULA, porque o tipo veio no
     * fio dizendo quantos bytes ela ocupa.
     */
    @Test
    @DisplayName("acrescentar um campo nao quebra nenhuma das duas direcoes")
    void acrescentarUmCampo() {
        Esquema velho = Medidas.pedido();
        Esquema novo = velho.com(new Campo(6, "desconto", Tipo.ZIGUE_ZAGUE));

        Registro comOCampoNovo = new Registro().poe(1, 42L).poe(5, "texto").poe(6, -99L);

        byte[] fioNovo = comOCampoNovo.escrever(novo, contador);
        Registro peloVelho = Registro.ler(fioNovo, velho, contador);

        assertThat(peloVelho.numero(1)).isEqualTo(42L);
        assertThat(peloVelho.texto(5)).isEqualTo("texto");
        assertThat(peloVelho.valor(6)).as("o velho nao conhece a etiqueta 6").isNull();
        assertThat(peloVelho.naoEntendidos()).as("mas ele guardou que ela existia").containsKey(6);
    }

    /**
     * REMOVER UM CAMPO tambem e seguro, e pelo mesmo motivo: o leitor novo pula a
     * etiqueta que ele nao conhece mais.
     */
    @Test
    @DisplayName("remover um campo nao quebra a leitura do fio velho")
    void removerUmCampo() {
        Esquema velho = Medidas.pedido();
        Esquema novo = velho.sem(5);

        Registro registro = new Registro().poe(1, 42L).poe(5, "vai sumir");

        Registro peloNovo = Registro.ler(registro.escrever(velho, contador), novo, contador);

        assertThat(peloNovo.numero(1)).isEqualTo(42L);
        assertThat(peloNovo.valor(5)).isNull();
        assertThat(peloNovo.naoEntendidos()).containsKey(5);
    }

    /**
     * RENOMEAR UM CAMPO nao custa nada, porque o nome nunca foi para o fio: o que vai e a
     * ETIQUETA.
     */
    @Test
    @DisplayName("renomear um campo da exatamente os mesmos bytes")
    void renomearUmCampo() {
        Esquema velho = Medidas.pedido();
        Esquema renomeado = velho.comNomeTrocado(2, "comprador");

        Registro registro = new Registro().poe(1, 42L).poe(2, 7L);

        assertThat(registro.escrever(renomeado, contador))
                .isEqualTo(registro.escrever(velho, contador));
    }

    /**
     * TROCAR O TIPO nao corrompe NADA quando o tipo vai no fio: o leitor le pelo tipo que
     * CHEGOU e nao pelo que esperava.
     *
     * <p>Foi isto que a tabela da evolucao mostrou e eu nao esperava. A mudanca que eu
     * tinha escrito como "a perigosa" e inofensiva neste formato.
     */
    @Test
    @DisplayName("com o tipo no fio, trocar o tipo de um campo nao corrompe o valor")
    void trocarOTipoComOTipoNoFio() {
        Esquema velho = Medidas.pedido();
        Esquema novo = velho.comTipoTrocado(3, Tipo.FIXO);

        Registro registro = new Registro().poe(3, -2_500L);

        assertThat(Registro.ler(registro.escrever(velho, contador), novo, contador).numero(3))
                .isEqualTo(-2_500L);

        assertThat(Registro.ler(registro.escrever(novo, contador), velho, contador).numero(3))
                .isEqualTo(-2_500L);
    }

    // ───────────────────── a evolucao, SEM o tipo no fio ─────────────────────

    /**
     * SEM O TIPO NO FIO, trocar o tipo CORROMPE em silencio: o leitor le os bytes do jeito
     * errado e devolve um numero plausivel.
     */
    @Test
    @DisplayName("sem o tipo no fio, trocar o tipo devolve outro numero sem dar erro")
    void trocarOTipoSemOTipoNoFio() {
        Esquema velho = Medidas.pedido();
        Esquema novo = velho.comTipoTrocado(3, Tipo.VARINTEIRO);

        Registro registro = new Registro().poe(3, -2_500L);

        Long lido = Registro.lerSemTipo(registro.escreverSemTipo(velho, contador), novo, contador)
                .numero(3);

        assertThat(lido).as("nao deu erro").isNotNull();
        assertThat(lido).as("e nao e o valor original").isNotEqualTo(-2_500L);
    }

    /**
     * E SEM O TIPO NO FIO, uma etiqueta desconhecida nao da para pular: o leitor nao sabe
     * quantos bytes ela ocupa e so pode parar.
     */
    @Test
    @DisplayName("sem o tipo no fio, uma etiqueta desconhecida quebra a leitura inteira")
    void aEtiquetaDesconhecidaSemTipo() {
        Esquema velho = Medidas.pedido();
        Esquema novo = velho.com(new Campo(6, "desconto", Tipo.ZIGUE_ZAGUE));

        Registro registro = new Registro().poe(1, 42L).poe(6, -99L);
        byte[] fioNovo = registro.escreverSemTipo(novo, contador);

        assertThatThrownBy(() -> Registro.lerSemTipo(fioNovo, velho, contador))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao da para pular");
    }

    /**
     * O BYTE QUE O TIPO CUSTA: um por campo, e ele compra a evolucao de esquema inteira.
     */
    @Test
    @DisplayName("o tipo no fio custa um byte por campo")
    void oPrecoDoTipoNoFio() {
        Esquema esquema = Medidas.pedido();
        Registro registro = Medidas.registrosDeVerdade(1, 1).get(0);

        int comTipo = registro.escrever(esquema, contador).length;
        int semTipo = registro.escreverSemTipo(esquema, contador).length;

        assertThat(comTipo - semTipo).isEqualTo(registro.valores().size());
    }

    // ───────────────────── o esquema ─────────────────────

    @Test
    @DisplayName("o esquema recusa etiqueta repetida e etiqueta nao positiva")
    void oEsquemaInvalido() {
        assertThatThrownBy(() -> new Esquema("ruim", List.of(
                new Campo(1, "a", Tipo.FIXO),
                new Campo(1, "b", Tipo.FIXO))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repetida");

        assertThatThrownBy(() -> new Campo(0, "a", Tipo.FIXO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positiva");
    }

    @Test
    @DisplayName("as transformacoes do esquema dao o esquema que prometem")
    void asTransformacoesDoEsquema() {
        Esquema esquema = Medidas.pedido();

        assertThat(esquema.campos()).hasSize(5);
        assertThat(esquema.sem(3).campos()).hasSize(4);
        assertThat(esquema.sem(3).porEtiqueta(3)).isNull();
        assertThat(esquema.com(new Campo(9, "novo", Tipo.FIXO)).campos()).hasSize(6);
        assertThat(esquema.comTipoTrocado(3, Tipo.FIXO).porEtiqueta(3).tipo()).isEqualTo(Tipo.FIXO);
        assertThat(esquema.comNomeTrocado(3, "outro").porEtiqueta(3).nome()).isEqualTo("outro");
        assertThat(esquema.comNomeTrocado(3, "outro").porEtiqueta(3).tipo())
                .as("renomear nao mexe no tipo").isEqualTo(Tipo.ZIGUE_ZAGUE);
    }

    @Test
    @DisplayName("um tipo desconhecido no fio reclama em vez de ler lixo")
    void oTipoDesconhecidoNoFio() {
        Fio fio = new Fio(contador);

        fio.poeVarinteiro(1);
        fio.poeVarinteiro(99);

        assertThatThrownBy(() -> Registro.ler(fio.paraVetor(), Medidas.pedido(), contador))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tipo desconhecido");
    }

    @Test
    @DisplayName("o registro compara pelos valores e nao pelo que nao foi entendido")
    void aIgualdadeDoRegistro() {
        Registro um = new Registro().poe(1, 1L);
        Registro outro = new Registro().poe(1, 1L);

        assertThat(um).isEqualTo(outro);
        assertThat(um.hashCode()).isEqualTo(outro.hashCode());
        assertThat(um).isNotEqualTo(new Registro().poe(1, 2L));
        assertThat(um.etiquetas()).containsExactly(1);
        assertThat(um.numero(1)).isEqualTo(1L);
        assertThat(um.texto(1)).as("nao e texto").isNull();
    }
}
