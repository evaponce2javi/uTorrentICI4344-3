package utorrent.modelos;

import java.io.Serializable;
import java.util.Objects;

/**
 * Información de contacto de un peer en el swarm.
 *
 * peerId es una cadena de 20 bytes (estándar BitTorrent) usada en el handshake
 * para que dos peers se identifiquen mutuamente sin depender de la IP.
 * Esto sostiene la transparencia de ubicación: el TorrentManager nunca trabaja
 * con IPs directamente, solo con peerIds resueltos por el Tracker.
 */
public class InfoPar implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String peerId;
    private final String direccionIp;
    private final int puerto;

    public InfoPar(String peerId, String direccionIp, int puerto) {
        this.peerId = peerId;
        this.direccionIp = direccionIp;
        this.puerto = puerto;
    }

    public String getPeerId()       { return peerId; }
    public String getDireccionIp()  { return direccionIp; }
    public int getPuerto()          { return puerto; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InfoPar)) return false;
        InfoPar otro = (InfoPar) o;
        return Objects.equals(peerId, otro.peerId);
    }

    @Override
    public int hashCode() { return Objects.hash(peerId); }

    @Override
    public String toString() {
        return String.format("Par[%s @ %s:%d]",
                peerId.substring(0, Math.min(8, peerId.length())),
                direccionIp, puerto);
    }
}