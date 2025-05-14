package com.example.cineai_webadmin.controller;

import com.example.cineai_webadmin.service.UserService;
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
import java.util.HashMap;

@Controller
@RequestMapping("/admin/users")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @GetMapping
    public String listUsers(Model model, @RequestParam(required = false) String search, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        String email = (String) session.getAttribute("email");

        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/users, userId: {}, role: {}", userId, role);
            return "redirect:/login?error=Unauthorized";
        }

        try {
            List<Map<String, Object>> users;
            if (search != null && !search.trim().isEmpty()) {
                users = userService.searchUsers(search);
            } else {
                users = userService.getAllUsers();
            }
            model.addAttribute("users", users);
            model.addAttribute("searchQuery", search);
            model.addAttribute("email", email);
            return "admin/users/list";
        } catch (Exception e) {
            logger.error("Error accessing /admin/users: {}", e.getMessage());
            model.addAttribute("error", "Lỗi khi tải danh sách người dùng: " + e.getMessage());
            return "admin/users/list";
        }
    }

    @GetMapping("/edit/{id}")
    public String editUser(@PathVariable String id, Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        String email = (String) session.getAttribute("email");

        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/users/edit/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }

        try {
            Map<String, Object> user = userService.getUserById(id);
            model.addAttribute("user", user);
            model.addAttribute("email", email);
            return "admin/users/edit";
        } catch (Exception e) {
            logger.error("Error accessing /admin/users/edit/{}: {}", id, e.getMessage());
            model.addAttribute("error", "Lỗi khi tải thông tin người dùng: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }

    @PostMapping("/update/{id}")
    public String updateUser(@PathVariable String id, @RequestParam Map<String, String> formData, 
                           RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");

        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/users/update/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }

        try {
            Map<String, Object> userData = new HashMap<>();
            userData.put("fullName", formData.get("fullName"));
            // Cho phép profileImage và phoneNumber null
            String profileImage = formData.get("profileImage");
            if (profileImage != null && !profileImage.trim().isEmpty()) {
                userData.put("profileImage", profileImage);
            }
            String phoneNumber = formData.get("phoneNumber");
            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                userData.put("phoneNumber", phoneNumber);
            }
            
            userService.updateUser(id, userData);
            redirectAttributes.addFlashAttribute("success", "Cập nhật thông tin người dùng thành công");
        } catch (Exception e) {
            logger.error("Error updating user {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật thông tin người dùng: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/delete/{id}")
    public String deleteUser(@PathVariable String id, RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");

        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/users/delete/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }

        try {
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("success", "Xóa người dùng thành công");
        } catch (Exception e) {
            logger.error("Error deleting user {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa người dùng: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }
} 