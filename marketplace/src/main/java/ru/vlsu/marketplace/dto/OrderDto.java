package ru.vlsu.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderDto {

    @NotBlank(message = "Укажите адрес доставки")
    @Size(max = 500, message = "Адрес слишком длинный")
    private String deliveryAddress;

    @NotBlank(message = "Укажите ФИО получателя")
    @Size(max = 100, message = "ФИО слишком длинное")
    private String contactName;

    @NotBlank(message = "Укажите телефон")
    @Pattern(regexp = "^[+0-9()\\-\\s]{6,20}$", message = "Некорректный номер телефона")
    private String contactPhone;
}
