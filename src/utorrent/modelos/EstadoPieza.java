package utorrent.modelos;

/**
 * Estados posibles de una pieza durante la descarga.
 *
 *  - PENDIENTE: aún no se ha solicitado a ningún peer.
 *  - EN_CURSO:  algún hilo PeerConnectionHandler la está descargando.
 *  - COMPLETADA: pieza recibida, hash SHA-1 verificado, escrita en disco.
 *
 * La transición PENDIENTE → EN_CURSO debe ser atómica para evitar que dos
 * hilos pidan la misma pieza simultáneamente (race condition).
 */
public enum EstadoPieza {
    PENDIENTE,
    EN_CURSO,
    COMPLETADA
}