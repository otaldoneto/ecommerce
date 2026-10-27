package com.ecommerce.api.catalog;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.ecommerce.api.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminCatalogController {

    private final CategoryService categoryService;
    private final ProductService productService;

    public AdminCatalogController(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> listProducts(@ModelAttribute ProductFilter filter,
                                                      @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return productService.search(filter, pageable, false);
    }

    @GetMapping("/products/{id}")
    public ProductResponse getProduct(@PathVariable UUID id) {
        return productService.get(id);
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateProduct(@PathVariable UUID id) {
        productService.deactivate(id);
    }
}
