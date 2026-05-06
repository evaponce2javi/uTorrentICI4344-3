package utorrent.p2p;

import java.util.HashSet;
import java.util.Set;

/**
 * Encapsula la conversión entre {@code byte[]} bitfield y {@code Set<Integer>}
 * de índices de piezas presentes.
 *
 * El estándar BEP-3 exige bit-packing MSB-first: el bit más significativo del
 * primer byte representa la pieza 0. Esto NO coincide con el orden natural
 * en muchos lenguajes (que usan LSB-first), por lo que este código aísla la
 * complejidad para que ningún otro componente tenga que pensar en bits.
 *
 * Si totalPiezas no es múltiplo de 8, los bits sobrantes del último byte
 * deben ser 0 según el estándar; esta clase respeta esa convención.
 */
public class GestorBitfield {

    /** Convierte un bitfield binario en el conjunto de índices de piezas presentes. */
    public Set<Integer> parsear(byte[] bitfield, int totalPiezas) {
        Set<Integer> resultado = new HashSet<>();
        for (int i = 0; i < totalPiezas; i++) {
            int byteIdx = i / 8;
            int bitIdx  = 7 - (i % 8);
            if (byteIdx < bitfield.length && ((bitfield[byteIdx] >> bitIdx) & 1) == 1) {
                resultado.add(i);
            }
        }
        return resultado;
    }

    /**
     * Construye el bitfield binario a partir de los estados del GestorPiezas.
     * El tamaño del arreglo es ceil(totalPiezas / 8).
     */
    public byte[] construir(GestorPiezas gestor) {
        int total = gestor.totalPiezas();
        byte[] bf = new byte[(total + 7) / 8];
        for (int i = 0; i < total; i++) {
            if (gestor.tienePieza(i)) {
                int byteIdx = i / 8;
                int bitIdx  = 7 - (i % 8);
                // El cast a (byte) es necesario porque el resultado del OR
                // sobre bytes en Java se promueve a int.
                bf[byteIdx] = (byte) (bf[byteIdx] | (1 << bitIdx));
            }
        }
        return bf;
    }
}