package utorrent.tracker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import utorrent.modelos.MetadatosTorrent;
import utorrent.modelos.RespuestaAnuncio;
import utorrent.modelos.SolicitudAnuncio;

/**
 * Cliente del Tracker. Encapsula los announce y la consulta por nombre.
 *
 * Política de fallos:
 *   - Connect timeout: 5 s por intento
 *   - Read timeout:   10 s por intento
 *   - Reintentos:     3 con backoff exponencial 5 s / 15 s / 30 s
 *   - Tras 3 fallos:  el llamador debe activar fallback a bootstrap peers
 */
public class ClienteTracker {

    private static final int TIMEOUT_CONEXION_MS = 5_000;
    private static final int TIMEOUT_LECTURA_MS = 10_000;
    private static final long[] BACKOFF_MS = {5_000L, 15_000L, 30_000L};

    private final String ipTracker;
    private final int puertoTracker;

    public ClienteTracker(String ipTracker, int puertoTracker) {
        this.ipTracker = ipTracker;
        this.puertoTracker = puertoTracker;
    }

    public RespuestaAnuncio anunciar(byte[] infoHash, String peerId, int puertoEscucha,
                                     long subido, long descargado, long restante,
                                     String evento) {
        SolicitudAnuncio solicitud = new SolicitudAnuncio(
                infoHash, peerId, puertoEscucha,
                subido, descargado, restante, evento, null);
        return enviarSolicitudConReintentos(solicitud, "anunciar(" + evento + ")");
    }

    /**
     * Announce inicial de un Seeder. Se hace en dos pasos:
     *   1) announce normal "iniciado" para entrar al swarm
     *   2) envío de los metadatos para registro nombre→infoHash
     */
    public RespuestaAnuncio publicarSeed(MetadatosTorrent meta, String peerId,
                                         int puertoEscucha) {
        RespuestaAnuncio r1 = anunciar(meta.getInfoHash(), peerId, puertoEscucha,
                0, meta.getLongitudTotal(), 0, "iniciado");
        if (r1 == null || !r1.isExito()) return r1;

        return enviarMetadatosConReintentos(meta);
    }

    public RespuestaAnuncio consultarPorNombre(String nombreArchivo, String peerId) {
        SolicitudAnuncio consulta = new SolicitudAnuncio(
                new byte[0], peerId, 0,
                0, 0, 0, "consulta", nombreArchivo);
        return enviarSolicitudConReintentos(consulta, "consultarPorNombre(" + nombreArchivo + ")");
    }

    public void desconectar(byte[] infoHash, String peerId, int puertoEscucha) {
        SolicitudAnuncio detenido = new SolicitudAnuncio(
                infoHash, peerId, puertoEscucha,
                0, 0, 0, "detenido", null);
        intentarUnaVez(detenido);
    }

    /* --------------------------- internos --------------------------- */

    private RespuestaAnuncio enviarSolicitudConReintentos(SolicitudAnuncio solicitud, String op) {
        return enviarConReintentos(() -> intentarUnaVez(solicitud), op);
    }

    private RespuestaAnuncio enviarMetadatosConReintentos(MetadatosTorrent meta) {
        return enviarConReintentos(() -> intentarEnviarMetadatos(meta), "publicarMetadatos");
    }

    private interface AccionEnvio { RespuestaAnuncio ejecutar(); }

    private RespuestaAnuncio enviarConReintentos(AccionEnvio accion, String operacion) {
        for (int intento = 0; intento < BACKOFF_MS.length; intento++) {
            try {
                if (intento > 0) {
                    long espera = BACKOFF_MS[intento - 1];
                    System.out.printf("[ClienteTracker] %s: esperando %d ms antes del intento %d%n",
                            operacion, espera, intento + 1);
                    Thread.sleep(espera);
                }
                RespuestaAnuncio resp = accion.ejecutar();
                if (resp != null) return resp;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        System.err.printf("[ClienteTracker] %s: agotados los reintentos.%n", operacion);
        return null;
    }

    private RespuestaAnuncio intentarUnaVez(SolicitudAnuncio solicitud) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipTracker, puertoTracker), TIMEOUT_CONEXION_MS);
            socket.setSoTimeout(TIMEOUT_LECTURA_MS);

            ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

            salida.writeObject(solicitud);
            salida.flush();

            Object respuesta = entrada.readObject();
            if (respuesta instanceof RespuestaAnuncio) {
                return (RespuestaAnuncio) respuesta;
            }
            return null;
        } catch (IOException | ClassNotFoundException e) {
            System.err.printf("[ClienteTracker] Fallo conectando a %s:%d → %s%n",
                    ipTracker, puertoTracker, e.getMessage());
            return null;
        }
    }

    private RespuestaAnuncio intentarEnviarMetadatos(MetadatosTorrent meta) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipTracker, puertoTracker), TIMEOUT_CONEXION_MS);
            socket.setSoTimeout(TIMEOUT_LECTURA_MS);

            ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

            salida.writeObject(meta);
            salida.flush();

            Object respuesta = entrada.readObject();
            if (respuesta instanceof RespuestaAnuncio) {
                return (RespuestaAnuncio) respuesta;
            }
            return null;
        } catch (IOException | ClassNotFoundException e) {
            System.err.printf("[ClienteTracker] Fallo publicando metadatos: %s%n", e.getMessage());
            return null;
        }
    }

    @Override
    public String toString() { return ipTracker + ":" + puertoTracker; }
}