package tech.dcluttr.dcluttrscrapper.util;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import tech.dcluttr.dcluttrscrapper.model.Product;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ProductParser {
    public List<Product> parseApiResponse(String originalL1CategoryId, String originalL2CategoryId, String originalStoreId, JSONObject apiResponse) {
        List<Product> parsedProducts = new ArrayList<>();
        try {
            int currentRank = 1;
            for (int i = 0; i < apiResponse.getJSONArray("objects").length(); i++) {
                JSONObject objNode = apiResponse.getJSONArray("objects").optJSONObject(i);
                if (objNode == null) continue;

                JSONObject dataNode = objNode.optJSONObject("data");
                if (dataNode == null) continue;

                JSONObject productDataNode = dataNode.optJSONObject("product");
                if (productDataNode == null) continue;

                JSONObject trackingNode = objNode.optJSONObject("tracking");
                JSONObject widgetDataNode = null;
                if (trackingNode != null) {
                    JSONObject widgetMetaNode = trackingNode.optJSONObject("widget_meta");
                    if (widgetMetaNode != null) {
                        widgetDataNode = widgetMetaNode.optJSONObject("custom_data");
                    }
                }
                
                if (widgetDataNode == null) {
                    widgetDataNode = new JSONObject(); 
                }


                JSONArray variantInfoArray = productDataNode.optJSONArray("variant_info");
                if (variantInfoArray == null) continue;

                String parentProductId = productDataNode.optString("id", null);
                String parentProductName = productDataNode.optString("name", null);
                for (int j = 0; j < variantInfoArray.length(); j++) {
                    JSONObject variantNode = variantInfoArray.optJSONObject(j);
                    if (variantNode == null) continue;

                    JSONArray imagesArray = variantNode.optJSONArray("images");
                    String imageUrl = "";
                    if (imagesArray != null && imagesArray.length() > 0) {
                        imageUrl = imagesArray.optString(0, ""); 
                    }

                    boolean isSponsored = false;
                    JSONArray imageOverlayTagsArray = variantNode.optJSONArray("image_overlay_tags");
                    if (imageOverlayTagsArray != null) {
                        for (int k = 0; k < imageOverlayTagsArray.length(); k++) {
                            if ("ad".equalsIgnoreCase(imageOverlayTagsArray.optString(k, ""))) {
                                isSponsored = true;
                                break;
                            }
                        }
                    }

                    int inventory = variantNode.optInt("inventory", 0);

                    String merchantId = "";

                    String brandName = "";
                    Object brandObj = variantNode.opt("brand"); 
                    if (brandObj instanceof JSONObject) {
                        brandName = ((JSONObject) brandObj).optString("name", "");
                    } else if (brandObj instanceof String) {
                        brandName = (String) brandObj;
                    }
                    Product product = Product.builder()
                            .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                            .l1CategoryId(widgetDataNode.optString("product_list_id", originalL1CategoryId))
                            .l2CategoryId(widgetDataNode.optString("subcategory_id", originalL2CategoryId))
                            .storeId(merchantId.isEmpty() ? originalStoreId : merchantId)
                            .rank(currentRank)
                            .variantId(variantNode.optString("product_id"))
                            .variantName(variantNode.optString("name"))
                            .productId(parentProductId != null ? parentProductId : variantNode.optString("product_id"))
                            .productName(parentProductName != null ? parentProductName : variantNode.optString("name"))
                            .sellingPrice((float) variantNode.optDouble("price", 0.0))
                            .mrp((float) variantNode.optDouble("mrp", 0.0))
                            .discount((float) variantNode.optDouble("discount", 0.0))
                            .inStock(inventory > 0)
                            .inventory(inventory)
                            .isSponsored(isSponsored)
                            .imageUrl(imageUrl)
                            .brandId(variantNode.optJSONObject("attributes") != null ? variantNode.optJSONObject("attributes").optString("brand_id") : "")
                            .brand(brandName)
                            .unit(variantNode.optString("unit"))
                            .productType(variantNode.optString("type"))
                            .build();

                    parsedProducts.add(product);
                    currentRank++;
                }
            }
        } catch (JSONException e) { // Handle JSON parsing errors
            System.err.println("Error parsing API response with org.json: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) { // Catch other potential runtime errors
            System.err.println("Unexpected error during parsing: " + e.getMessage());
            e.printStackTrace();
        }
        return parsedProducts;
    }

}