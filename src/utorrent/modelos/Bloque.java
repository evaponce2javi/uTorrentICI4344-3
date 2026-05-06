package utorrent.modelos;

/**
 * Bloque de datos dentro de una pieza. BitTorrent transfiere por bloques
 * (típicamente 16 KB) para permitir descargas paralelas desde varios peers
 * de la misma pieza y para evitar bloqueos prolongados de un solo socket.
 *
 *  - indicePieza: a qué pieza pertenece este bloque
 *  - offset:      posición en bytes dentro de la pieza (múltiplo de 16384)
 *  - datos:       los bytes del bloque (puede ser más corto en el último bloque)
 */
public class Bloque {

    /** Tamaño estándar de bloque en BitTorrent: 2^14 = 16 384 bytes. */
    public static final int TAMANO_ESTANDAR = 16_384;

    private final int indicePieza;
    private final int offset;
    private final byte[] datos;

    public Bloque(int indicePieza, int offset, byte[] datos) {
        this.indicePieza = indicePieza;
        this.offset = offset;
        this.datos = datos;
    }

    public int getIndicePieza() { return indicePieza; }
    public int getOffset()      { return offset; }
    public byte[] getDatos()    { return datos; }
    public int getLongitud()    { return datos.length; }
}