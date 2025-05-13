package tech.dcluttr.dcluttrscrapper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClickHouseService {

    private final JdbcTemplate clickHouseJdbcTemplate;
    
    private static final String TEST_TABLE_NAME = "test_products";
    
    /**
     * Creates a test table if it doesn't exist
     */
    public void createTestTable() {
        log.info("Creating test table if not exists: {}", TEST_TABLE_NAME);
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + TEST_TABLE_NAME + " (" +
                "id UUID," +
                "name String," +
                "price Float64," +
                "stock UInt32," +
                "created_at DateTime," +
                "description String" +
                ") ENGINE = MergeTree() ORDER BY id";
        
        clickHouseJdbcTemplate.execute(createTableSQL);
        log.info("Table {} created or already exists", TEST_TABLE_NAME);
    }
    
    /**
     * Drops the test table if it exists
     */
    public void dropTestTable() {
        log.info("Dropping test table if exists: {}", TEST_TABLE_NAME);
        clickHouseJdbcTemplate.execute("DROP TABLE IF EXISTS " + TEST_TABLE_NAME);
        log.info("Table {} dropped if it existed", TEST_TABLE_NAME);
    }
    
    /**
     * Generates test product data for batch insert
     * @param count Number of products to generate
     * @return List of product data arrays
     */
    private List<Object[]> generateTestProductData(int count) {
        List<Object[]> data = new ArrayList<>(count);
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < count; i++) {
            Object[] product = new Object[]{
                    UUID.randomUUID(),
                    "Product " + i,
                    10.0 + (i * 0.5),
                    100 + i,
                    now,
                    "Description for product " + i
            };
            data.add(product);
        }
        
        return data;
    }
    
    /**
     * Performs a batch insert of test products
     * @param count Number of products to insert
     * @return Number of inserted records
     */
    public int batchInsertTestProducts(int count) {
        log.info("Preparing to insert {} test products", count);
        
        String insertSQL = "INSERT INTO " + TEST_TABLE_NAME + 
                " (id, name, price, stock, created_at, description) VALUES (?, ?, ?, ?, ?, ?)";
        
        List<Object[]> batchData = generateTestProductData(count);
        int[] result = clickHouseJdbcTemplate.batchUpdate(insertSQL, batchData);
        
        int totalInserted = 0;
        for (int rowCount : result) {
            totalInserted += rowCount;
        }
        
        log.info("Successfully inserted {} test products", totalInserted);
        return totalInserted;
    }
    
    /**
     * Counts total records in the test table
     * @return Total record count
     */
    public long countTestProducts() {
        log.info("Counting records in {}", TEST_TABLE_NAME);
        Long count = clickHouseJdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + TEST_TABLE_NAME, Long.class);
        log.info("Found {} records in {}", count, TEST_TABLE_NAME);
        return count != null ? count : 0;
    }
    
    /**
     * Retrieves test products with limit
     * @param limit Maximum number of products to return
     * @return List of product data
     */
    public List<Object[]> getTestProducts(int limit) {
        log.info("Retrieving up to {} products from {}", limit, TEST_TABLE_NAME);
        
        return clickHouseJdbcTemplate.query(
                "SELECT id, name, price, stock, created_at, description FROM " + TEST_TABLE_NAME + 
                        " ORDER BY created_at DESC LIMIT " + limit,
                (rs, rowNum) -> new Object[]{
                        rs.getObject("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getInt("stock"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getString("description")
                }
        );
    }
} 