package utorrent.protocolo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Clase base de todos los mensajes BitTorrent post-handshake.
 *
 * Formato común (BEP-3):
 *   [4 bytes: longitud N+1][1 byte: id][N bytes: payload]
 *
 * Caso especial: keep-alive es un mensaje sin id ni payload, solo el prefijo
 * de longitud con valor 0. Se modela aparte en la lectura.
 *
 * El método estático leerSiguiente() es el punto de entrada para cualquier
 * receptor: lee la longitud, lee el id, y delega al constructor adecuado.
 */
public abstract class MensajePeer {

    private final byte id;

    protected MensajePeer(byte id) { this.id = id; }

    public byte getId() { return id; }

    /** Tamaño en bytes del payload (sin contar el id ni la longitud). */
    public abstract int longitudPayload();

    /** Escribe el payload puro al stream (sin prefijo de longitud ni id). */
    protected abstract void escribirPayload(DataOutputStream salida) throws IOException;

    /**
     * Escribe el mensaje completo: prefijo de longitud, id y payload.
     * Para keep-alive (longitud=0), no se escribe id ni payload.
     */
    public void escribirEn(DataOutputStream salida) throws IOException {
        int longitud = 1 + longitudPayload(); // 1 byte del id + payload
        salida.writeInt(longitud);
        salida.writeByte(id);
        escribirPayload(salida);
        salida.flush();
    }

    /**
     * Lee el siguiente mensaje del stream. Devuelve null si recibe un
     * keep-alive (longitud=0), permitiendo al llamador interpretarlo.
     */
    public static MensajePeer leerSiguiente(DataInputStream entrada) throws IOException {
        int longitud = entrada.readInt();
        if (longitud < 0) {
            throw new IOException("Longitud de mensaje negativa: " + longitud);
        }
        if (longitud == 0) {
            return null; // keep-alive
        }
        byte id = entrada.readByte();
        int longitudPayload = longitud - 1;

        switch (id) {
            case IdMensaje.CHOKE:          return new MensajeChoke();
            case IdMensaje.UNCHOKE:        return new MensajeUnchoke();
            case IdMensaje.INTERESTED:     return new MensajeInterested();
            case IdMensaje.NOT_INTERESTED: return new MensajeNotInterested();
            case IdMensaje.HAVE:           return MensajeHave.leerPayload(entrada);
            case IdMensaje.BITFIELD:       return MensajeBitfield.leerPayload(entrada, longitudPayload);
            case IdMensaje.REQUEST:        return MensajeRequest.leerPayload(entrada);
            case IdMensaje.PIECE:          return MensajePiece.leerPayload(entrada, longitudPayload);
            default:
                // Mensaje desconocido o no implementado: descartar bytes restantes
                // para mantener el stream consistente y evitar desalineamiento.
                entrada.skipBytes(longitudPayload);
                throw new IOException("ID de mensaje no soportado: " + id);
        }
    }
}