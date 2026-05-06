package utorrent.protocolo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Mapa de bits con las piezas que el remitente posee.
 *
 * Formato:
 *   [4 bytes: longitud=1+X][1 byte: id=5][X bytes: bitfield]
 *
 * Bit-packing MSB-first según BEP-3: el bit más significativo del primer byte
 * representa la pieza 0, el siguiente la pieza 1, y así sucesivamente. Esto
 * difiere del orden natural en muchos lenguajes y es fuente común de bugs:
 * por eso encapsulamos la lógica en GestorBitfield y no en este DTO.
 *
 * Solo es válido enviarlo INMEDIATAMENTE después del handshake, antes de
 * cualquier otro mensaje. Si llega en otro momento, el receptor debe cerrar
 * la conexión (estándar BEP-3).
 */
public class MensajeBitfield extends MensajePeer {

    private final byte[] bitfield;

    public MensajeBitfield(byte[] bitfield) {
        super(IdMensaje.BITFIELD);
        this.bitfield = bitfield;
    }

    public byte[] getBitfield() { return bitfield; }

    @Override
    public int longitudPayload() { return bitfield.length; }

    @Override
    protected void escribirPayload(DataOutputStream salida) throws IOException {
        salida.write(bitfield);
    }

    static MensajeBitfield leerPayload(DataInputStream entrada, int longitudPayload)
            throws IOException {
        byte[] bf = new byte[longitudPayload];
        entrada.readFully(bf);
        return new MensajeBitfield(bf);
    }
}