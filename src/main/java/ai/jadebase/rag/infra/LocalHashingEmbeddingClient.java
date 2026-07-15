package ai.jadebase.rag.infra;

import ai.jadebase.rag.domain.EmbeddingClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class LocalHashingEmbeddingClient implements EmbeddingClient {

    private static final int DIMENSIONS = 384;

    @Override
    public double[] embed(String text) {
        double[] vector = new double[DIMENSIONS];
        String normalized = text.toLowerCase().replaceAll("\\s+", " ");
        int[] codePoints = normalized.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            add(vector, token(codePoints, i, 1), 1.0);
            if (i + 1 < codePoints.length) add(vector, token(codePoints, i, 2), 1.4);
            if (i + 2 < codePoints.length) add(vector, token(codePoints, i, 3), 0.8);
        }
        normalize(vector);
        return vector;
    }

    private long token(int[] values, int start, int length) {
        long hash = 0xcbf29ce484222325L;
        for (int i = start; i < start + length; i++) {
            byte[] bytes = new String(Character.toChars(values[i])).getBytes(StandardCharsets.UTF_8);
            for (byte value : bytes) {
                hash ^= value & 0xff;
                hash *= 0x100000001b3L;
            }
        }
        return hash;
    }

    private void add(double[] vector, long hash, double weight) {
        int index = (int) Math.floorMod(hash, vector.length);
        vector[index] += ((hash >>> 12) & 1) == 0 ? weight : -weight;
    }

    private void normalize(double[] vector) {
        double norm = 0;
        for (double value : vector) norm += value * value;
        norm = Math.sqrt(norm);
        if (norm == 0) return;
        for (int i = 0; i < vector.length; i++) vector[i] /= norm;
    }
}
