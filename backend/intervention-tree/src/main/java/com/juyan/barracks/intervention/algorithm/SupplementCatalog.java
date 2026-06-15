package com.juyan.barracks.intervention.algorithm;

import lombok.Data;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SupplementCatalog {

    @Getter
    public enum SupplementType {
        DRIED_DATE("干枣", "维生素补充", new NutrientContent(50.0, 0, 0, 2.5, 0),
                new BigDecimal("15"), 30, Unit.GRAM),
        JERKY("肉干", "蛋白质补充", new NutrientContent(0, 30.0, 10.0, 3.0, 0),
                new BigDecimal("40"), 50, Unit.GRAM),
        FRESH_VEG("新鲜蔬菜", "维生素补充", new NutrientContent(30.0, 2.0, 0.3, 1.5, 0),
                new BigDecimal("8"), 200, Unit.GRAM),
        SOY("豆制品", "蛋白质补充", new NutrientContent(0, 15.0, 8.0, 2.0, 0),
                new BigDecimal("5"), 100, Unit.GRAM),
        FISH_LIVER_OIL("鱼肝油", "维生素A/D补充", new NutrientContent(0, 0, 0, 0, 1000.0),
                new BigDecimal("20"), 5, Unit.MILLILITER),
        NUTS("坚果", "脂肪蛋白补充", new NutrientContent(2.0, 20.0, 45.0, 3.0, 0),
                new BigDecimal("35"), 30, Unit.GRAM);

        private final String displayName;
        private final String category;
        private final NutrientContent nutrientContent;
        private final BigDecimal costPerUnit;
        private final int dailyDosageGrams;
        private final Unit unit;

        SupplementType(String displayName, String category, NutrientContent nutrientContent,
                       BigDecimal costPerUnit, int dailyDosageGrams, Unit unit) {
            this.displayName = displayName;
            this.category = category;
            this.nutrientContent = nutrientContent;
            this.costPerUnit = costPerUnit;
            this.dailyDosageGrams = dailyDosageGrams;
            this.unit = unit;
        }

        public BigDecimal calculateDailyCost() {
            BigDecimal dailyAmount = new BigDecimal(dailyDosageGrams);
            if (unit == Unit.GRAM) {
                BigDecimal gramsPerJin = new BigDecimal("500");
                return costPerUnit.multiply(dailyAmount)
                        .divide(gramsPerJin, 4, RoundingMode.HALF_UP);
            } else if (unit == Unit.MILLILITER) {
                BigDecimal mlPerBottle = new BigDecimal("100");
                return costPerUnit.multiply(dailyAmount)
                        .divide(mlPerBottle, 4, RoundingMode.HALF_UP);
            }
            return BigDecimal.ZERO;
        }

        public BigDecimal calculateTotalCost(int durationDays) {
            return calculateDailyCost().multiply(new BigDecimal(durationDays));
        }
    }

    public enum Unit {
        GRAM("g"),
        MILLILITER("ml"),
        BOTTLE("瓶"),
        JIN("斤");

        private final String symbol;

        Unit(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    @Data
    public static class NutrientContent implements Serializable {
        private static final long serialVersionUID = 1L;

        private double vitaminCPer100g;
        private double proteinPer100g;
        private double fatPer100g;
        private double ironPer100g;
        private double vitaminADPer100g;

        public NutrientContent() {
        }

        public NutrientContent(double vitaminCPer100g, double proteinPer100g, double fatPer100g,
                               double ironPer100g, double vitaminADPer100g) {
            this.vitaminCPer100g = vitaminCPer100g;
            this.proteinPer100g = proteinPer100g;
            this.fatPer100g = fatPer100g;
            this.ironPer100g = ironPer100g;
            this.vitaminADPer100g = vitaminADPer100g;
        }
    }

    @Data
    public static class Supplement implements Serializable {
        private static final long serialVersionUID = 1L;

        private SupplementType type;
        private String name;
        private String category;
        private NutrientContent nutrientContent;
        private BigDecimal costPerUnit;
        private int dailyDosage;
        private Unit unit;
        private String dosageDescription;
        private BigDecimal dailyCost;
        private List<String> matchedRules;

        public Supplement() {
            this.matchedRules = new ArrayList<>();
        }

        public Supplement(SupplementType type) {
            this.type = type;
            this.name = type.getDisplayName();
            this.category = type.getCategory();
            this.nutrientContent = type.getNutrientContent();
            this.costPerUnit = type.getCostPerUnit();
            this.dailyDosage = type.getDailyDosageGrams();
            this.unit = type.getUnit();
            this.dailyCost = type.calculateDailyCost();
            this.matchedRules = new ArrayList<>();
            this.dosageDescription = String.format("每日%d%s", dailyDosage, unit.getSymbol());
        }

        public void addMatchedRule(String rule) {
            if (!matchedRules.contains(rule)) {
                matchedRules.add(rule);
            }
        }

        public BigDecimal calculateTotalCost(int durationDays) {
            return dailyCost.multiply(new BigDecimal(durationDays))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    public static List<Supplement> getAllSupplements() {
        List<Supplement> supplements = new ArrayList<>();
        for (SupplementType type : SupplementType.values()) {
            supplements.add(new Supplement(type));
        }
        return Collections.unmodifiableList(supplements);
    }

    public static Supplement getSupplement(SupplementType type) {
        return new Supplement(type);
    }

    public static List<Supplement> getVitaminCSupplements() {
        return Arrays.asList(
                new Supplement(SupplementType.DRIED_DATE),
                new Supplement(SupplementType.FRESH_VEG)
        );
    }

    public static List<Supplement> getProteinSupplements() {
        return Arrays.asList(
                new Supplement(SupplementType.JERKY),
                new Supplement(SupplementType.SOY)
        );
    }

    public static List<Supplement> getFatSupplements() {
        return Collections.singletonList(
                new Supplement(SupplementType.NUTS)
        );
    }

    public static List<Supplement> getElderlySupplements() {
        return Collections.singletonList(
                new Supplement(SupplementType.FISH_LIVER_OIL)
        );
    }
}
