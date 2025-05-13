package tech.dcluttr.dcluttrscrapper.activity;

import io.micrometer.core.instrument.Counter;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import io.temporal.spring.boot.ActivityImpl;
import io.temporal.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import tech.dcluttr.dcluttrscrapper.model.Category;
import tech.dcluttr.dcluttrscrapper.model.DarkStore;
import tech.dcluttr.dcluttrscrapper.model.Product;
import tech.dcluttr.dcluttrscrapper.repository.ClickHouseRepository;
import tech.dcluttr.dcluttrscrapper.service.BlinkitApiService;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import tech.dcluttr.dcluttrscrapper.config.Prometheus;
import tech.dcluttr.dcluttrscrapper.util.ProductParser;

@Component
@RequiredArgsConstructor
@ActivityImpl(taskQueues = "blinkit-scraper-queue",workers = "dcluttr_scrapping_worker")
public class BlinkitActivityImpl implements BlinkitActivity {
    private static final Logger logger = Workflow.getLogger(BlinkitActivityImpl.class);

    private final ClickHouseRepository clickHouseRepository;
    private final BlinkitApiService blinkitApiService;
    private final Prometheus prometheus;
    private final MeterRegistry meterRegistry;
    private final ProductParser productParser;

    private static final DateTimeFormatter CLICKHOUSE_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @Override
    public Map<String, String> setupBlinkitScraping() {
        Counter activityCounter = prometheus.activityCallsWithTags()
                .tag("activity_name", "setupBlinkitScraping")
                .register(meterRegistry);
        activityCounter.increment();
        
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            logger.info("Setting up scraping environment");

            String query = """
                SELECT 1
                """
                ;

            String localTableQuery = """
                SELECT 1
                """
                ;

            // Execute the query to create the local table
            clickHouseRepository.executeQuery(localTableQuery);
            // Execute the query to create the distributed table
            clickHouseRepository.executeQuery(query);
            
            // Send heartbeat
            Activity.getExecutionContext().heartbeat("Complete");
            
            return Collections.singletonMap("status", "setup_complete");
        } catch (Exception e) {
            logger.error("ClickHouse table setup failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to set up ClickHouse table: " + e.getMessage(), e);
        } finally {
            timer.stop(prometheus.activityDurationWithTags()
                    .tag("activity_name", "setupBlinkitScraping")
                    .register(meterRegistry));
        }
    }
    
    @Override
    public List<DarkStore> fetchDarkStores() {
        Counter activityCounter = prometheus.activityCallsWithTags()
                .tag("activity_name", "fetchDarkStores")
                .register(meterRegistry);
        activityCounter.increment();
        
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            logger.info("Fetching dark stores");
            
            String query = """
                SELECT
                    distinct
                        store_id,
                        CAST(latitude AS Float64) as lat,
                        CAST(longitude AS Float64) as lon
                FROM dcluttr.blinkit_base_locality_map_distributed
            """;
            
            List<Map<String, Object>> rows = clickHouseRepository.executeQuery(query);
            List<DarkStore> stores = new ArrayList<>();
            
            for (Map<String, Object> row : rows) {
                DarkStore darkStore = DarkStore.builder()
                    .storeId((String) row.get("store_id"))
                    .lat(((Number) row.get("lat")).doubleValue())
                    .lon(((Number) row.get("lon")).doubleValue())
                    .build();
                
                stores.add(darkStore);
            }
            
            logger.info("Fetched {} dark stores", stores.size());
            Activity.getExecutionContext().heartbeat("Complete");
            return stores;
        } catch (Exception e) {
            logger.error("Error fetching dark stores: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch dark stores: " + e.getMessage(), e);
        } finally {
            timer.stop(prometheus.activityDurationWithTags()
                    .tag("activity_name", "fetchDarkStores")
                    .register(meterRegistry));
        }
    }
    
    @Override
    public List<Category> fetchCategories() {
        Counter activityCounter = prometheus.activityCallsWithTags()
                .tag("activity_name", "fetchCategories")
                .register(meterRegistry);
        activityCounter.increment();
        
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            logger.info("Fetching categories");
            
            String query = """
                SELECT distinct l1_category_id, l2_category_id
                FROM dcluttr.blinkit_base_category_map_distributed
            """;
            
            List<Map<String, Object>> rows = clickHouseRepository.executeQuery(query);
            List<Category> categories = new ArrayList<>();
            
            for (Map<String, Object> row : rows) {
                Category category = Category.builder()
                    .l1CategoryId((String) row.get("l1_category_id"))
                    .l2CategoryId((String) row.get("l2_category_id"))
                    .build();
                
                categories.add(category);
            }
            
            logger.info("Fetched {} categories", categories.size());
            Activity.getExecutionContext().heartbeat("Complete");
            return categories;
        } catch (Exception e) {
            logger.error("Error fetching categories: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch categories: " + e.getMessage(), e);
        } finally {
            timer.stop(prometheus.activityDurationWithTags()
                    .tag("activity_name", "fetchCategories")
                    .register(meterRegistry));
        }
    }
    
    @Override
    public Map<String, Object> fetchCategoryProducts(String l1CategoryId, String l2CategoryId, String storeId, double lat, double lon) {
        Counter activityCounter = prometheus.activityCallsWithTags()
                .tag("activity_name", "fetchCategoryProducts")
                .register(meterRegistry);
        activityCounter.increment();
        
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            logger.info("Fetching products for category: {}/{} from store: {}", l1CategoryId, l2CategoryId, storeId);
            Map<String, Object> result = new HashMap<>();
            int totalProductsScraped = 0;
            int totalPages = 0;
            int maxPages = 50;
            
            for (int page = 1; page <= maxPages; page++) {
                logger.info("Fetching page {} for category {}/{} from store {}", page, l1CategoryId, l2CategoryId, storeId);
                Activity.getExecutionContext().heartbeat("Fetching page " + page);
                
                // Convert string IDs to integers
                int l1Category = Integer.parseInt(l1CategoryId);
                int l2Category = Integer.parseInt(l2CategoryId);
                
                // Fetch products for this page
                Map<String, Object> response = blinkitApiService.fetchProductsWithRetry(lat, lon, l1Category, l2Category, page);
                JSONObject rawProductsArray = new JSONObject(response);
                if(!rawProductsArray.has("objects") || rawProductsArray.getJSONArray("objects").isEmpty()){
                    break;
                }
                // Store API response directly in ClickHouse
                storeApiResponseInClickHouse(l1CategoryId, l2CategoryId, storeId, page, rawProductsArray);

                
                // Send heartbeat after processing each page
                Activity.getExecutionContext().heartbeat("Processed page " + page);
            }
            
            logger.info("Completed fetching {} products across {} pages for category {}/{} from store {}", 
                    totalProductsScraped, totalPages, l1CategoryId, l2CategoryId, storeId);
            
            result.put("status", "complete");
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error fetching category products: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch category products: " + e.getMessage(), e);
        } finally {
            timer.stop(prometheus.activityDurationWithTags()
                    .tag("activity_name", "fetchCategoryProducts")
                    .register(meterRegistry));
        }
    }
    
    
    
    
    /**
     * Store API response directly in ClickHouse
     */
    private void storeApiResponseInClickHouse(String l1CategoryId, String l2CategoryId, String storeId, int page, JSONObject apiResponse) {
        try {

            String insertQuery =
                    "INSERT INTO dcluttr.blinkit_category_scraping_stream_naman_distributed " +
                    "(created_at, l1_category_id, l2_category_id, store_id, rank, variant_id, variant_name, product_id, product_name, selling_price, mrp, discount, in_stock, inventory, is_sponsored, image_url, brand_id, brand, unit, product_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)";

            List<Product> products = productParser.parseApiResponse(l1CategoryId,l2CategoryId,storeId, apiResponse);
            List<Object[]> data = new ArrayList<>(products.size());

            for (Product p : products) {
                Object[] row = new Object[]{
                        CLICKHOUSE_DATE_TIME_FORMATTER.format(p.getCreatedAt()),
                        p.getL1CategoryId(),
                        p.getL2CategoryId(),
                        p.getStoreId(),
                        p.getRank(),
                        p.getVariantId(),
                        p.getVariantName(),
                        p.getProductId(),
                        p.getProductName(),
                        p.getSellingPrice(),
                        p.getMrp(),
                        p.getDiscount(),
                        p.isInStock(),
                        p.getInventory(),
                        p.isSponsored(),
                        p.getImageUrl(),
                        p.getBrandId(),
                        p.getBrand(),
                        p.getUnit(),
                        p.getProductType()
                };
                data.add(row);
            }

            clickHouseRepository.batchInsert(insertQuery,data);
            logger.info("Stored API response for page {} in ClickHouse", page);
        } catch (Exception e) {
            logger.error("Error storing API response in ClickHouse: {}", e.getMessage(), e);
        }
    }
} 