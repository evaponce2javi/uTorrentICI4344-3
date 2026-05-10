package utorrent.app;

import utorrent.modelos.MetadatosTorrent;
import utorrent.modelos.RespuestaAnuncio;
import utorrent.tracker.ClienteTracker;
import utorrent.utils.CalculadorHash;
import utorrent.utils.ConfiguracionRed;
import utorrent.utils.EscritorBloques;
import utorrent.utils.GeneradorPeerId;
import utorrent.utils.LectorBloques;
import utorrent.utils.LimitesArchivo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

/**
 * Punto de entrada interactivo del cliente BitTorrent.
 *
 * Implementa las dos funciones principales del sistema:
 *   F1 — Compartir archivo (Seeder): hashea el archivo por piezas, anuncia
 *        al tracker y sirve bloques a quien se conecte.
 *   F2 — Descargar archivo (Leecher): consulta al tracker por nombre, recibe
 *        la lista de peers, descarga el archivo por bloques de 16 KB y, al
 *        completar, transiciona automáticamente a seeder.
 *
 * Transparencia de acceso:
 *   La transición leecher → seeder elimina la distinción estática de roles.
 *   Un nodo que termina de descargar un archivo pasa a compartirlo con las
 *   mismas operaciones que un seeder original, sin distinguir si el archivo
 *   es "local" o "descargado". El código de aplicación trata ambos casos
 *   con la misma abstracción (SesionTorrent + LectorBloques).
 *
 * Transparencia de ubicación:
 *   La dirección del tracker se resuelve en ConfiguracionRed sin que el
 *   usuario necesite conocerla a priori (variable de entorno → archivo de
 *   propiedades → consola).
 */
public class AplicacionCliente {

    public static final int LONGITUD_PIEZA_DEFECTO = 256 * 1024;

    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("   uTorrent académico — Cliente P2P");
        System.out.println("=========================================");

        try (Scanner sc = new Scanner(System.in)) {
            ConfiguracionRed config = new ConfiguracionRed(sc);

            String ipTracker     = config.pedirIpTracker();
            int    puertoTracker = config.pedirPuertoTracker();
            int    puertoEscucha = config.pedirPuertoEscuchaLocal();
            String miPeerId      = GeneradorPeerId.generar();
            System.out.println("Mi peerId: " + miPeerId);

            ClienteTracker clienteTracker = new ClienteTracker(ipTracker, puertoTracker);

            System.out.println();
            System.out.println("Selecciona una opción:");
            System.out.println("  1. Compartir archivo (Seeder)");
            System.out.println("  2. Descargar archivo (Leecher)");
            System.out.print("Opción: ");
            String opcion = sc.nextLine().trim();

            switch (opcion) {
                case "1":
                    ejecutarSeeder(sc, config, clienteTracker, miPeerId, puertoEscucha);
                    break;
                case "2":
                    ejecutarLeecher(sc, config, clienteTracker, miPeerId, puertoEscucha);
                    break;
                default:
                    System.err.println("Opción no válida.");
            }
        } catch (Exception e) {
            System.err.println("Error fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------ //
    //  Modo Seeder                                                         //
    // ------------------------------------------------------------------ //

    private static void ejecutarSeeder(Scanner sc, ConfiguracionRed config,
                                       ClienteTracker clienteTracker,
                                       String miPeerId, int puertoEscucha) throws IOException {
        Path archivo = Paths.get(config.pedirRutaArchivo());

        try {
            LimitesArchivo.validarParaSeeding(archivo);
        } catch (IllegalArgumentException e) {
            System.err.println("[Seeder] " + e.getMessage());
            return;
        }

        long longitudTotal = Files.size(archivo);
        int  longitudPieza = LONGITUD_PIEZA_DEFECTO;
        System.out.printf("[Seeder] Hasheando %s (%d bytes en piezas de %d bytes)...%n",
                archivo.getFileName(), longitudTotal, longitudPieza);

        List<byte[]> hashesPiezas = CalculadorHash.hashearArchivoPorPiezas(archivo, longitudPieza);
        byte[] infoHash = CalculadorHash.calcularInfoHash(
                archivo.getFileName().toString(), longitudTotal, longitudPieza, hashesPiezas);

        MetadatosTorrent meta = new MetadatosTorrent(
                archivo.getFileName().toString(),
                longitudTotal, longitudPieza, infoHash, hashesPiezas,
                clienteTracker.toString(), 0);

        System.out.println("[Seeder] infoHash = " + CalculadorHash.aHex(infoHash));
        System.out.println("[Seeder] piezas   = " + hashesPiezas.size());

        RespuestaAnuncio resp = clienteTracker.publicarSeed(meta, miPeerId, puertoEscucha);
        if (resp == null || !resp.isExito()) {
            System.err.println("[Seeder] No pude publicar al tracker. Abortando.");
            return;
        }

        LectorBloques lector = new LectorBloques(archivo);
        iniciarYEsperarSeeder(sc, clienteTracker, miPeerId, puertoEscucha, meta, lector);
    }

    // ------------------------------------------------------------------ //
    //  Modo Leecher con transición automática a Seeder                    //
    // ------------------------------------------------------------------ //

    /**
     * Descarga el archivo y, al completar, pregunta al usuario si desea
     * transicionar a modo seeder para compartirlo con otros peers.
     *
     * Transparencia de acceso: si el usuario elige compartir, el archivo
     * descargado se sirve con la misma abstracción que usa un seeder
     * original (LectorBloques + SesionTorrent SEEDER). La capa P2P no
     * distingue si el archivo es "propio" o "descargado".
     */
    private static void ejecutarLeecher(Scanner sc, ConfiguracionRed config,
                                        ClienteTracker clienteTracker,
                                        String miPeerId, int puertoEscucha) throws IOException {
        String nombreArchivo  = config.pedirNombreArchivo();
        String carpetaDestino = config.pedirCarpetaDestino();

        System.out.println("[Leecher] Consultando al tracker por '" + nombreArchivo + "'...");
        RespuestaAnuncio respuestaConsulta = clienteTracker.consultarPorNombre(
                nombreArchivo, miPeerId);
        if (respuestaConsulta == null || !respuestaConsulta.isExito()) {
            System.err.println("[Leecher] El tracker no conoce el archivo solicitado.");
            return;
        }

        MetadatosTorrent meta = respuestaConsulta.getMetadatos();
        if (meta == null) {
            System.err.println("[Leecher] El tracker no entregó los metadatos del archivo.");
            return;
        }
        System.out.printf("[Leecher] Archivo encontrado: %d bytes en %d piezas de %d bytes%n",
                meta.getLongitudTotal(), meta.totalPiezas(), meta.getLongitudPieza());

        Path destino = Paths.get(carpetaDestino, meta.getNombreArchivo());
        EscritorBloques escritor = new EscritorBloques(destino, meta.getLongitudTotal());
        System.out.println("[Leecher] Espacio reservado en " + destino);

        // ── Fase de descarga ────────────────────────────────────────────
        SesionTorrent sesionLeecher = new SesionTorrent(
                meta, miPeerId, puertoEscucha,
                SesionTorrent.Modo.LEECHER,
                clienteTracker, null, escritor);

        // Shutdown hook defensivo: si el proceso se interrumpe durante la
        // descarga, se envía announce "detenido" al tracker.
        Thread hookDescarga = new Thread(sesionLeecher::detener,
                "shutdown-leecher");
        Runtime.getRuntime().addShutdownHook(hookDescarga);

        sesionLeecher.iniciar();
        descargaConProgreso(sesionLeecher);

        // ── Verificación y cierre limpio de la sesión de descarga ───────
        if (!sesionLeecher.estaCompleto()) {
            // Timeout o interrupción: no transicionamos.
            sesionLeecher.detener();
            quitarHook(hookDescarga);
            return;
        }

        System.out.println("[Leecher] ✓ Descarga completa: " + destino);

        // Detiene la sesión de leecher: envía announce "detenido" al tracker
        // y cierra el EscritorBloques (ya sincronizado a disco por
        // verificarCompletado() antes del announce "completado").
        sesionLeecher.detener();
        quitarHook(hookDescarga);

        // ── Decisión del usuario: compartir o terminar ───────────────────
        //
        // No se fuerza la transición: el usuario controla su nodo.
        // Si elige compartir, el archivo descargado se sirve con la misma
        // abstracción que usa un seeder original, evidenciando transparencia
        // de acceso: la capa P2P no distingue "archivo propio" de "descargado".
        System.out.println();
        System.out.print("¿Deseas compartir el archivo con otros peers? (s/n): ");
        String respuesta = sc.nextLine().trim().toLowerCase();

        if (respuesta.equals("s") || respuesta.equals("si") || respuesta.equals("sí")) {
            System.out.println("[Seeder] ── Activando modo seeder con el archivo descargado ──");
            LectorBloques lector = new LectorBloques(destino);
            iniciarYEsperarSeeder(sc, clienteTracker, miPeerId, puertoEscucha, meta, lector);
        } else {
            System.out.println("[Cliente] Sesión finalizada. El archivo está en: " + destino);
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers compartidos                                                 //
    // ------------------------------------------------------------------ //

    /**
     * Inicia una SesionTorrent en modo SEEDER y espera que el usuario
     * presione ENTER para detenerla. Se reutiliza tanto para seeders
     * originales como para leechers que transicionaron.
     */
    private static void iniciarYEsperarSeeder(Scanner sc,
                                               ClienteTracker clienteTracker,
                                               String miPeerId, int puertoEscucha,
                                               MetadatosTorrent meta,
                                               LectorBloques lector) throws IOException {
        SesionTorrent sesion = new SesionTorrent(
                meta, miPeerId, puertoEscucha,
                SesionTorrent.Modo.SEEDER,
                clienteTracker, lector, null);

        Thread hookSeeder = new Thread(sesion::detener, "shutdown-seeder");
        Runtime.getRuntime().addShutdownHook(hookSeeder);

        sesion.iniciar();
        System.out.println("[Seeder] Compartiendo. Presiona ENTER para detener...");

        sc.nextLine();

        sesion.detener();
        quitarHook(hookSeeder);

        try { lector.close(); } catch (IOException ignorada) {}
    }

    /**
     * Bucle de progreso de la descarga. Imprime el porcentaje en la misma
     * línea y aplica un timeout de 10 minutos.
     */
    private static void descargaConProgreso(SesionTorrent sesion) {
        System.out.println("[Leecher] Descarga en curso...");
        long inicio = System.currentTimeMillis();

        while (!sesion.estaCompleto()) {
            try { Thread.sleep(500); } catch (InterruptedException ie) { break; }

            if (sesion.getGestorPiezas() != null) {
                int hechas = sesion.getGestorPiezas().piezasCompletadas();
                int total  = sesion.getGestorPiezas().totalPiezas();
                System.out.printf("\r[Leecher] Progreso: %d/%d piezas (%.1f%%)",
                        hechas, total, hechas * 100.0 / total);
            }

            if (System.currentTimeMillis() - inicio > 10 * 60 * 1_000L) {
                System.err.println("\n[Leecher] Timeout: 10 minutos sin completar.");
                break;
            }
        }
        System.out.println(); // salto de línea tras el \r
    }

    /**
     * Elimina un shutdown hook registrado previamente. Necesario al hacer
     * la transición leecher → seeder para que la JVM no intente ejecutar
     * el hook de la sesión ya detenida.
     */
    private static void quitarHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignorada) {
            // La JVM ya está en proceso de shutdown; no es necesario hacer nada.
        }
    }
}