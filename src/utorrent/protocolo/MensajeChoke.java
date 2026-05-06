package utorrent.protocolo;

import java.io.DataOutputStream;

/** "No te voy a enviar bloques por ahora." */
public class MensajeChoke extends MensajePeer {
    public MensajeChoke() { super(IdMensaje.CHOKE); }
    @Override public int longitudPayload() { return 0; }
    @Override protected void escribirPayload(DataOutputStream salida) { /* sin payload */ }
}