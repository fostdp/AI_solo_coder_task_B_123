package com.juyan.barracks.nutrition.algorithm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
public class RandomForestNutritionPredictor implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<DecisionTree> trees;
    private final int numTrees;
    private final int maxDepth;
    private final Random random;

    public RandomForestNutritionPredictor(int numTrees, int maxDepth) {
        this.numTrees = numTrees;
        this.maxDepth = maxDepth;
        this.trees = new ArrayList<>();
        this.random = new Random(42);
    }

    public void train(List<double[]> features, List<Integer> labels) {
        log.info("开始训练随机森林模型: numTrees={}, maxDepth={}, samples={}", numTrees, maxDepth, features.size());
        trees.clear();

        for (int i = 0; i < numTrees; i++) {
            List<double[]> bootstrapFeatures = new ArrayList<>();
            List<Integer> bootstrapLabels = new ArrayList<>();

            for (int j = 0; j < features.size(); j++) {
                int index = random.nextInt(features.size());
                bootstrapFeatures.add(features.get(index));
                bootstrapLabels.add(labels.get(index));
            }

            DecisionTree tree = new DecisionTree(maxDepth, random, features.get(0).length);
            tree.build(bootstrapFeatures, bootstrapLabels);
            trees.add(tree);

            if (i % 10 == 0) {
                log.debug("已训练 {}/{} 棵决策树", i + 1, numTrees);
            }
        }

        log.info("随机森林模型训练完成");
    }

    public double predictProbability(double[] features) {
        if (trees.isEmpty()) {
            return 0.0;
        }

        int positiveVotes = 0;
        for (DecisionTree tree : trees) {
            if (tree.predict(features) == 1) {
                positiveVotes++;
            }
        }

        return (double) positiveVotes / trees.size();
    }

    public double[] predictAll(List<double[]> featuresList) {
        double[] results = new double[featuresList.size()];
        for (int i = 0; i < featuresList.size(); i++) {
            results[i] = predictProbability(featuresList.get(i));
        }
        return results;
    }

    @Data
    private static class DecisionTree implements Serializable {
        private static final long serialVersionUID = 1L;

        private Node root;
        private final int maxDepth;
        private final Random random;
        private final int numFeatures;

        public DecisionTree(int maxDepth, Random random, int numFeatures) {
            this.maxDepth = maxDepth;
            this.random = random;
            this.numFeatures = numFeatures;
        }

        public void build(List<double[]> features, List<Integer> labels) {
            this.root = buildTree(features, labels, 0);
        }

        private Node buildTree(List<double[]> features, List<Integer> labels, int depth) {
            if (depth >= maxDepth || features.isEmpty() || isPure(labels)) {
                Node leaf = new Node();
                leaf.isLeaf = true;
                leaf.prediction = calculateMajority(labels);
                return leaf;
            }

            int m = (int) Math.sqrt(numFeatures);
            int[] featureIndices = selectRandomFeatures(m);

            Split bestSplit = findBestSplit(features, labels, featureIndices);

            if (bestSplit == null) {
                Node leaf = new Node();
                leaf.isLeaf = true;
                leaf.prediction = calculateMajority(labels);
                return leaf;
            }

            List<double[]> leftFeatures = new ArrayList<>();
            List<Integer> leftLabels = new ArrayList<>();
            List<double[]> rightFeatures = new ArrayList<>();
            List<Integer> rightLabels = new ArrayList<>();

            for (int i = 0; i < features.size(); i++) {
                if (features.get(i)[bestSplit.featureIndex] <= bestSplit.threshold) {
                    leftFeatures.add(features.get(i));
                    leftLabels.add(labels.get(i));
                } else {
                    rightFeatures.add(features.get(i));
                    rightLabels.add(labels.get(i));
                }
            }

            Node node = new Node();
            node.featureIndex = bestSplit.featureIndex;
            node.threshold = bestSplit.threshold;
            node.left = buildTree(leftFeatures, leftLabels, depth + 1);
            node.right = buildTree(rightFeatures, rightLabels, depth + 1);

            return node;
        }

        private int[] selectRandomFeatures(int m) {
            int[] indices = new int[numFeatures];
            for (int i = 0; i < numFeatures; i++) {
                indices[i] = i;
            }

            for (int i = numFeatures - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int temp = indices[i];
                indices[i] = indices[j];
                indices[j] = temp;
            }

            int[] result = new int[Math.min(m, numFeatures)];
            System.arraycopy(indices, 0, result, 0, result.length);
            return result;
        }

        private Split findBestSplit(List<double[]> features, List<Integer> labels, int[] featureIndices) {
            double bestGini = Double.MAX_VALUE;
            Split bestSplit = null;

            for (int featureIndex : featureIndices) {
                double minVal = Double.MAX_VALUE;
                double maxVal = -Double.MAX_VALUE;

                for (double[] sample : features) {
                    minVal = Math.min(minVal, sample[featureIndex]);
                    maxVal = Math.max(maxVal, sample[featureIndex]);
                }

                if (minVal == maxVal) continue;

                int numThresholds = 10;
                for (int t = 1; t < numThresholds; t++) {
                    double threshold = minVal + (maxVal - minVal) * t / numThresholds;
                    double gini = calculateSplitGini(features, labels, featureIndex, threshold);

                    if (gini < bestGini) {
                        bestGini = gini;
                        bestSplit = new Split(featureIndex, threshold);
                    }
                }
            }

            return bestSplit;
        }

        private double calculateSplitGini(List<double[]> features, List<Integer> labels,
                                          int featureIndex, double threshold) {
            int leftSize = 0, leftPos = 0, leftNeg = 0;
            int rightSize = 0, rightPos = 0, rightNeg = 0;

            for (int i = 0; i < features.size(); i++) {
                if (features.get(i)[featureIndex] <= threshold) {
                    leftSize++;
                    if (labels.get(i) == 1) leftPos++;
                    else leftNeg++;
                } else {
                    rightSize++;
                    if (labels.get(i) == 1) rightPos++;
                    else rightNeg++;
                }
            }

            if (leftSize == 0 || rightSize == 0) return Double.MAX_VALUE;

            double leftGini = gini(leftPos, leftNeg, leftSize);
            double rightGini = gini(rightPos, rightNeg, rightSize);

            return (leftSize * leftGini + rightSize * rightGini) / features.size();
        }

        private double gini(int pos, int neg, int total) {
            if (total == 0) return 0;
            double pPos = (double) pos / total;
            double pNeg = (double) neg / total;
            return 1.0 - pPos * pPos - pNeg * pNeg;
        }

        private boolean isPure(List<Integer> labels) {
            if (labels.isEmpty()) return true;
            int first = labels.get(0);
            for (int label : labels) {
                if (label != first) return false;
            }
            return true;
        }

        private int calculateMajority(List<Integer> labels) {
            if (labels.isEmpty()) return 0;
            int pos = 0, neg = 0;
            for (int label : labels) {
                if (label == 1) pos++;
                else neg++;
            }
            return pos >= neg ? 1 : 0;
        }

        public int predict(double[] features) {
            return predictNode(root, features);
        }

        private int predictNode(Node node, double[] features) {
            if (node.isLeaf) {
                return node.prediction;
            }
            if (features[node.featureIndex] <= node.threshold) {
                return predictNode(node.left, features);
            } else {
                return predictNode(node.right, features);
            }
        }
    }

    @Data
    private static class Node implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean isLeaf;
        private int prediction;
        private int featureIndex;
        private double threshold;
        private Node left;
        private Node right;
    }

    @Data
    @lombok.AllArgsConstructor
    private static class Split {
        private int featureIndex;
        private double threshold;
    }
}
