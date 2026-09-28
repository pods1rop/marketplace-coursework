package ru.vlsu.marketplace.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.support.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException ex, Model model) {
        log.warn("Resource not found: {}", ex.getMessage());
        model.addAttribute("status", 404);
        model.addAttribute("message", "Запрашиваемый объект не найден");
        return "error";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handle404(NoHandlerFoundException ex, Model model) {
        log.warn("Page not found: {}", ex.getRequestURL());
        model.addAttribute("status", 404);
        model.addAttribute("message", "Страница не найдена");
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Object handleAccessDenied(AccessDeniedException ex, Model model, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        if (request.getHeader("X-Requested-With") != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
        model.addAttribute("status", 403);
        model.addAttribute("message", "Доступ запрещён");
        return "error";
    }

    /**
     * Нарушение бизнес-правила. Для обычной отправки формы пользователь возвращается
     * на исходную страницу с всплывающим сообщением, для AJAX-запроса отдаётся 400 с текстом.
     */
    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("Illegal state: {}", ex.getMessage());
        String referer = request.getHeader("Referer");
        boolean isFormPost = "POST".equals(request.getMethod())
                && request.getHeader("X-Requested-With") == null
                && (request.getContentType() == null || !request.getContentType().startsWith("application/json"));
        if (isFormPost && referer != null) {
            RequestContextUtils.getOutputFlashMap(request).put("flashError", ex.getMessage());
            return "redirect:" + referer;
        }
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String handleTooLarge(HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("flashError", "Размер файла не должен превышать 5 МБ");
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model, WebRequest request) {
        log.error("Unhandled error on {}: ", request.getDescription(false), ex);
        model.addAttribute("status", 500);
        model.addAttribute("message", "Внутренняя ошибка сервера");
        return "error";
    }
}
