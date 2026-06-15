package com.juyan.barracks.nutrition.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RandomForestNutritionPredictorTest {

    private RandomForestNutritionPredictor predictor;

    @BeforeEach
    void setUp() {
        predictor = new RandomForestNutritionPredictor(10, 5);
    }

    @Test
    void testInitialization() {
        assertNotNull(predictor);
        assertEquals(0, predictor.getTrees() == null ? 0 : 
                (predictor.getTrees() != null ? predictor.getTrees().size() : 0));
    }

    @Test
    void testEmptyPrediction() {
        double[] features = new double[16];
        double prob = predictor.predictProbability(features);
        assertEquals(0.0, prob, 0.001);
    }

    @Test
    void testTrainAndPredict() {
        List<double[]> features = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            double[] f = new double[16];
            for (int j = 0; j < 16; j++) {
                f[j] = Math.random() * 100;
            }
            features.add(f);
            labels.add(i < 25 ? 0 : 1);
        }

        predictor.train(features, labels);

        assertEquals(10, getTreeCount(predictor));

        double prob = predictor.predictProbability(features.get(0));
        assertTrue(prob >= 0.0 && prob <= 1.0, 
                "预测概率应在0到1之间: " + prob);
    }

    @Test
    void testPredictAll() {
        List<double[]> features = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            double[] f = new double[16];
            for (int j = 0; j < 16; j++) {
                f[j] = i * 10.0 + j * 5.0;
            }
            features.add(f);
            labels.add(i % 2);
        }

        predictor.train(features, labels);

        double[] results = predictor.predictAll(features);
        assertEquals(30, results.length);
        for (double r : results) {
            assertTrue(r >= 0.0 && r <= 1.0);
        }
    }

    @Test
    void testDeterministicPrediction() {
        List<double[]> features = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();

        for (int i = 0; i < 40; i++) {
            double[] f = new double[16];
            for (int j = 0; j < 16; j++) {
                f[j] = i * 3.14 + j * 2.71;
            }
            features.add(f);
            labels.add(i % 2);
        }

        predictor.train(features, labels);

        double[] testFeat = features.get(5);
        double p1 = predictor.predictProbability(testFeat);
        double p2 = predictor.predictProbability(testFeat);
        assertEquals(p1, p2, 0.0001, "相同输入应产生相同输出（确定性）");
    }

    @Test
    void testDifferentNumTrees() {
        RandomForestNutritionPredictor bigForest = 
                new RandomForestNutritionPredictor(50, 8);
        
        List<double[]> features = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            double[] f = new double[16];
            for (int j = 0; j < 16; j++) {
                f[j] = Math.random() * 50;
            }
            features.add(f);
            labels.add(i < 30 ? 0 : 1);
        }

        bigForest.train(features, labels);
        assertEquals(50, getTreeCount(bigForest));
    }

    private int getTreeCount(RandomForestNutritionPredictor p) {
        try {
            java.lang.reflect.Field treesField = 
                    RandomForestNutritionPredictor.class.getDeclaredField("trees");
            treesField.setAccessible(true);
            List<?> trees = (List<?>) treesField.get(p);
            return trees != null ? trees.size() : 0;
        } catch (Exception e) {
            return -1;
        }
    }
}
