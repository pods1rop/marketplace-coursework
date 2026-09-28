package ru.vlsu.marketplace.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vlsu.marketplace.dto.OrderDto;
import ru.vlsu.marketplace.entities.Order;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.services.CartService;
import ru.vlsu.marketplace.services.OrderService;
import ru.vlsu.marketplace.services.UserService;

@Controller
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;
    private final UserService userService;

    @GetMapping("/orders/checkout")
    public String checkoutPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        model.addAttribute("cartItems", cartService.getCartItems(user.getId()));
        model.addAttribute("total", cartService.getCartTotal(user.getId()));
        model.addAttribute("orderDto", new OrderDto());
        return "checkout";
    }

    @PostMapping("/orders/checkout")
    public String checkout(@Valid @ModelAttribute OrderDto dto, BindingResult binding,
                           @AuthenticationPrincipal UserDetails userDetails, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("flashError", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/orders/checkout";
        }
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        Order order = orderService.createOrder(user, dto);
        ra.addFlashAttribute("flashSuccess", "Заказ №" + order.getId() + " оформлен");
        return "redirect:/orders";
    }

    @GetMapping("/orders")
    public String orders(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        model.addAttribute("orders", orderService.getOrdersByBuyer(user.getId()));
        return "orders";
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancelOrder(@PathVariable Integer id, @AuthenticationPrincipal UserDetails userDetails,
                              RedirectAttributes ra) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        orderService.cancelByBuyer(user, id);
        ra.addFlashAttribute("flashSuccess", "Заказ №" + id + " отменён");
        return "redirect:/orders";
    }
}
