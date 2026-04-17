package com.haeni.carrot.settle.infrastructure.product;

import com.haeni.carrot.settle.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {}