package utorrent.protocolo;

import java.io.DataOutputStream;

/** "Ya no me interesan tus piezas; puedes ahorrar recursos." */
public class MensajeNotInterested extends MensajePeer {
    public MensajeNotInterested() { super(IdMensaje.NOT_INTERESTED); }
    @Override public int longitudPayload() { return 0; }
    @Override protected void escribirPayload(DataOutputStream salida) { /* sin payload */ }
}