package tech.dcluttr.dcluttrscrapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DarkStore {
    private String storeId;
    private double lat;
    private double lon;
} 