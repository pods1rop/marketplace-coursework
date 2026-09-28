package ru.vlsu.marketplace.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.vlsu.marketplace.entities.Product;

import java.math.BigDecimal;

@Data
public class ProductDto {

    @NotBlank(message = "Укажите название товара")
    @Size(max = 200, message = "Название не должно превышать 200 символов")
    private String title;

    @Size(max = 5000, message = "Описание слишком длинное")
    private String description;

    @NotNull(message = "Укажите цену")
    @DecimalMin(value = "1", message = "Цена должна быть не меньше 1 ₽")
    private BigDecimal price;

    private BigDecimal oldPrice;

    @NotNull(message = "Выберите состояние товара")
    private Product.Condition condition;

    @NotNull(message = "Выберите категорию")
    private Integer categoryId;

    private Integer brandId;
    private Product.Gender gender;
    private Product.Season season;

    @Size(max = 50)
    private String color;

    @Size(max = 100)
    private String material;

    @Size(max = 30)
    private String size;
}
