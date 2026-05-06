package utorrent.protocolo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Solicitud de un bloque al peer remoto.
 *
 * Formato:
 *   [4 bytes: longitud=13][1 byte: id=6]
 *   [4 bytes: índice de pieza][4 bytes: begin (offset dentro de la pieza)]
 *   [4 bytes: longitud del bloque solicitado, típicamente 16 384]
 *
 * Importante: "begin" es el offset DENTRO DE LA PIEZA, no dentro del archivo.
 * El offset absoluto se calcula como: índicePieza * longitudPieza + begin.
 *
 * Por convención, longitud nunca debe exceder 16 KB. Algunos clientes
 * cierran la conexión si reciben requests con longitudes mayores como
 * defensa contra ataques de amplificación.
 */
public class MensajeRequest extends MensajePeer {

    private final int indicePieza;
    private final int begin;
    private final int longitud;

    public MensajeRequest(int indicePieza, int begin, int longitud) {
        super(IdMensaje.REQUEST);
        this.indicePieza = indicePieza;
        this.begin = begin;
        this.longitud = longitud;
    }

    public int getIndicePieza() { return indicePieza; }
    public int getBegin()       { return begin; }
    public int getLongitud()    { return longitud; }

    @Override
    public int longitudPayload() { return 12; }

    @Override
    protected void escribirPayload(DataOutputStream salida) throws IOException {
        salida.writeInt(indicePieza);
        salida.writeInt(begin);
        salida.writeInt(longitud);
    }

    static MensajeRequest leerPayload(DataInputStream entrada) throws IOException {
        int indice = entrada.readInt();
        int begin  = entrada.readInt();
        int longitud = entrada.readInt();
        return new MensajeRequest(indice, begin, longitud);
    }
}