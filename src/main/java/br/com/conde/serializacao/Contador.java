package br.com.conde.serializacao;

/**
 * O CONTADOR de BYTES, que e a unica medida que vale aqui.
 *
 * <p>Byte, nunca segundo. Segundo muda com a maquina, com o que mais esta rodando e com o
 * humor do coletor de lixo, e a integracao continua roda em tres sistemas. Byte e o mesmo
 * numero em qualquer lugar, e e byte que vai pela rede e fica no disco.
 */
public final class Contador {

    private long bytesEscritos;
    private long bytesLidos;
    private long bytesPulados;
    private long campos;

    public void escreveu(long quantos) {
        bytesEscritos += quantos;
    }

    public void leu(long quantos) {
        bytesLidos += quantos;
    }

    /** Bytes que o leitor atravessou sem entender, por serem de um campo desconhecido. */
    public void pulou(long quantos) {
        bytesPulados += quantos;
    }

    public void campo() {
        campos++;
    }

    public long bytesEscritos() {
        return bytesEscritos;
    }

    public long bytesLidos() {
        return bytesLidos;
    }

    public long bytesPulados() {
        return bytesPulados;
    }

    public long campos() {
        return campos;
    }

    public void zerar() {
        bytesEscritos = 0;
        bytesLidos = 0;
        bytesPulados = 0;
        campos = 0;
    }

    @Override
    public String toString() {
        return bytesEscritos + " bytes escritos, " + bytesLidos + " lidos, " + bytesPulados + " pulados";
    }
}
