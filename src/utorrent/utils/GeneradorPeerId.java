package utorrent.utils;

import java.security.SecureRandom;

/**
 * Genera peer IDs de 20 bytes según convención Azureus (BEP-20).
 *
 * Formato: "-UT0001-" + 12 caracteres aleatorios alfanuméricos.
 *  - "-UT" identifica el cliente como uTorrent (académico, en este caso)
 *  - "0001" es la versión
 *  - El bloque aleatorio garantiza unicidad incluso si dos peers arrancan
 *    al mismo tiempo en distintas máquinas.
 */
public class GeneradorPeerId {

    private static final String PREFIJO = "-UT0001-";
    private static final String CHARSET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final SecureRandom rng = new SecureRandom();

    /** Genera un peerId de 20 caracteres ASCII (= 20 bytes). */
    public static String generar() {
        StringBuilder sb = new StringBuilder(20);
        sb.append(PREFIJO);
        for (int i = 0; i < 20 - PREFIJO.length(); i++) {
            sb.append(CHARSET.charAt(rng.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }
}