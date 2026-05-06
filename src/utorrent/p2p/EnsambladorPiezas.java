package utorrent.p2p;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import utorrent.modelos.Bloque;
import utorrent.utils.CalculadorHash;
import utorrent.utils.EscritorBloques;

/**
 * Ensambla bloques de 16 KB en piezas completas, verifica el hash SHA-1,
 * y delega la escritura a disco si la verificación pasa.
 *
 * Estructura interna: un mapa concurrente {indicePieza → BufferEnsamblado},
 * donde cada BufferEnsamblado acumula los bloques que ha ido recibiendo
 * de uno o más peers para una misma pieza.
 *
 * Cuando el buffer está completo, ensambla los bytes en orden, calcula el
 * SHA-1 y lo compara con el esperado del torrent. Si es válido, escribe
 * en disco y retorna true; si es inválido, retorna false (el llamador
 * debe re-encolar la pieza con GestorPiezas.reencolar()).
 *
 * Justificación de la concurrencia: distintos peers pueden estar enviando
 * bloques de DISTINTAS piezas en paralelo, y un mismo peer puede partir
 * los bloques de su pieza entre varios sockets. El ConcurrentHashMap
 * más el lock por pieza permiten paralelismo sin riesgo de race conditions
 * en el ensamblado.
 */
public class EnsambladorPiezas {

    private final GestorPiezas gestorPiezas;
    private final EscritorBloques escritor;
    private final int longitudPieza;
    private final ConcurrentHashMap<Integer, BufferEnsamblado> buffers = new ConcurrentHashMap<>();

    public EnsambladorPiezas(GestorPiezas gestor, EscritorBloques escritor) {
        this.gestorPiezas = gestor;
        this.escritor = escritor;
        this.longitudPieza = gestor.getMetadatos().getLongitudPieza();
    }

    /**
     * Recibe un bloque. Si con este bloque se completa la pieza, ejecuta la
     * verificación y la escritura.
     *
     * @return true si la pieza se completó y verificó correctamente,
     *         false si la pieza aún está incompleta o si la verificación falló
     */
    public ResultadoBloque procesarBloque(Bloque bloque) throws IOException {
        int indicePieza = bloque.getIndicePieza();
        int longitudReal = gestorPiezas.longitudDePieza(indicePieza);

        // computeIfAbsent es atómico: dos hilos no pueden crear dos BufferEnsamblado
        // distintos para la misma pieza. Una vez creado, la mutación interna se
        // protege con el lock del BufferEnsamblado.
        BufferEnsamblado buffer = buffers.computeIfAbsent(
                indicePieza, k -> new BufferEnsamblado(longitudReal));

        boolean completo = buffer.agregarBloque(bloque);
        if (!completo) {
            return ResultadoBloque.PIEZA_INCOMPLETA;
        }

        // La pieza está completa: verificamos el hash
        byte[] datosPieza = buffer.consolidar();
        byte[] hashEsperado = gestorPiezas.hashEsperado(indicePieza);

        if (!CalculadorHash.verificar(datosPieza, hashEsperado)) {
            // Falla de valor (Coulouris): contenido bizantino
            buffers.remove(indicePieza);
            return ResultadoBloque.HASH_INVALIDO;
        }

        // Hash válido: escribimos en disco en el offset absoluto correcto
        long offsetGlobal = (long) indicePieza * longitudPieza;
        escritor.escribir(offsetGlobal, datosPieza);
        gestorPiezas.marcarCompletada(indicePieza);
        buffers.remove(indicePieza);

        return ResultadoBloque.PIEZA_VERIFICADA;
    }

    /** Limpia el buffer de una pieza. Llamado al re-encolar tras fallo. */
    public void descartarPieza(int indicePieza) {
        buffers.remove(indicePieza);
    }

    public enum ResultadoBloque {
        PIEZA_INCOMPLETA,
        PIEZA_VERIFICADA,
        HASH_INVALIDO
    }

    /* ------------------ buffer interno por pieza ------------------ */

    private static class BufferEnsamblado {
        private final int longitudPieza;
        private final Map<Integer, byte[]> bloquesPorOffset = new HashMap<>();
        private final ReentrantLock lock = new ReentrantLock();
        private int bytesAcumulados = 0;

        BufferEnsamblado(int longitudPieza) {
            this.longitudPieza = longitudPieza;
        }

        boolean agregarBloque(Bloque bloque) {
            lock.lock();
            try {
                if (bloquesPorOffset.containsKey(bloque.getOffset())) {
                    // Bloque duplicado: lo ignoramos sin contar bytes dos veces
                    return bytesAcumulados >= longitudPieza;
                }
                bloquesPorOffset.put(bloque.getOffset(), bloque.getDatos());
                bytesAcumulados += bloque.getLongitud();
                return bytesAcumulados >= longitudPieza;
            } finally {
                lock.unlock();
            }
        }

        byte[] consolidar() {
            lock.lock();
            try {
                byte[] resultado = new byte[longitudPieza];
                for (Map.Entry<Integer, byte[]> e : bloquesPorOffset.entrySet()) {
                    int offset = e.getKey();
                    byte[] datos = e.getValue();
                    int aCopiar = Math.min(datos.length, longitudPieza - offset);
                    System.arraycopy(datos, 0, resultado, offset, aCopiar);
                }
                return resultado;
            } finally {
                lock.unlock();
            }
        }
    }
}