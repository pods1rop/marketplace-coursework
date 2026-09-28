package ru.vlsu.marketplace.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vlsu.marketplace.dto.ProductDto;
import ru.vlsu.marketplace.entities.Order;
import ru.vlsu.marketplace.entities.Product;
import ru.vlsu.marketplace.entities.ProductImage;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.repositories.BrandRepository;
import ru.vlsu.marketplace.repositories.CategoryRepository;
import ru.vlsu.marketplace.repositories.ProductImageRepository;
import ru.vlsu.marketplace.services.OrderService;
import ru.vlsu.marketplace.services.ProductService;
import ru.vlsu.marketplace.services.UserService;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/seller")
@RequiredArgsConstructor
public class SellerController {

    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UserService userService;
    private final OrderService orderService;
    private final ProductImageRepository productImageRepository;

    @GetMapping("/products")
    public String myProducts(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = currentUser(userDetails);
        model.addAttribute("approved", productService.getBySellerAndStatus(user.getId(), Product.Status.APPROVED));
        model.addAttribute("pending", productService.getBySellerAndStatus(user.getId(), Product.Status.PENDING));
        model.addAttribute("rejected", productService.getBySellerAndStatus(user.getId(), Product.Status.REJECTED));
        model.addAttribute("removed", productService.getBySellerAndStatus(user.getId(), Product.Status.REMOVED));
        return "seller/my_products";
    }

    @GetMapping("/products/new")
    public String addProductForm(Model model) {
        model.addAttribute("productDto", new ProductDto());
        addFormReferenceData(model);
        return "seller/add_product";
    }

    @PostMapping("/products/new")
    public String addProduct(@Valid @ModelAttribute ProductDto dto, BindingResult binding,
                             @RequestParam(required = false) MultipartFile[] images,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes ra) throws IOException {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("flashError", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/seller/products/new";
        }
        checkImages(images);
        User seller = currentUser(userDetails);
        Product product = new Product();
        product.setTitle(dto.getTitle().trim());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setCondition(dto.getCondition());
        // Новый товар всегда уходит на модерацию
        product.setStatus(Product.Status.PENDING);
        product.setSeller(seller);
        product.setCreatedAt(Instant.now());
        applyDtoAttributes(product, dto);
        Product saved = productService.save(product);
        saveImages(saved, images, 0);
        ra.addFlashAttribute("flashSuccess", "Товар отправлен на модерацию");
        return "redirect:/seller/products";
    }

    @GetMapping("/products/{id}/edit")
    public String editProductForm(@PathVariable Integer id, @AuthenticationPrincipal UserDetails userDetails, Model model) {
        Product product = ownProduct(id, userDetails);
        ProductDto dto = new ProductDto();
        dto.setTitle(product.getTitle());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setOldPrice(product.getOldPrice());
        dto.setCondition(product.getCondition());
        dto.setCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        dto.setBrandId(product.getBrand() != null ? product.getBrand().getId() : null);
        dto.setGender(product.getGender());
        dto.setSeason(product.getSeason());
        dto.setColor(product.getColor());
        dto.setMaterial(product.getMaterial());
        dto.setSize(product.getSize());
        model.addAttribute("productDto", dto);
        model.addAttribute("productId", id);
        model.addAttribute("hasImage", product.getImageData() != null && product.getImageData().length > 0);
        model.addAttribute("extraImages", productImageRepository.findByProductIdOrderBySortOrderAsc(id));
        addFormReferenceData(model);
        return "seller/edit_product";
    }

    @PostMapping("/products/{id}/edit")
    public String editProduct(@PathVariable Integer id, @Valid @ModelAttribute ProductDto dto, BindingResult binding,
                              @RequestParam(required = false) MultipartFile[] images,
                              @AuthenticationPrincipal UserDetails userDetails,
                              RedirectAttributes ra) throws IOException {
        Product product = ownProduct(id, userDetails);
        if (binding.hasErrors()) {
            ra.addFlashAttribute("flashError", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/seller/products/" + id + "/edit";
        }
        checkImages(images);
        product.setTitle(dto.getTitle().trim());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setCondition(dto.getCondition());
        applyDtoAttributes(product, dto);
        // Исправленный после отклонения товар повторно отправляется на модерацию
        if (product.getStatus() == Product.Status.REJECTED) {
            product.setStatus(Product.Status.PENDING);
            product.setRejectReason(null);
            ra.addFlashAttribute("flashSuccess", "Товар исправлен и повторно отправлен на модерацию");
        } else {
            ra.addFlashAttribute("flashSuccess", "Изменения сохранены");
        }
        Product saved = productService.save(product);
        int existingCount = productImageRepository.findByProductIdOrderBySortOrderAsc(id).size()
                          + (saved.getImageData() != null ? 1 : 0);
        saveImages(saved, images, existingCount);
        return "redirect:/seller/products";
    }

    private void addFormReferenceData(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("brands", brandRepository.findAll());
        model.addAttribute("conditions", Product.Condition.values());
        model.addAttribute("genders", Product.Gender.values());
        model.addAttribute("seasons", Product.Season.values());
    }

    private void applyDtoAttributes(Product product, ProductDto dto) {
        product.setCategory(categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new IllegalStateException("Категория не найдена")));
        product.setBrand(dto.getBrandId() != null ? brandRepository.findById(dto.getBrandId()).orElse(null) : null);
        product.setGender(dto.getGender());
        product.setSeason(dto.getSeason());
        product.setColor(dto.getColor());
        product.setMaterial(dto.getMaterial());
        product.setSize(dto.getSize());
        product.setOldPrice(dto.getOldPrice());
    }

    @PostMapping("/products/{productId}/images/{imageId}/delete")
    @ResponseBody
    public ResponseEntity<String> deleteImage(@PathVariable Integer productId, @PathVariable Integer imageId,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        ownProduct(productId, userDetails);
        productImageRepository.findById(imageId).ifPresent(img -> {
            if (img.getProduct().getId().equals(productId)) {
                productImageRepository.delete(img);
            }
        });
        return ResponseEntity.ok("OK");
    }

    /** Принимаются только изображения; размер ограничен настройкой multipart (5 МБ). */
    private void checkImages(MultipartFile[] images) {
        if (images == null) return;
        for (MultipartFile file : images) {
            if (file == null || file.isEmpty()) continue;
            String type = file.getContentType();
            if (type == null || !type.startsWith("image/")) {
                throw new IllegalStateException("Файл «" + file.getOriginalFilename() + "» не является изображением");
            }
        }
    }

    private void saveImages(Product product, MultipartFile[] images, int startSortOrder) throws IOException {
        if (images == null) return;
        List<ProductImage> toSave = new ArrayList<>();
        int order = startSortOrder;
        for (MultipartFile file : images) {
            if (file == null || file.isEmpty()) continue;
            // Первое фото — в imageData (для совместимости с каталогом)
            if (product.getImageData() == null) {
                product.setImageData(file.getBytes());
                productService.save(product);
            } else {
                ProductImage img = new ProductImage();
                img.setProduct(product);
                img.setImageData(file.getBytes());
                img.setSortOrder(order++);
                toSave.add(img);
            }
        }
        if (!toSave.isEmpty()) {
            productImageRepository.saveAll(toSave);
        }
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Integer id, @AuthenticationPrincipal UserDetails userDetails,
                                RedirectAttributes ra) {
        Product product = ownProduct(id, userDetails);
        product.setStatus(Product.Status.REMOVED);
        productService.save(product);
        ra.addFlashAttribute("flashSuccess", "Товар снят с продажи");
        return "redirect:/seller/products";
    }

    /** Возобновление продажи: товар заново проходит модерацию. */
    @PostMapping("/products/{id}/restore")
    public String restoreProduct(@PathVariable Integer id, @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes ra) {
        Product product = ownProduct(id, userDetails);
        if (product.getStatus() == Product.Status.REMOVED) {
            product.setStatus(Product.Status.PENDING);
            productService.save(product);
            ra.addFlashAttribute("flashSuccess", "Товар отправлен на модерацию для возобновления продажи");
        }
        return "redirect:/seller/products";
    }

    @GetMapping("/orders")
    public String sellerOrders(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = currentUser(userDetails);
        model.addAttribute("orders", orderService.getOrdersBySeller(user.getId()));
        return "seller/seller_orders";
    }

    @PostMapping("/orders/{id}/status")
    public String updateOrderStatus(@PathVariable Integer id, @RequestParam Order.Status status,
                                    @AuthenticationPrincipal UserDetails userDetails, RedirectAttributes ra) {
        orderService.updateStatusBySeller(currentUser(userDetails), id, status);
        ra.addFlashAttribute("flashSuccess", "Статус заказа №" + id + ": " + OrderService.statusTitle(status));
        return "redirect:/seller/orders";
    }

    private User currentUser(UserDetails userDetails) {
        return userService.findByUsername(userDetails.getUsername()).orElseThrow();
    }

    /** Товар текущего продавца (администратору доступны любые товары). */
    private Product ownProduct(Integer id, UserDetails userDetails) {
        Product product = productService.findById(id).orElseThrow();
        User user = currentUser(userDetails);
        if (user.getRole() != User.Role.admin && !product.getSeller().getId().equals(user.getId())) {
            throw new AccessDeniedException("Это товар другого продавца");
        }
        return product;
    }
}
