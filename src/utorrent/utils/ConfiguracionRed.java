package utorrent.utils;

import java.util.Scanner;

/**
 * Lee la configuración de red desde consola para evitar hardcoding.
 *
 * Esta clase NO almacena la configuración: simplemente expone métodos para
 * pedirla al usuario al inicio de cada cliente. La intención es que el
 * sistema sea desplegable en cualquier red sin recompilar.
 */
public class ConfiguracionRed {

    private final Scanner sc;

    public ConfiguracionRed(Scanner sc) { this.sc = sc; }

    public String pedirIpTracker() {
        System.out.print("IP del Tracker (ej. 192.168.1.10): ");
        return sc.nextLine().trim();
    }

    public int pedirPuertoTracker() {
        System.out.print("Puerto del Tracker (ej. 6969): ");
        return Integer.parseInt(sc.nextLine().trim());
    }

    public int pedirPuertoEscuchaLocal() {
        System.out.print("Puerto local de escucha P2P (ej. 6881): ");
        return Integer.parseInt(sc.nextLine().trim());
    }

    public String pedirRutaArchivo() {
        System.out.print("Ruta del archivo a compartir: ");
        return sc.nextLine().trim();
    }

    public String pedirNombreArchivo() {
        System.out.print("Nombre del archivo a descargar (ej. video.mp4): ");
        return sc.nextLine().trim();
    }

    public String pedirCarpetaDestino() {
        System.out.print("Carpeta de destino para la descarga: ");
        return sc.nextLine().trim();
    }
}