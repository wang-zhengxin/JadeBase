package ai.jadebase.rag.application;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class VectorCodec {

    public String encode(double[] vector) {
        return Arrays.stream(vector).mapToObj(Double::toString).collect(Collectors.joining(","));
    }

    public double[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return new double[0];
        String[] values = encoded.split(",");
        double[] vector = new double[values.length];
        for (int i = 0; i < values.length; i++) vector[i] = Double.parseDouble(values[i]);
        return vector;
    }
}
