package ru.vlsu.marketplace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewDto {

    @NotNull(message = "Поставьте оценку")
    @Min(value = 1, message = "Оценка от 1 до 5")
    @Max(value = 5, message = "Оценка от 1 до 5")
    private Byte rating;

    @Size(max = 2000, message = "Отзыв не должен превышать 2000 символов")
    private String text;
}
