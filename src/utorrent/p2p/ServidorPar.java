package utorrent.p2p;

import utorrent.modelos.MetadatosTorrent;
import utorrent.utils.LectorBloques;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Acepta conexiones P2P entrantes en el puerto local del peer.
 *
 * Patrón: ServerSocket en bucle accept() + ExecutorService con un hilo por
 * conexión entrante. Cada socket aceptado se envuelve en una SesionPar que
 * gestiona el ciclo de vida P2P completo.
 *
 * Las sesiones activas se mantienen en una CopyOnWriteArrayList para que
 * SesionTorrent pueda iterar sobre ellas (por ejemplo, broadcastear "have"
 * al completar una pieza) sin riesgo de ConcurrentModificationException.
 */
public class ServidorPar {

    private final int puerto;
    private final MetadatosTorrent meta;
    private final String miPeerId;
    private final GestorPiezas gestorPiezas;
    private final EnsambladorPiezas ensamblador;
    private final GestorChoke gestorChoke;
    private final LectorBloques lectorBloques;

    private final ExecutorService poolSesiones;
    private final ExecutorService poolAccept;
    private final CopyOnWriteArrayList<SesionPar> sesionesActivas = new CopyOnWriteArrayList<>();

    private volatile boolean ejecutando = false;
    private ServerSocket socketServidor;

    public ServidorPar(int puerto, MetadatosTorrent meta, String miPeerId,
                       GestorPiezas gestorPiezas, EnsambladorPiezas ensamblador,
                       GestorChoke gestorChoke, LectorBloques lectorBloques) {
        this.puerto = puerto;
        this.meta = meta;
        this.miPeerId = miPeerId;
        this.gestorPiezas = gestorPiezas;
        this.ensamblador = ensamblador;
        this.gestorChoke = gestorChoke;
        this.lectorBloques = lectorBloques;

        AtomicInteger n = new AtomicInteger(1);
        this.poolSesiones = Executors.newFixedThreadPool(16, r -> {
            Thread t = new Thread(r, "sesion-par-" + n.getAndIncrement());
            t.setDaemon(false);
            return t;
        });
        this.poolAccept = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "p2p-accept");
            t.setDaemon(false);
            return t;
        });
    }

    public void iniciar() throws IOException {
        ejecutando = true;
        socketServidor = new ServerSocket(puerto);
        System.out.println("[ServidorPar] Escuchando peers entrantes en puerto " + puerto);

        poolAccept.submit(() -> {
            while (ejecutando) {
                try {
                    Socket socket = socketServidor.accept();
                    SesionPar sesion = new SesionPar(
                            socket, meta, miPeerId,
                            gestorPiezas, ensamblador, gestorChoke,
                            lectorBloques, true);
                    sesionesActivas.add(sesion);
                    poolSesiones.submit(() -> {
                        try { sesion.run(); }
                        finally { sesionesActivas.remove(sesion); }
                    });
                } catch (IOException e) {
                    if (ejecutando) {
                        System.err.println("[ServidorPar] Error en accept: " + e.getMessage());
                    }
                }
            }
        });
    }

    public List<SesionPar> getSesionesActivas() { return sesionesActivas; }

    public void broadcastHave(int indicePieza) {
        for (SesionPar s : sesionesActivas) {
            s.notificarHave(indicePieza);
        }
    }

    public void detener() {
        ejecutando = false;
        for (SesionPar s : sesionesActivas) s.detener();
        try {
            if (socketServidor != null) socketServidor.close();
        } catch (IOException ignorada) {}
        poolAccept.shutdownNow();
        poolSesiones.shutdownNow();
    }

    public int getPuerto() { return puerto; }
}