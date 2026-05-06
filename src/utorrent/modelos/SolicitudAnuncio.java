package utorrent.modelos;

import java.io.Serializable;

/**
 * Mensaje que un peer envía al Tracker en cada announce.
 *
 * El campo evento sigue el estándar BitTorrent:
 *  - "iniciado":   primer announce, peer entra al swarm
 *  - "completado": peer terminó la descarga (ahora es seeder)
 *  - "detenido":   peer se desconecta limpiamente
 *  - "actualizado": announce periódico de mantenimiento
 */
public class SolicitudAnuncio implements Serializable {

    private static final long serialVersionUID = 1L;

    private final byte[] infoHash;
    private final String peerId;
    private final int puertoEscucha;
    private final long subido;
    private final long descargado;
    private final long restante;
    private final String evento;
    private final String nombreArchivo; // necesario para el primer "iniciado" del seeder

    public SolicitudAnuncio(byte[] infoHash, String peerId, int puertoEscucha,
                            long subido, long descargado, long restante,
                            String evento, String nombreArchivo) {
        this.infoHash = infoHash;
        this.peerId = peerId;
        this.puertoEscucha = puertoEscucha;
        this.subido = subido;
        this.descargado = descargado;
        this.restante = restante;
        this.evento = evento;
        this.nombreArchivo = nombreArchivo;
    }

    public byte[] getInfoHash()      { return infoHash; }
    public String getPeerId()        { return peerId; }
    public int getPuertoEscucha()    { return puertoEscucha; }
    public long getSubido()          { return subido; }
    public long getDescargado()      { return descargado; }
    public long getRestante()        { return restante; }
    public String getEvento()        { return evento; }
    public String getNombreArchivo() { return nombreArchivo; }
}