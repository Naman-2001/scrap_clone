package tech.dcluttr.dcluttrscrapper.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ClickHouseServiceTest {

    @Autowired
    private ClickHouseService clickHouseService;

    @BeforeEach
    void setUp() {
        // Create the test table before each test
        clickHouseService.createTestTable();
    }

    @AfterEach
    void tearDown() {
        // Clean up by dropping the test table after each test
        clickHouseService.dropTestTable();
    }

    @Test
    void testBatchInsertAndRetrieve() {
        // Given
        int batchSize = 50;
        
        // When
        int inserted = clickHouseService.batchInsertTestProducts(batchSize);
        
        // Then
        assertEquals(batchSize, inserted, "All records should be inserted");
        
        // Verify count
        long count = clickHouseService.countTestProducts();
        assertEquals(batchSize, count, "Count should match the number of inserted records");
        
        // Retrieve and verify data
        List<Object[]> products = clickHouseService.getTestProducts(10);
        assertNotNull(products, "Products should not be null");
        assertFalse(products.isEmpty(), "Products should not be empty");
        assertTrue(products.size() <= 10, "Should return at most 10 products");
        
        // Verify first product structure
        Object[] firstProduct = products.get(0);
        assertEquals(6, firstProduct.length, "Product should have 6 fields");
        assertNotNull(firstProduct[0], "Product id should not be null");
        assertTrue(firstProduct[1].toString().startsWith("Product "), "Product name should start with 'Product '");
    }
    
    @Test
    void testLargeBatchInsert() {
        // Given
        int batchSize = 1000;
        
        // When
        int inserted = clickHouseService.batchInsertTestProducts(batchSize);
        
        // Then
        assertEquals(batchSize, inserted, "All records should be inserted");
        
        // Verify count
        long count = clickHouseService.countTestProducts();
        assertEquals(batchSize, count, "Count should match the number of inserted records");
    }
    
    @Test
    void testMultipleBatchInserts() {
        // Given
        int firstBatchSize = 50;
        int secondBatchSize = 75;
        
        // When
        int firstInserted = clickHouseService.batchInsertTestProducts(firstBatchSize);
        int secondInserted = clickHouseService.batchInsertTestProducts(secondBatchSize);
        
        // Then
        assertEquals(firstBatchSize, firstInserted, "All records from first batch should be inserted");
        assertEquals(secondBatchSize, secondInserted, "All records from second batch should be inserted");
        
        // Verify total count
        long count = clickHouseService.countTestProducts();
        assertEquals(firstBatchSize + secondBatchSize, count, 
                "Count should match the total number of inserted records");
    }
} 