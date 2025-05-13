package tech.dcluttr.dcluttrscrapper.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.List;
import java.util.Map;
import tech.dcluttr.dcluttrscrapper.model.Category;
import tech.dcluttr.dcluttrscrapper.model.DarkStore;

@ActivityInterface
public interface BlinkitActivity {

    @ActivityMethod
    public Map<String, String> setupBlinkitScraping();
    
    @ActivityMethod
    public List<DarkStore> fetchDarkStores();
    
    @ActivityMethod
    public List<Category> fetchCategories();
    
    @ActivityMethod
    public Map<String, Object> fetchCategoryProducts(String l1CategoryId, String l2CategoryId, String storeId, double lat, double lon);
}
