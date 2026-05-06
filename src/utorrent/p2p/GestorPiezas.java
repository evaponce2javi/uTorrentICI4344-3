package utorrent.p2p;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;
import utorrent.modelos.EstadoPieza;
import utorrent.modelos.MetadatosTorrent;

/**
 * Coordina el estado de las piezas durante una sesión de descarga.
 *
 * Estados (ver {@link EstadoPieza}):
 *   PENDIENTE  → EN_CURSO  (selección atómica al asignar pieza a un peer)
 *   EN_CURSO   → COMPLETADA (cuando se ensambla y verifica el hash)
 *   EN_CURSO   → PENDIENTE  (re-encolado por falla de valor o crash de peer)
 *
 * La transición PENDIENTE → EN_CURSO se hace con compareAndSet sobre
 * AtomicReferenceArray, garantizando que dos hilos PeerConnectionHandler
 * no reciban la misma pieza incluso si la solicitan al mismo tiempo.
 *
 * Política de selección: ALEATORIA entre las piezas faltantes que el peer
 * remoto tiene disponibles. El enunciado indica que para esta versión
 * académica esto es aceptable (rarest-first es opcional).
 */
public class GestorPiezas {

    private final MetadatosTorrent meta;
    private final AtomicReferenceArray<EstadoPieza> estados;
    private final java.util.Random rng = new java.util.Random();

    public GestorPiezas(MetadatosTorrent meta, boolean yaCompleto) {
        this.meta = meta;
        this.estados = new AtomicReferenceArray<>(meta.totalPiezas());
        for (int i = 0; i < estados.length(); i++) {
            estados.set(i, yaCompleto ? EstadoPieza.COMPLETADA : EstadoPieza.PENDIENTE);
        }
    }

    /**
     * Selecciona aleatoriamente una pieza pendiente que el peer remoto tenga.
     * Marca la pieza como EN_CURSO atómicamente; si otro hilo logró marcarla
     * antes, se intenta con la siguiente candidata.
     *
     * @return índice de la pieza seleccionada, o -1 si no hay candidatas útiles
     */
    public int seleccionarSiguientePieza(Set<Integer> piezasDelPeer) {
        // Construimos la lista de candidatas (piezas pendientes que tiene el peer)
        List<Integer> candidatas = new ArrayList<>();
        for (int idx : piezasDelPeer) {
            if (estados.get(idx) == EstadoPieza.PENDIENTE) {
                candidatas.add(idx);
            }
        }
        if (candidatas.isEmpty()) return -1;

        // Selección aleatoria: barajamos y vamos intentando hasta encontrar una
        // que aún esté PENDIENTE al momento del compareAndSet (otro hilo puede
        // habérnosla "robado" entre la lectura y la escritura).
        Collections.shuffle(candidatas, rng);
        for (int idx : candidatas) {
            if (estados.compareAndSet(idx, EstadoPieza.PENDIENTE, EstadoPieza.EN_CURSO)) {
                return idx;
            }
        }
        return -1;
    }

    /** Marca como completada. Solo debe llamarse tras verificación SHA-1 exitosa. */
    public void marcarCompletada(int indicePieza) {
        estados.set(indicePieza, EstadoPieza.COMPLETADA);
    }

    /**
     * Re-encola una pieza fallida. Causas posibles:
     *  - Falla de valor: hash SHA-1 incorrecto
     *  - Crash del peer: SocketException antes de completar el ensamblado
     *  - Timeout en la transferencia
     */
    public void reencolar(int indicePieza) {
        estados.compareAndSet(indicePieza, EstadoPieza.EN_CURSO, EstadoPieza.PENDIENTE);
    }

    public boolean tienePieza(int indicePieza) {
        return estados.get(indicePieza) == EstadoPieza.COMPLETADA;
    }

    public boolean estaCompleto() {
        for (int i = 0; i < estados.length(); i++) {
            if (estados.get(i) != EstadoPieza.COMPLETADA) return false;
        }
        return true;
    }

    public int totalPiezas() { return meta.totalPiezas(); }

    public int piezasCompletadas() {
        int n = 0;
        for (int i = 0; i < estados.length(); i++) {
            if (estados.get(i) == EstadoPieza.COMPLETADA) n++;
        }
        return n;
    }

    public byte[] hashEsperado(int indicePieza) {
        return meta.getHashesDePiezas().get(indicePieza);
    }

    public int longitudDePieza(int indicePieza) {
        return meta.longitudPiezaEn(indicePieza);
    }

    public MetadatosTorrent getMetadatos() { return meta; }
}