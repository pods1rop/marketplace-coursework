package ru.vlsu.marketplace.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.services.FavoriteService;
import ru.vlsu.marketplace.services.UserService;

@Controller
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final UserService userService;

    /** Отдельной страницы нет — избранное показывается во вкладке профиля. */
    @GetMapping
    public String favoritesPage() {
        return "redirect:/profile?tab=favorites";
    }

    @PostMapping("/add/{productId}")
    public String add(@PathVariable Integer productId, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        favoriteService.addToFavorites(user, productId);
        return "redirect:/product/" + productId;
    }

    @PostMapping("/remove/{productId}")
    public String remove(@PathVariable Integer productId, @AuthenticationPrincipal UserDetails userDetails,
                         @RequestParam(defaultValue = "product") String from) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        favoriteService.removeFromFavorites(user.getId(), productId);
        return "profile".equals(from) ? "redirect:/profile" : "redirect:/product/" + productId;
    }
}
