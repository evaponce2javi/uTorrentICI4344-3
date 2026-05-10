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
 *
 * Transparencia de ubicación — orden de resolución para IP y puerto del tracker:
 *   1. Variables de entorno  TRACKER_HOST / TRACKER_PORT
 *   2. Archivo tracker.properties en el directorio de trabajo
 *   3. Entrada interactiva por consola (fallback)
 *
 * Gracias a este mecanismo, el usuario (o el entorno de despliegue) no necesita
 * conocer ni escribir la dirección del tracker para ejecutar el cliente; basta
 * con que esté declarada en el entorno o en el archivo de configuración.
 * Esto evidencia transparencia de ubicación: el recurso (tracker) se descubre
 * sin que el código de aplicación codifique su dirección física.
 *
 * Formato de tracker.properties:
 *   tracker.host=192.168.1.10
 *   tracker.port=6969
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

    // ------------------------------------------------------------------ //
    //  Resolución de host y puerto del tracker                            //
    // ------------------------------------------------------------------ //

    /**
     * Devuelve la IP del tracker, resolviéndola en orden de prioridad:
     * variable de entorno → archivo de propiedades → consola.
     */
    public String pedirIpTracker() {
        // 1. Variable de entorno
        String host = System.getenv(ENV_TRACKER_HOST);
        if (estaDefinido(host)) {
            System.out.println("[Config] Tracker host desde variable de entorno ("
                    + ENV_TRACKER_HOST + "): " + host.trim());
            return host.trim();
        }

        // 2. Archivo tracker.properties
        String propHost = props.getProperty("tracker.host");
        if (estaDefinido(propHost)) {
            System.out.println("[Config] Tracker host desde " + ARCHIVO_CONFIG
                    + ": " + propHost.trim());
            return propHost.trim();
        }

        // 3. Fallback: consola
        System.out.print("IP del Tracker: ");
        return sc.nextLine().trim();
    }

    /**
     * Devuelve el puerto del tracker, resolviéndolo en el mismo orden de prioridad.
     */
    public int pedirPuertoTracker() {
        // 1. Variable de entorno
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

        // 2. Archivo tracker.properties
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

        // 3. Fallback: consola
        System.out.print("Puerto del Tracker: ");
        return Integer.parseInt(sc.nextLine().trim());
    }

    // ------------------------------------------------------------------ //
    //  Resto de parámetros (solo consola; no aplica transparencia aquí)  //
    // ------------------------------------------------------------------ //

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

    // ------------------------------------------------------------------ //
    //  Internos                                                            //
    // ------------------------------------------------------------------ //

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