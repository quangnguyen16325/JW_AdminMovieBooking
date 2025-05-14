package com.example.cineai_webadmin.controller;

import com.example.cineai_webadmin.dto.ShowtimeRequest;
import com.example.cineai_webadmin.service.ShowtimeService;
import com.example.cineai_webadmin.service.CinemaService;
import com.example.cineai_webadmin.service.MovieService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Collections;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/showtimes")
public class ShowtimeController {
    private static final Logger logger = LoggerFactory.getLogger(ShowtimeController.class);
    
    @Autowired
    private ShowtimeService showtimeService;

    @Autowired
    private CinemaService cinemaService;

    @Autowired
    private MovieService movieService;

    @GetMapping
    public String listShowtimes(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String date,
            Model model, 
            HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        String email = (String) session.getAttribute("email");

        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access attempt to showtimes list");
            return "redirect:/login";
        }

        try {
            List<Map<String, Object>> showtimes = showtimeService.getAllShowtimes();
            
            // Lọc theo từ khóa tìm kiếm
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                showtimes = showtimes.stream()
                    .filter(showtime -> {
                        String movieName = ((String) showtime.get("movieName")).toLowerCase();
                        String cinemaName = ((String) showtime.get("cinemaName")).toLowerCase();
                        return movieName.contains(searchLower) || cinemaName.contains(searchLower);
                    })
                    .collect(Collectors.toList());
            }
            
            // Nhóm lịch chiếu theo ngày
            Map<String, List<Map<String, Object>>> showtimesByDate = new TreeMap<>(Collections.reverseOrder());
            for (Map<String, Object> showtime : showtimes) {
                String startTime = showtime.get("startTime").toString();
                String showDate = startTime.substring(0, 10); // Lấy phần YYYY-MM-DD
                
                // Lọc theo ngày nếu có
                if (date != null && !date.isEmpty() && !showDate.equals(date)) {
                    continue;
                }
                
                showtimesByDate.computeIfAbsent(showDate, k -> new ArrayList<>()).add(showtime);
            }

            // Lấy danh sách các ngày có lịch chiếu để hiển thị trong dropdown
            List<String> availableDates = new ArrayList<>(showtimesByDate.keySet());
            Collections.sort(availableDates, Collections.reverseOrder());

            model.addAttribute("showtimesByDate", showtimesByDate);
            model.addAttribute("availableDates", availableDates);
            model.addAttribute("selectedDate", date);
            model.addAttribute("searchQuery", search);
            model.addAttribute("email", email);
            return "admin/showtimes/list";
        } catch (Exception e) {
            logger.error("Error getting showtimes list", e);
            model.addAttribute("error", "Có lỗi xảy ra khi lấy danh sách lịch chiếu");
            return "admin/showtimes/list";
        }
    }

    @GetMapping("/create")
    public String showCreateForm(Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/showtimes/create, userId: {}, role: {}", userId, role);
            return "redirect:/login?error=Unauthorized";
        }

        try {
            // Lấy danh sách rạp chiếu
            List<Map<String, Object>> cinemas = cinemaService.getAllCinemas();
            model.addAttribute("cinemas", cinemas);

            // Lấy danh sách phim đang chiếu
            List<Map<String, Object>> movies = movieService.getNowShowingMovies();
            model.addAttribute("movies", movies);

            model.addAttribute("showtime", new ShowtimeRequest());
            model.addAttribute("email", session.getAttribute("email"));
            return "admin/showtimes/create";
        } catch (Exception e) {
            logger.error("Error loading create showtime form: {}", e.getMessage());
            return "redirect:/admin/showtimes?error=" + e.getMessage();
        }
    }

    @PostMapping("/create")
    public String createShowtime(@ModelAttribute ShowtimeRequest showtime, RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to POST /admin/showtimes/create, userId: {}, role: {}", userId, role);
            return "redirect:/login?error=Unauthorized";
        }

        try {
            // Validate input
            if (showtime.getCinemaId() == null || showtime.getCinemaId().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng chọn rạp chiếu");
            }
            if (showtime.getMovieId() == null || showtime.getMovieId().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng chọn phim");
            }
            if (showtime.getScreenId() == null || showtime.getScreenId().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng chọn phòng chiếu");
            }
            if (showtime.getStartTime() == null) {
                throw new IllegalArgumentException("Vui lòng chọn thời gian bắt đầu");
            }
            if (showtime.getEndTime() == null) {
                throw new IllegalArgumentException("Vui lòng chọn thời gian kết thúc");
            }
            if (showtime.getFormat() == null || showtime.getFormat().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng chọn định dạng");
            }
            if (showtime.getPrice() == null || showtime.getPrice() <= 0) {
                throw new IllegalArgumentException("Vui lòng nhập giá vé hợp lệ");
            }

            // Create showtime
            showtimeService.createShowtime(showtime);
            redirectAttributes.addFlashAttribute("success", "Thêm lịch chiếu thành công!");
            return "redirect:/admin/showtimes";
        } catch (IllegalArgumentException e) {
            logger.error("Validation error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/showtimes/create";
        } catch (Exception e) {
            logger.error("Error creating showtime: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Có lỗi xảy ra khi thêm lịch chiếu: " + e.getMessage());
            return "redirect:/admin/showtimes/create";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable String id, Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/showtimes/edit/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            // Lấy thông tin lịch chiếu
            Map<String, Object> showtime = showtimeService.getShowtimeById(id);
            if (showtime == null) {
                return "redirect:/admin/showtimes?error=Lịch chiếu không tồn tại";
            }

            // Lấy danh sách rạp chiếu
            List<Map<String, Object>> cinemas = cinemaService.getAllCinemas();
            model.addAttribute("cinemas", cinemas);

            // Lấy danh sách phim đang chiếu
            List<Map<String, Object>> movies = movieService.getNowShowingMovies();
            model.addAttribute("movies", movies);

            // Lấy danh sách phòng chiếu của rạp đã chọn
            String cinemaId = (String) showtime.get("cinemaId");
            List<Map<String, Object>> screens = cinemaService.getScreensByCinemaId(cinemaId);
            model.addAttribute("screens", screens);

            model.addAttribute("showtime", showtime);
            model.addAttribute("email", session.getAttribute("email"));
            return "admin/showtimes/edit";
        } catch (Exception e) {
            logger.error("Error accessing /admin/showtimes/edit/{}: {}", id, e.getMessage());
            return "redirect:/admin/showtimes?error=" + e.getMessage();
        }
    }

    @PostMapping("/edit/{id}")
    public String updateShowtime(@PathVariable String id, @ModelAttribute ShowtimeRequest showtime, Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to POST /admin/showtimes/edit/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            // Validate input
            if (showtime.getCinemaId() == null || showtime.getCinemaId().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng chọn rạp chiếu");
            }
            if (showtime.getMovieId() == null || showtime.getMovieId().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng chọn phim");
            }
            if (showtime.getScreenId() == null || showtime.getScreenId().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng chọn phòng chiếu");
            }
            if (showtime.getStartTime() == null) {
                throw new IllegalArgumentException("Vui lòng chọn thời gian bắt đầu");
            }
            if (showtime.getEndTime() == null) {
                throw new IllegalArgumentException("Vui lòng chọn thời gian kết thúc");
            }
            if (showtime.getFormat() == null || showtime.getFormat().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng chọn định dạng");
            }
            if (showtime.getPrice() == null || showtime.getPrice() <= 0) {
                throw new IllegalArgumentException("Vui lòng nhập giá vé hợp lệ");
            }

            showtimeService.updateShowtime(id, showtime);
            redirectAttributes.addFlashAttribute("success", "Cập nhật lịch chiếu thành công!");
            return "redirect:/admin/showtimes";
        } catch (IllegalArgumentException e) {
            logger.error("Validation error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            
            // Lấy lại dữ liệu cần thiết cho form
            try {
                Map<String, Object> showtimeData = showtimeService.getShowtimeById(id);
                model.addAttribute("showtime", showtimeData);
                
                List<Map<String, Object>> cinemas = cinemaService.getAllCinemas();
                model.addAttribute("cinemas", cinemas);
                
                List<Map<String, Object>> movies = movieService.getNowShowingMovies();
                model.addAttribute("movies", movies);
                
                String cinemaId = showtime.getCinemaId();
                List<Map<String, Object>> screens = cinemaService.getScreensByCinemaId(cinemaId);
                model.addAttribute("screens", screens);
                
                model.addAttribute("email", session.getAttribute("email"));
            } catch (Exception ex) {
                logger.error("Error loading form data: {}", ex.getMessage());
                model.addAttribute("error", "Có lỗi xảy ra khi tải dữ liệu form: " + ex.getMessage());
            }
            
            return "admin/showtimes/edit";
        } catch (Exception e) {
            logger.error("Error updating showtime {}: {}", id, e.getMessage());
            model.addAttribute("error", e.getMessage());
            
            // Lấy lại dữ liệu cần thiết cho form
            try {
                Map<String, Object> showtimeData = showtimeService.getShowtimeById(id);
                model.addAttribute("showtime", showtimeData);
                
                List<Map<String, Object>> cinemas = cinemaService.getAllCinemas();
                model.addAttribute("cinemas", cinemas);
                
                List<Map<String, Object>> movies = movieService.getNowShowingMovies();
                model.addAttribute("movies", movies);
                
                String cinemaId = showtime.getCinemaId();
                List<Map<String, Object>> screens = cinemaService.getScreensByCinemaId(cinemaId);
                model.addAttribute("screens", screens);
                
                model.addAttribute("email", session.getAttribute("email"));
            } catch (Exception ex) {
                logger.error("Error loading form data: {}", ex.getMessage());
                model.addAttribute("error", "Có lỗi xảy ra khi tải dữ liệu form: " + ex.getMessage());
            }
            
            return "admin/showtimes/edit";
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteShowtime(@PathVariable String id, RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to POST /admin/showtimes/delete/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            showtimeService.deleteShowtime(id);
            redirectAttributes.addFlashAttribute("success", "Xóa lịch chiếu thành công!");
        } catch (Exception e) {
            logger.error("Error deleting showtime {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/showtimes";
    }

    @GetMapping("/screens/{cinemaId}")
    @ResponseBody
    public List<Map<String, Object>> getScreensByCinema(@PathVariable String cinemaId) {
        try {
            return cinemaService.getScreensByCinemaId(cinemaId);
        } catch (Exception e) {
            logger.error("Error getting screens for cinema {}: {}", cinemaId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @PostMapping("/check-time-conflict")
    @ResponseBody
    public Map<String, Boolean> checkTimeConflict(@RequestBody Map<String, String> request) {
        try {
            String cinemaId = request.get("cinemaId");
            String screenId = request.get("screenId");
            String startTime = request.get("startTime");
            String endTime = request.get("endTime");
            String excludeShowtimeId = request.get("excludeShowtimeId");

            LocalDateTime startDateTime = LocalDateTime.parse(startTime.replace(" ", "T"));
            LocalDateTime endDateTime = LocalDateTime.parse(endTime.replace(" ", "T"));

            boolean hasConflict = showtimeService.isTimeSlotOverlapping(
                cinemaId, 
                screenId, 
                startDateTime, 
                endDateTime, 
                excludeShowtimeId
            );

            Map<String, Boolean> response = new HashMap<>();
            response.put("hasConflict", hasConflict);
            return response;
        } catch (Exception e) {
            Map<String, Boolean> response = new HashMap<>();
            response.put("hasConflict", true);
            return response;
        }
    }
} 