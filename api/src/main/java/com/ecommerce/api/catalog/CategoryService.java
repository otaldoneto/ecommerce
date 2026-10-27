package com.ecommerce.api.catalog;

import com.ecommerce.api.shared.error.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        String slug = Slugs.from(name);

        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Category already exists");
        }

        try {
            return CategoryResponse.from(categoryRepository.saveAndFlush(new Category(name, slug)));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Category already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
