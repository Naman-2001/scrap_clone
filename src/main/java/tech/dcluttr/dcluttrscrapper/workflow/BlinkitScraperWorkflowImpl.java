package tech.dcluttr.dcluttrscrapper.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.ActivityStub;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import tech.dcluttr.dcluttrscrapper.activity.BlinkitActivity;
import tech.dcluttr.dcluttrscrapper.model.Category;
import tech.dcluttr.dcluttrscrapper.model.DarkStore;
import tech.dcluttr.dcluttrscrapper.model.Pair;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@WorkflowImpl(taskQueues = "blinkit-scraper-queue",workers = "dcluttr_scrapping_worker")
public class BlinkitScraperWorkflowImpl implements BlinkitScraperWorkflow {

    private final Logger logger = Workflow.getLogger(BlinkitScraperWorkflowImpl.class);

    // Activity configuration
    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(10))
            .setHeartbeatTimeout(Duration.ofMinutes(3))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setInitialInterval(Duration.ofSeconds(5))
                    .setMaximumInterval(Duration.ofMinutes(1))
                    .build())
            .build();

    // Activity client for invoking activity methods
    private final BlinkitActivity blinkitActivity = Workflow.newActivityStub(
            BlinkitActivity.class, activityOptions);
            
    // Maximum concurrent activities to execute at once
    private static final int MAX_CONCURRENT_ACTIVITIES = 100;

    @Override
    public Map<String, Object> runBlinkitScraper(int batchIndex) {
        Map<String, Object> results = new HashMap<>();
        
        // Log workflow start
        logger.info("Starting Blinkit Scraper Workflow");
        
        // Step 1: Run initial activities in parallel
        logger.info("Running initial activities in parallel");
        
        // Run setupBlinkitScraping, fetchDarkStores, and fetchCategories in parallel
        Promise<Map<String, String>> setupPromise = Async.function(blinkitActivity::setupBlinkitScraping);
        Promise<List<DarkStore>> darkStoresPromise = Async.function(blinkitActivity::fetchDarkStores);
        Promise<List<Category>> categoriesPromise = Async.function(blinkitActivity::fetchCategories);
        
        // Wait for all initial activities to complete
        Map<String, String> setupResult = setupPromise.get();
        List<DarkStore> darkStores = darkStoresPromise.get();
        List<Category> categories = categoriesPromise.get();
        
        // Log completion of initial activities
        logger.info("Initial activities completed. " +
                "Setup status: {}, Dark stores: {}, Categories: {}", 
                setupResult.get("status"), darkStores.size(), categories.size());
        
        // Store initial results
        results.put("setup", setupResult);
        results.put("darkStoresCount", darkStores.size());
        results.put("categoriesCount", categories.size());
        
        // Step 2: Prepare all combinations
        List<Pair<DarkStore, Category>> allCombinations = new ArrayList<>();
        for (DarkStore store : darkStores) {
            for (Category category : categories) {
                allCombinations.add(new Pair<>(store, category));
            }
        }

        int totalCombinations = allCombinations.size();
        int batchSize = 2000;
        int start = batchIndex * batchSize;
        int end = Math.min(start + batchSize, totalCombinations);

        if (start >= totalCombinations) {
            logger.info("All batches processed.");
            return results;
        }

        logger.info("Processing batch {}: combinations {} to {}", batchIndex, start, end);

        List<Promise<Map<String, Object>>> batchPromises = new ArrayList<>();
        for (int i = start; i < end; i++) {
            DarkStore store = allCombinations.get(i).getKey();
            Category category = allCombinations.get(i).getValue();
            // ... call fetchCategoryProducts as before ...
            Promise<Map<String, Object>> promise = Async.function(
                blinkitActivity::fetchCategoryProducts,
                category.getL1CategoryId(), category.getL2CategoryId(),
                store.getStoreId(), store.getLat(), store.getLon()
            );
            batchPromises.add(promise);
        }

        Promise.allOf(batchPromises).get();

        // If there are more batches, continue as new
        if (end < totalCombinations) {
            logger.info("Continuing as new for next batch: {}", batchIndex + 1);
            Workflow.continueAsNew(batchIndex + 1);
        }

        return results;
    }
} 
