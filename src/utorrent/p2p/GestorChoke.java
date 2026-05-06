package utorrent.p2p;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Implementa el algoritmo tit-for-tat de BitTorrent:
 *
 *  - 4 slots de unchoke regular: los peers que más bytes nos han enviado en la
 *    última ventana reciben "unchoke" y por tanto pueden pedirnos bloques.
 *  - 1 unchoke optimista rotativo cada 30 s: se elige aleatoriamente un peer
 *    chocado y se le da una oportunidad. Es el mecanismo de descubrimiento:
 *    así un peer recién conectado puede entrar al juego sin haber demostrado
 *    nada todavía.
 *
 * Fase de warm-up: al inicio no hay historial de tasas, así que los primeros
 * 4 peers que se conectan reciben unchoke gratuito hasta el primer recálculo
 * (a los 10 s). Es el comportamiento estándar de libtorrent y otros clientes
 * reales, conocido como "initial unchoke seed".
 *
 * Solo los peers en unchoked (o el optimisticPeer) reciben datos cuando piden
 * bloques: ProtocoloBitTorrent llama a estaUnchoked() antes de servir.
 */
public class GestorChoke {

    private static final int SLOTS_REGULARES = 4;
    private static final int INTERVALO_REGULAR_S = 10;
    private static final int INTERVALO_OPTIMISTA_S = 30;

    private final ConcurrentHashMap<String, Long> tasaDescarga = new ConcurrentHashMap<>();
    private final Set<String> unchoked = ConcurrentHashMap.newKeySet();
    private final Set<String> conocidos = ConcurrentHashMap.newKeySet();
    private volatile String paresOptimista;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "choke-scheduler");
                t.setDaemon(true);
                return t;
            });

    public void iniciar() {
        scheduler.scheduleAtFixedRate(this::recalcularRegulares,
                INTERVALO_REGULAR_S, INTERVALO_REGULAR_S, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::rotarOptimista,
                INTERVALO_OPTIMISTA_S, INTERVALO_OPTIMISTA_S, TimeUnit.SECONDS);
    }

    public void detener() { scheduler.shutdownNow(); }

    /**
     * Registra un peer recién conectado. En fase de warm-up (antes del primer
     * recálculo), recibe slot inicial gratuito si hay capacidad.
     */
    public void registrarPar(String peerId) {
        conocidos.add(peerId);
        tasaDescarga.putIfAbsent(peerId, 0L);
        if (unchoked.size() < SLOTS_REGULARES) {
            unchoked.add(peerId); // warm-up: slot inicial
        }
    }

    public void desregistrarPar(String peerId) {
        conocidos.remove(peerId);
        unchoked.remove(peerId);
        tasaDescarga.remove(peerId);
        if (peerId.equals(paresOptimista)) paresOptimista = null;
    }

    public void registrarBytesDescargados(String peerId, long bytes) {
        tasaDescarga.merge(peerId, bytes, Long::sum);
    }

    public boolean estaUnchoked(String peerId) {
        return unchoked.contains(peerId) || peerId.equals(paresOptimista);
    }

    private void recalcularRegulares() {
        List<Map.Entry<String, Long>> ranking = new ArrayList<>(tasaDescarga.entrySet());
        ranking.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        Set<String> nuevos = new HashSet<>();
        for (int i = 0; i < Math.min(SLOTS_REGULARES, ranking.size()); i++) {
            // Solo entran al top quienes efectivamente aportaron bytes (>0).
            // Antes del primer ciclo con datos reales, esto deja la lista vacía
            // y los slots quedan disponibles para los siguientes warm-ups.
            if (ranking.get(i).getValue() > 0) nuevos.add(ranking.get(i).getKey());
        }

        unchoked.clear();
        unchoked.addAll(nuevos);
        tasaDescarga.replaceAll((k, v) -> 0L); // reset de la ventana

        System.out.println("[Choke] Unchoke regular (tit-for-tat): " + nuevos);
    }

    private void rotarOptimista() {
        List<String> candidatos = new ArrayList<>();
        for (String p : conocidos) if (!unchoked.contains(p)) candidatos.add(p);
        if (candidatos.isEmpty()) {
            paresOptimista = null;
            return;
        }
        paresOptimista = candidatos.get(ThreadLocalRandom.current().nextInt(candidatos.size()));
        System.out.println("[Choke] Unchoke optimista rotado a: " + paresOptimista);
    }
}