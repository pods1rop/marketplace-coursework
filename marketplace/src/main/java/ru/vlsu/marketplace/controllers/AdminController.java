package ru.vlsu.marketplace.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vlsu.marketplace.dto.ChangeRoleRequest;
import ru.vlsu.marketplace.entities.Order;
import ru.vlsu.marketplace.entities.Product;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.entities.Warning;
import ru.vlsu.marketplace.repositories.OrderRepository;
import ru.vlsu.marketplace.services.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final UserModerationService userModerationService;
    private final SellerRequestService sellerRequestService;
    private final ReportService reportService;

    @GetMapping("/users")
    public String userList(@RequestParam(required = false) String search,
                           @RequestParam(required = false) User.Role role,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        Page<User> users = userService.findWithFilters(ModerationController.blankToNull(search), role, PageRequest.of(page, 20));
        model.addAttribute("users", users);
        model.addAttribute("roles", User.Role.values());
        model.addAttribute("warningTypes", Warning.Type.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", users.getTotalPages());
        model.addAttribute("baseUrl", "/admin/users");
        model.addAttribute("isAdminPanel", true);
        return "admin/user_list";
    }

    @PostMapping("/users/change-role")
    @ResponseBody
    public ResponseEntity<String> changeRole(@RequestBody ChangeRoleRequest request, @AuthenticationPrincipal UserDetails me) {
        userModerationService.changeRole(current(me), request.getUserId(), request.getNewRole());
        return ResponseEntity.ok("Роль изменена");
    }

    @PostMapping("/users/{id}/block")
    @ResponseBody
    public ResponseEntity<String> blockUser(@PathVariable Integer id, @AuthenticationPrincipal UserDetails me) {
        userModerationService.setBlocked(current(me), id, true);
        return ResponseEntity.ok("Пользователь заблокирован");
    }

    @PostMapping("/users/{id}/unblock")
    @ResponseBody
    public ResponseEntity<String> unblockUser(@PathVariable Integer id, @AuthenticationPrincipal UserDetails me) {
        userModerationService.setBlocked(current(me), id, false);
        return ResponseEntity.ok("Пользователь разблокирован");
    }

    @PostMapping("/users/{id}/warn")
    @ResponseBody
    public ResponseEntity<String> warnUser(@PathVariable Integer id,
                                           @RequestParam Warning.Type type,
                                           @RequestParam(required = false) String comment,
                                           @AuthenticationPrincipal UserDetails me) {
        userModerationService.warn(current(me), id, type, comment);
        return ResponseEntity.ok("Предупреждение выдано");
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userService.count());
        model.addAttribute("totalProducts", productService.countByStatus(Product.Status.APPROVED));
        model.addAttribute("totalOrders", orderService.count());
        model.addAttribute("pendingProducts", productService.countByStatus(Product.Status.PENDING));
        model.addAttribute("revenue", orderRepository.sumTotalExcludingStatus(Order.Status.CANCELLED));
        model.addAttribute("sellerRequests", sellerRequestService.getPending());
        return "admin/dashboard";
    }

    @PostMapping("/seller-requests/{id}/approve")
    public String approveRequest(@PathVariable Integer id, RedirectAttributes ra) {
        sellerRequestService.decide(id, true);
        ra.addFlashAttribute("flashSuccess", "Заявка одобрена, пользователю выдана роль продавца");
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/seller-requests/{id}/reject")
    public String rejectRequest(@PathVariable Integer id, RedirectAttributes ra) {
        sellerRequestService.decide(id, false);
        ra.addFlashAttribute("flashSuccess", "Заявка отклонена");
        return "redirect:/admin/dashboard";
    }

    /** Статистический отчёт в CSV. */
    @GetMapping("/report")
    public ResponseEntity<byte[]> report() {
        String filename = "marketplace-report-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(reportService.buildCsv());
    }

    private User current(UserDetails me) {
        return userService.findByUsername(me.getUsername()).orElseThrow();
    }
}
