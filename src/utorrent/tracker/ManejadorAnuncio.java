package utorrent.tracker;

import utorrent.modelos.InfoPar;
import utorrent.modelos.MetadatosTorrent;
import utorrent.modelos.RespuestaAnuncio;
import utorrent.modelos.SolicitudAnuncio;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

/**
 * Maneja una única conexión entrante al Tracker. Se ejecuta en un hilo del
 * ExecutorService del ServidorTracker, cumpliendo el patrón "un hilo por cliente".
 *
 * Tipos de mensajes soportados:
 *   - SolicitudAnuncio con evento "iniciado"/"actualizado"/"completado"/"detenido":
 *     announce estándar; el peer entra/permanece/sale del swarm.
 *   - SolicitudAnuncio con evento "consulta": búsqueda por nombre del enunciado.
 *     El peer NO entra al swarm; solo recibe el infoHash y los metadatos.
 *   - MetadatosTorrent (objeto suelto): segundo mensaje del seeder al hacer
 *     publicarSeed. Registra los metadatos para que futuras consultas por
 *     nombre puedan recuperarlos.
 */
public class ManejadorAnuncio implements Runnable {

    private static final int INTERVALO_ANUNCIO_S = 60;

    private final Socket socket;
    private final RegistroPares registro;

    public ManejadorAnuncio(Socket socket, RegistroPares registro) {
        this.socket = socket;
        this.registro = registro;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(10_000);
        } catch (IOException ignorada) {}

        try (ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream())) {
            salida.flush();
            try (ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())) {

                Object mensaje = entrada.readObject();

                if (mensaje instanceof MetadatosTorrent) {
                    manejarPublicacionMetadatos((MetadatosTorrent) mensaje, salida);
                    return;
                }

                if (!(mensaje instanceof SolicitudAnuncio)) {
                    salida.writeObject(new RespuestaAnuncio(
                            false, "Tipo de mensaje no válido", 0, null, null));
                    return;
                }

                SolicitudAnuncio solicitud = (SolicitudAnuncio) mensaje;
                String ipRemota = socket.getInetAddress().getHostAddress();

                if (esConsultaPorNombre(solicitud)) {
                    manejarConsultaPorNombre(solicitud, salida);
                    return;
                }

                manejarAnuncioEstandar(solicitud, ipRemota, salida);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[Tracker] Error en manejador: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignorada) {}
        }
    }

    private boolean esConsultaPorNombre(SolicitudAnuncio s) {
        return (s.getInfoHash() == null || s.getInfoHash().length == 0)
                && s.getNombreArchivo() != null
                && "consulta".equals(s.getEvento());
    }

    private void manejarConsultaPorNombre(SolicitudAnuncio solicitud,
                                          ObjectOutputStream salida) throws IOException {
        byte[] infoHash = registro.resolverInfoHashPorNombre(solicitud.getNombreArchivo());
        if (infoHash == null) {
            salida.writeObject(new RespuestaAnuncio(
                    false, "Archivo no encontrado: " + solicitud.getNombreArchivo(),
                    0, null, null));
            return;
        }
        MetadatosTorrent meta = registro.obtenerMetadatos(infoHash);
        List<InfoPar> pares = registro.obtenerPares(infoHash, solicitud.getPeerId());
        salida.writeObject(new RespuestaAnuncio(
                true, "OK", INTERVALO_ANUNCIO_S, pares, meta));
        System.out.printf("[Tracker] consulta-nombre archivo=%s pares_devueltos=%d%n",
                solicitud.getNombreArchivo(), pares.size());
    }

    private void manejarAnuncioEstandar(SolicitudAnuncio solicitud, String ipRemota,
                                        ObjectOutputStream salida) throws IOException {
        InfoPar par = new InfoPar(
                solicitud.getPeerId(), ipRemota, solicitud.getPuertoEscucha());

        try {
            if ("detenido".equals(solicitud.getEvento())) {
                registro.desregistrar(solicitud.getInfoHash(), solicitud.getPeerId());
            } else {
                registro.registrar(solicitud.getInfoHash(), par, null);
            }

            List<InfoPar> pares = registro.obtenerPares(
                    solicitud.getInfoHash(), solicitud.getPeerId());

            salida.writeObject(new RespuestaAnuncio(
                    true, "OK", INTERVALO_ANUNCIO_S, pares, null));

            System.out.printf("[Tracker] %-12s peer=%s ip=%s pares_swarm=%d%n",
                    solicitud.getEvento(),
                    solicitud.getPeerId().substring(0, Math.min(8, solicitud.getPeerId().length())),
                    ipRemota, pares.size() + 1);

        } catch (SecurityException se) {
            salida.writeObject(new RespuestaAnuncio(
                    false, se.getMessage(), 0, null, null));
            System.err.println("[Tracker] Anti-Sybil bloqueó: " + se.getMessage());
        }
    }

    private void manejarPublicacionMetadatos(MetadatosTorrent meta,
                                             ObjectOutputStream salida) throws IOException {
        String ipRemota = socket.getInetAddress().getHostAddress();
        registro.registrarMetadatosSolo(meta);

        salida.writeObject(new RespuestaAnuncio(
                true, "Metadatos publicados", INTERVALO_ANUNCIO_S,
                java.util.Collections.emptyList(), meta));

        System.out.printf("[Tracker] metadatos publicados archivo=%s ip=%s%n",
                meta.getNombreArchivo(), ipRemota);
    }
}