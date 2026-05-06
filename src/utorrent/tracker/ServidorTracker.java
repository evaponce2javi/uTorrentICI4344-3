package utorrent.tracker;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Servidor centralizado que coordina el descubrimiento de peers (F2 del proyecto).
 *
 * Patrón: un ServerSocket aceptando conexiones en bucle + ExecutorService con
 * pool fijo de hilos (uno por cliente entrante). Demuestra el patrón clásico
 * de servidor concurrente multi-hilo.
 *
 * Recursos compartidos:
 *  - registro:    protegido por ConcurrentHashMap (lecturas sin bloqueo)
 *  - serverSocket: cerrado limpiamente en detener() para desbloquear accept()
 */
public class ServidorTracker {

    private static final int TAMANO_POOL_DEFECTO = 32;

    private final int puerto;
    private final ExecutorService poolHilos;
    private final ScheduledExecutorService mantenimiento;
    private final RegistroPares registro;
    private volatile boolean ejecutando = false;
    private ServerSocket socketServidor;

    public ServidorTracker(int puerto, int maxParesPorIp, int tamanoPool) {
        this.puerto = puerto;
        this.registro = new RegistroPares(maxParesPorIp);
        this.poolHilos = Executors.newFixedThreadPool(tamanoPool, r -> {
            Thread t = new Thread(r, "tracker-handler");
            t.setDaemon(false);
            return t;
        });
        this.mantenimiento = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tracker-mantenimiento");
            t.setDaemon(true);
            return t;
        });
    }

    public void iniciar() throws IOException {
        ejecutando = true;
        socketServidor = new ServerSocket(puerto);
        System.out.printf("[Tracker] Escuchando en puerto %d (maxParesPorIp=%d, pool=%d hilos)%n",
                puerto, RegistroPares.MAX_PARES_POR_IP_DEFECTO,
                ((java.util.concurrent.ThreadPoolExecutor) poolHilos).getMaximumPoolSize());

        // Tarea periódica: limpiar ventana anti-Sybil cada 60 s
        mantenimiento.scheduleAtFixedRate(registro::reiniciarVentana,
                60, 60, TimeUnit.SECONDS);

        while (ejecutando) {
            try {
                Socket cliente = socketServidor.accept();
                // Cada cliente entrante se delega al pool: el accept() vuelve
                // inmediatamente a aceptar la siguiente conexión.
                poolHilos.submit(new ManejadorAnuncio(cliente, registro));
            } catch (IOException e) {
                if (ejecutando) {
                    System.err.println("[Tracker] Error aceptando conexión: " + e.getMessage());
                }
                // Si !ejecutando, es por detener() y es esperado.
            }
        }
    }

    public void detener() {
        ejecutando = false;
        try {
            if (socketServidor != null) socketServidor.close();
        } catch (IOException ignorada) {}
        poolHilos.shutdownNow();
        mantenimiento.shutdownNow();
        System.out.println("[Tracker] Detenido.");
    }

    /**
     * Punto de entrada del Tracker. Pide el puerto al usuario para evitar
     * hardcoding (cumple regla "Cero Hardcoding" del enunciado).
     */
    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            System.out.println("=== Servidor Tracker BitTorrent ===");
            System.out.print("Puerto de escucha (sugerido 6969): ");
            int puerto = Integer.parseInt(sc.nextLine().trim());

            System.out.print("Máximo de peers por IP (sugerido 3): ");
            int maxPorIp = Integer.parseInt(sc.nextLine().trim());

            System.out.print("Tamaño del pool de hilos (sugerido 32): ");
            int pool = Integer.parseInt(sc.nextLine().trim());

            ServidorTracker servidor = new ServidorTracker(puerto, maxPorIp, pool);
            Runtime.getRuntime().addShutdownHook(new Thread(servidor::detener));
            servidor.iniciar();

        } catch (NumberFormatException e) {
            System.err.println("Error: debe ingresar un número válido.");
        } catch (IOException e) {
            System.err.println("Error iniciando el servidor: " + e.getMessage());
        }
    }
}