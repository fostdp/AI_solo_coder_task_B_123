package com.juyan.barracks.supply.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopDeficitItem {
    private String foodCategory;
    private String categoryName;
    private Long totalDeficitCount;
    private BigDecimal avgDeficitRate;
    private BigDecimal maxDeficitRate;
    private Integer affectedBarracksCount;
}
