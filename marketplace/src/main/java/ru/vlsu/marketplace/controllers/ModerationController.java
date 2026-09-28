package ru.vlsu.marketplace.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vlsu.marketplace.entities.Notification;
import ru.vlsu.marketplace.entities.Product;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.entities.Warning;
import ru.vlsu.marketplace.services.NotificationService;
import ru.vlsu.marketplace.services.ProductService;
import ru.vlsu.marketplace.services.UserModerationService;
import ru.vlsu.marketplace.services.UserService;

@Controller
@RequestMapping("/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final ProductService productService;
    private final UserService userService;
    private final UserModerationService userModerationService;
    private final NotificationService notificationService;

    @GetMapping
    public String moderationPage(Model model) {
        model.addAttribute("pendingProducts", productService.getPendingProducts());
        return "moderation";
    }

    @PostMapping("/approve/{id}")
    public String approve(@PathVariable Integer id, RedirectAttributes ra) {
        Product product = productService.findById(id).orElseThrow();
        product.setStatus(Product.Status.APPROVED);
        product.setRejectReason(null);
        productService.save(product);
        notificationService.notify(product.getSeller(), Notification.Type.MODERATION,
                "Товар «" + product.getTitle() + "» одобрен и опубликован в каталоге", "/product/" + id);
        ra.addFlashAttribute("flashSuccess", "Товар «" + product.getTitle() + "» опубликован");
        return "redirect:/moderation";
    }

    @PostMapping("/reject/{id}")
    public String reject(@PathVariable Integer id, @RequestParam(required = false) String reason,
                         RedirectAttributes ra) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("Укажите причину отклонения");
        }
        Product product = productService.findById(id).orElseThrow();
        product.setStatus(Product.Status.REJECTED);
        product.setRejectReason(reason.trim().length() > 500 ? reason.trim().substring(0, 500) : reason.trim());
        productService.save(product);
        notificationService.notify(product.getSeller(), Notification.Type.MODERATION,
                "Товар «" + product.getTitle() + "» отклонён. Причина: " + product.getRejectReason(),
                "/seller/products");
        ra.addFlashAttribute("flashSuccess", "Товар «" + product.getTitle() + "» отклонён");
        return "redirect:/moderation";
    }

    /** Список пользователей для модератора: блокировка и предупреждения. */
    @GetMapping("/users")
    public String users(@RequestParam(required = false) String search,
                        @RequestParam(required = false) User.Role role,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        Page<User> users = userService.findWithFilters(blankToNull(search), role, PageRequest.of(page, 20));
        model.addAttribute("users", users);
        model.addAttribute("roles", User.Role.values());
        model.addAttribute("warningTypes", Warning.Type.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", users.getTotalPages());
        model.addAttribute("baseUrl", "/moderation/users");
        model.addAttribute("isAdminPanel", false);
        return "admin/user_list";
    }

    @PostMapping("/users/{id}/block")
    @ResponseBody
    public ResponseEntity<String> block(@PathVariable Integer id, @AuthenticationPrincipal UserDetails me) {
        userModerationService.setBlocked(current(me), id, true);
        return ResponseEntity.ok("Пользователь заблокирован");
    }

    @PostMapping("/users/{id}/unblock")
    @ResponseBody
    public ResponseEntity<String> unblock(@PathVariable Integer id, @AuthenticationPrincipal UserDetails me) {
        userModerationService.setBlocked(current(me), id, false);
        return ResponseEntity.ok("Пользователь разблокирован");
    }

    @PostMapping("/users/{id}/warn")
    @ResponseBody
    public ResponseEntity<String> warn(@PathVariable Integer id,
                                       @RequestParam Warning.Type type,
                                       @RequestParam(required = false) String comment,
                                       @AuthenticationPrincipal UserDetails me) {
        userModerationService.warn(current(me), id, type, comment);
        return ResponseEntity.ok("Предупреждение выдано");
    }

    private User current(UserDetails me) {
        return userService.findByUsername(me.getUsername()).orElseThrow();
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
