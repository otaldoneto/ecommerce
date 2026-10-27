package com.ecommerce.api.catalog;

import com.ecommerce.api.shared.error.BadRequestException;
import com.ecommerce.api.shared.error.ConflictException;
import com.ecommerce.api.shared.error.ResourceNotFoundException;
import com.ecommerce.api.shared.web.PageResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "price", "createdAt");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = findCategory(request.categoryId());
        String name = request.name().trim();
        String slug = Slugs.from(name);

        if (productRepository.existsBySlug(slug)) {
            throw new ConflictException("Product already exists");
        }

        Product product = new Product(name, slug, request.description().trim(), request.price(), request.stock(), category);

        try {
            return ProductResponse.from(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Product already exists");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductFilter filter, Pageable pageable, boolean onlyActive) {
        validateSort(pageable.getSort());
        return PageResponse.from(productRepository
                .findAll(ProductSpecifications.matching(filter, onlyActive), pageable)
                .map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse getActiveBySlug(String slug) {
        return productRepository.findBySlugAndActiveTrue(slug)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(findProduct(id));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = findProduct(id);
        Category category = findCategory(request.categoryId());

        product.update(request.name().trim(), request.description().trim(), request.price(), request.stock(), category);
        return ProductResponse.from(product);
    }

    @Transactional
    public void deactivate(UUID id) {
        findProduct(id).deactivate();
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by " + order.getProperty());
            }
        }
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }
}
