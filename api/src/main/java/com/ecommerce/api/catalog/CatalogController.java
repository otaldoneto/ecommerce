package com.ecommerce.api.catalog;

import com.ecommerce.api.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CatalogController {

    private final CategoryService categoryService;
    private final ProductService productService;

    public CatalogController(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() {
        return categoryService.list();
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> listProducts(@ModelAttribute ProductFilter filter,
                                                      @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return productService.search(filter, pageable, true);
    }

    @GetMapping("/products/{slug}")
    public ProductResponse getProduct(@PathVariable String slug) {
        return productService.getActiveBySlug(slug);
    }
}
