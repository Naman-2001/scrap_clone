package tech.dcluttr.dcluttrscrapper.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.Map;

@WorkflowInterface
public interface BlinkitScraperWorkflow {
    
    @WorkflowMethod
    Map<String, Object> runBlinkitScraper(int batchIndex);
} 