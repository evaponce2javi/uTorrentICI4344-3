package utorrent.modelos;

import java.io.Serializable;
import java.util.List;

/**
 * Metadatos de un torrent. Equivale al contenido de un archivo .torrent real,
 * pero generado en memoria por el Seeder a partir del archivo a compartir.
 *
 * Campos clave del estándar BitTorrent (BEP-3):
 *  - infoHash: SHA-1 (20 bytes) del diccionario "info"; identifica el torrent en toda la red
 *  - hashesDePiezas: lista con el SHA-1 de cada pieza, en orden
 *  - longitudPieza: tamaño en bytes de cada pieza (la última puede ser menor)
 */
public class MetadatosTorrent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nombreArchivo;
    private final long longitudTotal;
    private final int longitudPieza;
    private final byte[] infoHash;          // 20 bytes
    private final List<byte[]> hashesDePiezas; // cada uno 20 bytes
    private final String ipTracker;
    private final int puertoTracker;

    public MetadatosTorrent(String nombreArchivo, long longitudTotal, int longitudPieza,
                            byte[] infoHash, List<byte[]> hashesDePiezas,
                            String ipTracker, int puertoTracker) {
        this.nombreArchivo = nombreArchivo;
        this.longitudTotal = longitudTotal;
        this.longitudPieza = longitudPieza;
        this.infoHash = infoHash;
        this.hashesDePiezas = hashesDePiezas;
        this.ipTracker = ipTracker;
        this.puertoTracker = puertoTracker;
    }

    public String getNombreArchivo()       { return nombreArchivo; }
    public long getLongitudTotal()         { return longitudTotal; }
    public int getLongitudPieza()          { return longitudPieza; }
    public byte[] getInfoHash()            { return infoHash; }
    public List<byte[]> getHashesDePiezas(){ return hashesDePiezas; }
    public String getIpTracker()           { return ipTracker; }
    public int getPuertoTracker()          { return puertoTracker; }

    /** Cantidad total de piezas en que se divide el archivo. */
    public int totalPiezas() {
        return hashesDePiezas.size();
    }

    /**
     * Devuelve el tamaño real de la pieza en el índice dado.
     * La última pieza puede ser menor que longitudPieza si el archivo no
     * es múltiplo exacto del tamaño de pieza.
     */
    public int longitudPiezaEn(int indice) {
        if (indice == totalPiezas() - 1) {
            long resto = longitudTotal % longitudPieza;
            return resto == 0 ? longitudPieza : (int) resto;
        }
        return longitudPieza;
    }
}