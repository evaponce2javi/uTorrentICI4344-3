package utorrent.modelos;

import java.io.Serializable;
import java.util.List;

/**
 * Respuesta del Tracker a un announce.
 *
 *  - exito:      si la operación fue aceptada
 *  - mensaje:    descripción legible (útil para errores: rate limit, hash desconocido)
 *  - intervalo:  segundos hasta el próximo announce que el peer debe enviar
 *  - pares:      lista de peers actualmente conectados al swarm (excluye al solicitante)
 *  - metadatos:  enviado solo en la consulta por nombre de archivo (búsqueda simplificada)
 */
public class RespuestaAnuncio implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean exito;
    private final String mensaje;
    private final int intervalo;
    private final List<InfoPar> pares;
    private final MetadatosTorrent metadatos;

    public RespuestaAnuncio(boolean exito, String mensaje, int intervalo,
                            List<InfoPar> pares, MetadatosTorrent metadatos) {
        this.exito = exito;
        this.mensaje = mensaje;
        this.intervalo = intervalo;
        this.pares = pares;
        this.metadatos = metadatos;
    }

    public boolean isExito()                   { return exito; }
    public String getMensaje()                 { return mensaje; }
    public int getIntervalo()                  { return intervalo; }
    public List<InfoPar> getPares()            { return pares; }
    public MetadatosTorrent getMetadatos()     { return metadatos; }
}