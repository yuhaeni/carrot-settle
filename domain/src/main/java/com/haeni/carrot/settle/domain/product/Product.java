package com.haeni.carrot.settle.domain.product;

import com.haeni.carrot.settle.domain.common.BaseEntity;
import com.haeni.carrot.settle.domain.seller.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_id", nullable = false)
  private Seller seller;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Column(nullable = false)
  private int stock;

  protected Product() {}

  public Product(Seller seller, String name, BigDecimal price, int stock) {
    this.seller = seller;
    this.name = name;
    this.price = price;
    this.stock = stock;
  }

  public Long getId() {
    return id;
  }

  public Seller getSeller() {
    return seller;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public int getStock() {
    return stock;
  }
}