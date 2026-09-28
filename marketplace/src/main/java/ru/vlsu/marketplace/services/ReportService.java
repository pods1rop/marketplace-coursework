package ru.vlsu.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vlsu.marketplace.entities.Order;
import ru.vlsu.marketplace.entities.Product;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.repositories.OrderRepository;
import ru.vlsu.marketplace.repositories.ProductRepository;
import ru.vlsu.marketplace.repositories.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Формирование статистического отчёта администратора в формате CSV
 * (разделитель «;», кодировка UTF-8 с BOM — открывается в Excel без настройки).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public byte[] buildCsv() {
        StringBuilder sb = new StringBuilder();
        String now = ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        sb.append("Статистический отчёт MARKETPLACE;").append(now).append('\n').append('\n');

        sb.append("Раздел;Показатель;Значение\n");
        sb.append("Пользователи;Всего;").append(userRepository.count()).append('\n');
        for (User.Role r : User.Role.values()) {
            sb.append("Пользователи;").append(UserModerationService.roleTitle(r)).append(';')
              .append(userRepository.countByRole(r)).append('\n');
        }

        sb.append("Товары;Всего;").append(productRepository.count()).append('\n');
        for (Product.Status s : Product.Status.values()) {
            sb.append("Товары;").append(productStatusTitle(s)).append(';')
              .append(productRepository.countByStatus(s)).append('\n');
        }

        sb.append("Заказы;Всего;").append(orderRepository.count()).append('\n');
        for (Order.Status s : Order.Status.values()) {
            sb.append("Заказы;").append(orderStatusTitle(s)).append(';')
              .append(orderRepository.countByStatus(s)).append('\n');
        }
        sb.append("Заказы;Оборот без отменённых, руб.;")
          .append(orderRepository.sumTotalExcludingStatus(Order.Status.CANCELLED)).append('\n');

        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] result = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(body, 0, result, bom.length, body.length);
        return result;
    }

    private static String productStatusTitle(Product.Status s) {
        return switch (s) {
            case PENDING -> "На модерации";
            case APPROVED -> "Одобрено";
            case REJECTED -> "Отклонено";
            case REMOVED -> "Снято с продажи";
        };
    }

    private static String orderStatusTitle(Order.Status s) {
        return switch (s) {
            case NEW -> "Новые";
            case CONFIRMED -> "Подтверждённые";
            case IN_DELIVERY -> "В доставке";
            case DELIVERED -> "Доставленные";
            case COMPLETED -> "Завершённые";
            case CANCELLED -> "Отменённые";
        };
    }
}
