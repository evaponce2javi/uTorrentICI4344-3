package utorrent.utils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Escritura aleatoria de bloques en un archivo de salida. Usado por el Leecher.
 *
 * Al inicio (reservarEspacio) crea un archivo vacío del tamaño total exacto
 * mediante setLength(); esto evita la fragmentación en disco y permite
 * escribir bloques en cualquier orden sin preocuparse por extender el archivo.
 *
 * Igual que LectorBloques, encapsulamos seek+write bajo un ReentrantLock
 * porque RandomAccessFile no es thread-safe.
 */
public class EscritorBloques implements AutoCloseable {

    private final RandomAccessFile raf;
    private final ReentrantLock lock = new ReentrantLock();
    private final Path destino;

    public EscritorBloques(Path destino, long longitudTotal) throws IOException {
        this.destino = destino;
        Files.createDirectories(destino.getParent() != null
                ? destino.getParent() : Path.of("."));
        this.raf = new RandomAccessFile(destino.toFile(), "rw");
        // Reserva el espacio total: el archivo queda creado con el tamaño
        // exacto, lleno de ceros. Permite escrituras aleatorias seguras.
        this.raf.setLength(longitudTotal);
    }

    /** Escribe {@code datos} en el {@code offsetGlobal} del archivo. */
    public void escribir(long offsetGlobal, byte[] datos) throws IOException {
        lock.lock();
        try {
            raf.seek(offsetGlobal);
            raf.write(datos);
        } finally {
            lock.unlock();
        }
    }

    /** Fuerza la sincronización a disco: importante al completar la descarga. */
    public void sincronizar() throws IOException {
        lock.lock();
        try { raf.getFD().sync(); }
        finally { lock.unlock(); }
    }

    public Path getDestino() { return destino; }

    @Override
    public void close() throws IOException {
        lock.lock();
        try { raf.close(); }
        finally { lock.unlock(); }
    }
}