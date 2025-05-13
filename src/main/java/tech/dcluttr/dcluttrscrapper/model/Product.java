package tech.dcluttr.dcluttrscrapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private LocalDateTime createdAt;
    private String l1CategoryId;
    private String l2CategoryId;
    private String storeId;
    private int rank;
    private String variantId;
    private String variantName;
    private String productId;
    private String productName;
    private float sellingPrice;
    private float mrp;
    private float discount;
    private boolean inStock;
    private int inventory;
    private boolean isSponsored;
    private String imageUrl;
    private String brandId;
    private String brand;
    private String unit;
    private String productType;
    
    // Additional fields for keyword results
    private String keyword;
    private boolean isBoosted;
    private String sponsoredMetadata;
    private String productBadges;
    private String groupId;
} 