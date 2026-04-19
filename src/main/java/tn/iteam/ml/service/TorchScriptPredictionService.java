package tn.iteam.ml.service;

import ai.djl.MalformedModelException;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.iteam.ml.config.MlTorchScriptProperties;
import tn.iteam.ml.dto.TorchScriptPredictionResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TorchScriptPredictionService {

    private final MlTorchScriptProperties properties;
    private final ObjectMapper objectMapper;

    private List<String> featureOrder;
    private float[] means;
    private float[] stds;
    private Path resolvedModelPath;

    @PostConstruct
    void init() throws IOException {
        Path metadataPath = resolveConfiguredPath(properties.metadataPath());
        resolvedModelPath = resolveConfiguredPath(properties.modelPath());

        JsonNode root = objectMapper.readTree(Files.readString(metadataPath));
        featureOrder = new ArrayList<>();
        for (JsonNode node : root.path("feature_order")) {
            featureOrder.add(node.asText());
        }

        means = new float[root.path("feature_means").size()];
        stds = new float[root.path("feature_stds").size()];
        for (int index = 0; index < means.length; index++) {
            means[index] = (float) root.path("feature_means").get(index).asDouble();
            stds[index] = (float) root.path("feature_stds").get(index).asDouble();
            if (Math.abs(stds[index]) < 1e-8f) {
                stds[index] = 1.0f;
            }
        }
    }

    public TorchScriptPredictionResponse predict(List<Double> features)
            throws ModelException, TranslateException, IOException {
        if (features.size() != means.length) {
            throw new IllegalArgumentException(
                    "Expected " + means.length + " features in this exact order: " + featureOrder
            );
        }

        Criteria<float[], float[]> criteria = Criteria.builder()
                .setTypes(float[].class, float[].class)
                .optModelPath(resolvedModelPath)
                .optTranslator(new TorchScriptTranslator(means, stds))
                .build();

        try (ZooModel<float[], float[]> model = criteria.loadModel();
             Predictor<float[], float[]> predictor = model.newPredictor()) {
            float[] input = new float[features.size()];
            for (int i = 0; i < features.size(); i++) {
                input[i] = features.get(i).floatValue();
            }

            float[] logits = predictor.predict(input);
            double[] probabilities = softmax(logits);
            int bestIndex = 0;
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > probabilities[bestIndex]) {
                    bestIndex = i;
                }
            }

            List<Double> probabilityList = new ArrayList<>();
            for (double probability : probabilities) {
                probabilityList.add(probability);
            }

            return TorchScriptPredictionResponse.builder()
                    .prediction(bestIndex + 1)
                    .probability(probabilities[bestIndex])
                    .probabilities(probabilityList)
                    .featureOrder(featureOrder)
                    .build();
        } catch (MalformedModelException | ModelNotFoundException exception) {
            throw new IOException("Unable to load TorchScript model from " + properties.modelPath(), exception);
        }
    }

    public List<String> getFeatureOrder() {
        return List.copyOf(featureOrder);
    }

    private Path resolveConfiguredPath(String configuredPath) throws IOException {
        Path raw = Paths.get(configuredPath);
        List<Path> candidates = new ArrayList<>();

        if (raw.isAbsolute()) {
            candidates.add(raw.normalize());
        } else {
            Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            candidates.add(raw.toAbsolutePath().normalize());
            candidates.add(userDir.resolve(raw).normalize());
            candidates.add(userDir.resolve("PFECODING").resolve(configuredPath).normalize());
        }

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        throw new IOException("Unable to resolve path '" + configuredPath + "'. Tried: " + candidates);
    }

    private double[] softmax(float[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (float logit : logits) {
            max = Math.max(max, logit);
        }

        double sum = 0.0;
        double[] exp = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = Math.exp(logits[i] - max);
            sum += exp[i];
        }
        for (int i = 0; i < exp.length; i++) {
            exp[i] = exp[i] / sum;
        }
        return exp;
    }

    private static final class TorchScriptTranslator implements Translator<float[], float[]> {

        private final float[] means;
        private final float[] stds;

        private TorchScriptTranslator(float[] means, float[] stds) {
            this.means = means;
            this.stds = stds;
        }

        @Override
        public NDList processInput(TranslatorContext ctx, float[] input) {
            float[] normalized = new float[input.length];
            for (int i = 0; i < input.length; i++) {
                normalized[i] = (input[i] - means[i]) / stds[i];
            }

            NDManager manager = ctx.getNDManager();
            NDArray array = manager.create(normalized, new Shape(1, input.length));
            return new NDList(array);
        }

        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            return list.singletonOrThrow().toFloatArray();
        }

        @Override
        public Batchifier getBatchifier() {
            return null;
        }
    }
}
