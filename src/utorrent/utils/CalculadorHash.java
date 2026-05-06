package utorrent.utils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Operaciones criptográficas SHA-1 del protocolo BitTorrent.
 *
 * El protocolo usa SHA-1 (BEP-3) para:
 *  - Identificar piezas (cada pieza tiene su hash de 20 bytes en .torrent)
 *  - Identificar el torrent completo (infoHash = SHA-1 del diccionario "info")
 *  - Verificar integridad: detectar fallas de valor bizantinas
 *
 * En esta versión académica generamos los hashes en memoria al hacer seeding
 * inicial, sin recurrir a un .torrent en disco.
 */
public class CalculadorHash {

    private static final int TAMANO_HASH = 20; // SHA-1 produce 20 bytes

    private CalculadorHash() { /* utilidad */ }

    /** Calcula SHA-1 de un bloque de datos en memoria. */
    public static byte[] sha1(byte[] datos) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return md.digest(datos);
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 está garantizado en cualquier JVM estándar
            throw new IllegalStateException("SHA-1 no disponible en la JVM", e);
        }
    }

    /**
     * Lee un archivo y produce el hash SHA-1 de cada pieza, en orden.
     *
     * Usa RandomAccessFile (no FileInputStream) para mantener consistencia
     * con la lectura por bloques que hará el Seeder durante las transferencias
     * P2P. La última pieza puede ser más corta si el archivo no es múltiplo
     * exacto de longitudPieza.
     */
    public static List<byte[]> hashearArchivoPorPiezas(Path archivo, int longitudPieza)
            throws IOException {
        List<byte[]> hashes = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(archivo.toFile(), "r")) {
            long longitud = raf.length();
            long offset = 0;
            byte[] buffer = new byte[longitudPieza];

            while (offset < longitud) {
                int aLeer = (int) Math.min(longitudPieza, longitud - offset);
                raf.seek(offset);
                raf.readFully(buffer, 0, aLeer);

                // Si la pieza es más corta que el buffer, hasheamos solo lo leído
                byte[] datosReales = (aLeer == longitudPieza)
                        ? buffer
                        : java.util.Arrays.copyOf(buffer, aLeer);
                hashes.add(sha1(datosReales));

                offset += aLeer;
            }
        }
        return hashes;
    }

    /**
     * Calcula el infoHash del torrent: SHA-1 sobre la concatenación de los
     * campos identificadores. En BitTorrent real es SHA-1 del bencode del
     * diccionario "info"; aquí simplificamos a una concatenación determinista
     * que produce el mismo infoHash en seeder y leecher cuando comparten el
     * mismo archivo.
     */
    public static byte[] calcularInfoHash(String nombreArchivo, long longitudTotal,
                                          int longitudPieza, List<byte[]> hashesPiezas) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(nombreArchivo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            md.update(longBytes(longitudTotal));
            md.update(intBytes(longitudPieza));
            for (byte[] h : hashesPiezas) md.update(h);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 no disponible en la JVM", e);
        }
    }

    /**
     * Verifica si los datos coinciden con el hash esperado.
     * Usa MessageDigest.isEqual() (comparación de tiempo constante) para
     * mitigar ataques de timing.
     */
    public static boolean verificar(byte[] datos, byte[] hashEsperado) {
        if (hashEsperado == null || hashEsperado.length != TAMANO_HASH) return false;
        return MessageDigest.isEqual(sha1(datos), hashEsperado);
    }

    /* ----------------------------- helpers ----------------------------- */

    private static byte[] longBytes(long v) {
        return new byte[]{
                (byte)(v >>> 56), (byte)(v >>> 48), (byte)(v >>> 40), (byte)(v >>> 32),
                (byte)(v >>> 24), (byte)(v >>> 16), (byte)(v >>> 8),  (byte) v
        };
    }

    private static byte[] intBytes(int v) {
        return new byte[]{
                (byte)(v >>> 24), (byte)(v >>> 16), (byte)(v >>> 8), (byte) v
        };
    }

    public static String aHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}