package utorrent.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Scanner;

/**
 * Centraliza las preguntas de configuración al usuario por consola.
 */
public class ConfiguracionRed {

    private static final String ENV_TRACKER_HOST = "TRACKER_HOST";
    private static final String ENV_TRACKER_PORT = "TRACKER_PORT";
    private static final String ARCHIVO_CONFIG   = "tracker.properties";

    private final Scanner sc;
    private final Properties props;

    public ConfiguracionRed(Scanner sc) {
        this.sc    = sc;
        this.props = cargarPropiedades();
    }

    public String pedirIpTracker() {
        String host = System.getenv(ENV_TRACKER_HOST);
        if (estaDefinido(host)) {
            System.out.println("[Config] Tracker host desde variable de entorno ("
                    + ENV_TRACKER_HOST + "): " + host.trim());
            return host.trim();
        }

        String propHost = props.getProperty("tracker.host");
        if (estaDefinido(propHost)) {
            System.out.println("[Config] Tracker host desde " + ARCHIVO_CONFIG
                    + ": " + propHost.trim());
            return propHost.trim();
        }

        System.out.print("IP del Tracker: ");
        return sc.nextLine().trim();
    }

    /**
     * Devuelve el puerto del tracker, resolviéndolo en el mismo orden de prioridad.
     */
    public int pedirPuertoTracker() {
        String portEnv = System.getenv(ENV_TRACKER_PORT);
        if (estaDefinido(portEnv)) {
            try {
                int p = Integer.parseInt(portEnv.trim());
                System.out.println("[Config] Tracker puerto desde variable de entorno ("
                        + ENV_TRACKER_PORT + "): " + p);
                return p;
            } catch (NumberFormatException e) {
                System.err.println("[Config] " + ENV_TRACKER_PORT
                        + " no es un número válido: " + portEnv);
            }
        }

        String propPort = props.getProperty("tracker.port");
        if (estaDefinido(propPort)) {
            try {
                int p = Integer.parseInt(propPort.trim());
                System.out.println("[Config] Tracker puerto desde " + ARCHIVO_CONFIG + ": " + p);
                return p;
            } catch (NumberFormatException e) {
                System.err.println("[Config] tracker.port en " + ARCHIVO_CONFIG
                        + " no es un número válido: " + propPort);
            }
        }

        System.out.print("Puerto del Tracker: ");
        return Integer.parseInt(sc.nextLine().trim());
    }

    public int pedirPuertoEscuchaLocal() {
        System.out.print("Puerto local de escucha P2P: ");
        return Integer.parseInt(sc.nextLine().trim());
    }

    public String pedirRutaArchivo() {
        System.out.print("Ruta del archivo a compartir: ");
        return sc.nextLine().trim();
    }

    public String pedirNombreArchivo() {
        System.out.print("Nombre del archivo a descargar: ");
        return sc.nextLine().trim();
    }

    public String pedirCarpetaDestino() {
        System.out.print("Carpeta de destino: ");
        return sc.nextLine().trim();
    }

    private Properties cargarPropiedades() {
        Properties p = new Properties();
        Path ruta = Paths.get(ARCHIVO_CONFIG);
        if (Files.exists(ruta)) {
            try (InputStream is = new FileInputStream(ruta.toFile())) {
                p.load(is);
                System.out.println("[Config] Configuración cargada desde " + ARCHIVO_CONFIG);
            } catch (IOException e) {
                System.err.println("[Config] No se pudo leer " + ARCHIVO_CONFIG
                        + ": " + e.getMessage());
            }
        }
        return p;
    }

    private static boolean estaDefinido(String valor) {
        return valor != null && !valor.isBlank();
    }
}