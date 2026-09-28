package ru.vlsu.marketplace.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_status", columnList = "status"),
    @Index(name = "idx_product_status_created", columnList = "status, created_at"),
    @Index(name = "idx_product_category", columnList = "fk_category"),
    @Index(name = "idx_product_brand", columnList = "fk_brand"),
    @Index(name = "idx_product_seller", columnList = "fk_seller"),
    @Index(name = "idx_product_price", columnList = "price")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    public enum Condition { NEW, USED, VINTAGE }
    public enum Status { PENDING, APPROVED, REJECTED, REMOVED }
    public enum Gender { MALE, FEMALE, UNISEX }
    public enum Season { SUMMER, WINTER, DEMI, UNIVERSAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "old_price", precision = 10, scale = 2)
    private BigDecimal oldPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_condition", nullable = false)
    private Condition condition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Lob
    @Column(name = "image_data", columnDefinition = "LONGBLOB")
    private byte[] imageData;

    @ManyToOne
    @JoinColumn(name = "fk_category")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "fk_brand")
    private Brand brand;

    @ManyToOne
    @JoinColumn(name = "fk_seller", nullable = false)
    private User seller;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private Season season;

    @Column(length = 50)
    private String color;

    @Column(length = 100)
    private String material;

    @Column(length = 30)
    private String size;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<Review> reviews;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images;
}
