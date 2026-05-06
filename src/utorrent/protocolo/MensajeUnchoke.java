package utorrent.protocolo;

import java.io.DataOutputStream;

/** "Puedes pedirme bloques: estás unchoked." */
public class MensajeUnchoke extends MensajePeer {
    public MensajeUnchoke() { super(IdMensaje.UNCHOKE); }
    @Override public int longitudPayload() { return 0; }
    @Override protected void escribirPayload(DataOutputStream salida) { /* sin payload */ }
}