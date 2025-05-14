package com.example.cineai_webadmin.controller;

import com.example.cineai_webadmin.service.BookingService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Controller
@RequestMapping("/admin/bookings")
public class BookingController {
    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);

    private final BookingService bookingService;

    @Autowired
    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    private boolean isAdmin(HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        return userId != null && "admin".equals(role);
    }

    @GetMapping
    public String listBookings(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String sortBy,
            Model model,
            HttpSession session) {
        
        // Kiểm tra đăng nhập và quyền admin
        if (!isAdmin(session)) {
            logger.warn("Unauthorized access attempt to /admin/bookings");
            return "redirect:/login?error=Unauthorized";
        }

        try {
            List<Map<String, Object>> bookings = bookingService.searchBookings(search, status, paymentMethod, sortBy);
            model.addAttribute("bookings", bookings);
            model.addAttribute("searchQuery", search);
            model.addAttribute("selectedStatus", status);
            model.addAttribute("selectedPaymentMethod", paymentMethod);
            model.addAttribute("selectedSort", sortBy);
            model.addAttribute("totalBookings", bookings.size());
            model.addAttribute("email", session.getAttribute("email"));
            return "admin/bookings/list";
        } catch (Exception e) {
            logger.error("Error in listBookings: {}", e.getMessage());
            model.addAttribute("error", "Lỗi khi tải danh sách đặt vé: " + e.getMessage());
            return "admin/bookings/list";
        }
    }

    @GetMapping("/{id}")
    public String viewBooking(@PathVariable String id, Model model, HttpSession session) {
        // Kiểm tra đăng nhập và quyền admin
        if (!isAdmin(session)) {
            logger.warn("Unauthorized access attempt to /admin/bookings/{}", id);
            return "redirect:/login?error=Unauthorized";
        }

        try {
            Map<String, Object> booking = bookingService.getBookingById(id);
            model.addAttribute("booking", booking);
            model.addAttribute("email", session.getAttribute("email"));
            return "admin/bookings/view";
        } catch (Exception e) {
            logger.error("Error in viewBooking: {}", e.getMessage());
            model.addAttribute("error", "Lỗi khi xem chi tiết đặt vé: " + e.getMessage());
            return "redirect:/admin/bookings";
        }
    }

    @GetMapping("/edit/{id}")
    public String editBooking(@PathVariable String id, Model model, HttpSession session) {
        // Kiểm tra đăng nhập và quyền admin
        if (!isAdmin(session)) {
            logger.warn("Unauthorized access attempt to /admin/bookings/edit/{}", id);
            return "redirect:/login?error=Unauthorized";
        }

        try {
            Map<String, Object> booking = bookingService.getBookingById(id);
            model.addAttribute("booking", booking);
            model.addAttribute("email", session.getAttribute("email"));
            return "admin/bookings/edit";
        } catch (Exception e) {
            logger.error("Error in editBooking: {}", e.getMessage());
            model.addAttribute("error", "Lỗi khi tải form chỉnh sửa: " + e.getMessage());
            return "redirect:/admin/bookings";
        }
    }

    @PostMapping("/update/{id}")
    public String updateBooking(
            @PathVariable String id,
            @RequestParam String status,
            @RequestParam String paymentMethod,
            RedirectAttributes redirectAttributes,
            HttpSession session) {
        
        // Kiểm tra đăng nhập và quyền admin
        if (!isAdmin(session)) {
            logger.warn("Unauthorized access attempt to /admin/bookings/update/{}", id);
            return "redirect:/login?error=Unauthorized";
        }

        try {
            Map<String, Object> bookingData = Map.of(
                "status", status,
                "paymentMethod", paymentMethod
            );
            bookingService.updateBooking(id, bookingData);
            redirectAttributes.addFlashAttribute("success", "Cập nhật đặt vé thành công");
            return "redirect:/admin/bookings";
        } catch (Exception e) {
            logger.error("Error in updateBooking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật đặt vé: " + e.getMessage());
            return "redirect:/admin/bookings/edit/" + id;
        }
    }
} 