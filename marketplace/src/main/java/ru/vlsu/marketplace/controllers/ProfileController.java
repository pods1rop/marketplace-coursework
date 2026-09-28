package ru.vlsu.marketplace.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.services.*;

import java.io.IOException;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final ReviewService reviewService;
    private final FavoriteService favoriteService;
    private final NotificationService notificationService;
    private final SellerRequestService sellerRequestService;
    private final UserModerationService userModerationService;
    private final CustomUserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @GetMapping
    public String profile(@AuthenticationPrincipal UserDetails userDetails,
                          @RequestParam(defaultValue = "favorites") String tab,
                          Model model) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("reviews", reviewService.getByAuthor(user.getId()));
        model.addAttribute("favorites", favoriteService.getFavorites(user.getId()));
        model.addAttribute("notifications", notificationService.getLatest(user.getId()));
        model.addAttribute("warnings", userModerationService.getWarnings(user.getId()));
        model.addAttribute("sellerRequest", sellerRequestService.getLast(user.getId()).orElse(null));
        model.addAttribute("activeTab", tab);
        return "profile";
    }

    @GetMapping("/{id}/avatar")
    @ResponseBody
    public ResponseEntity<byte[]> avatar(@PathVariable Integer id) {
        User user = userService.findById(id).orElseThrow();
        if (user.getProfilePic() != null) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(user.getProfilePic());
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/edit")
    @ResponseBody
    public ResponseEntity<String> editProfile(@PathVariable Integer id,
                                               @RequestParam(required = false) String username,
                                               @RequestParam(required = false) String bio,
                                               @RequestParam(required = false) MultipartFile avatar,
                                               @AuthenticationPrincipal UserDetails userDetails,
                                               HttpServletRequest request,
                                               HttpServletResponse response) throws IOException {
        User user = userService.findById(id).orElseThrow();
        if (!user.getUsername().equals(userDetails.getUsername())) {
            return ResponseEntity.status(403).body("Нет доступа");
        }
        boolean renamed = false;
        if (username != null && !username.isBlank() && !username.trim().equals(user.getUsername())) {
            String newName = username.trim();
            if (newName.length() > 50) return ResponseEntity.badRequest().body("Имя не должно превышать 50 символов");
            if (userService.isUsernameTaken(newName)) return ResponseEntity.badRequest().body("Имя пользователя занято");
            user.setUsername(newName);
            renamed = true;
        }
        if (bio != null) {
            if (bio.length() > 255) return ResponseEntity.badRequest().body("Слишком длинный текст «О себе»");
            user.setBio(bio.trim());
        }
        if (avatar != null && !avatar.isEmpty()) {
            if (avatar.getContentType() == null || !avatar.getContentType().startsWith("image/")) {
                return ResponseEntity.badRequest().body("Аватар должен быть изображением");
            }
            user.setProfilePic(avatar.getBytes());
        }
        userService.save(user);
        if (renamed) {
            // Сессия привязана к имени пользователя — обновляем аутентификацию под новым именем
            UserDetails fresh = userDetailsService.loadUserByUsername(user.getUsername());
            var auth = UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities());
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/notifications/read")
    public String markNotificationsRead(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        notificationService.markAllRead(user.getId());
        return "redirect:/profile?tab=notifications";
    }

    @PostMapping("/seller-request")
    public String sellerRequest(@RequestParam String shopName,
                                @RequestParam(required = false) String description,
                                @AuthenticationPrincipal UserDetails userDetails,
                                RedirectAttributes ra) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        sellerRequestService.submit(user, shopName, description);
        ra.addFlashAttribute("flashSuccess", "Заявка отправлена администратору");
        return "redirect:/profile?tab=seller";
    }
}
