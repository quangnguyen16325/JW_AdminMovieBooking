package com.example.cineai_webadmin.controller;

import com.example.cineai_webadmin.dto.RegisterRequest;
import com.example.cineai_webadmin.dto.MovieRequest;
import com.example.cineai_webadmin.dto.CinemaRequest;
import com.example.cineai_webadmin.service.AuthService;
import com.example.cineai_webadmin.service.MovieService;
import com.example.cineai_webadmin.service.CinemaService;
import com.example.cineai_webadmin.service.BookingService;
import com.example.cineai_webadmin.service.UserService;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
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
import java.util.stream.Collectors;

@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    private final MovieService movieService;
    private final CinemaService cinemaService;
    private final BookingService bookingService;
    private final UserService userService;

    @Autowired
    public AuthController(AuthService authService, MovieService movieService, CinemaService cinemaService, BookingService bookingService, UserService userService) {
        this.authService = authService;
        this.movieService = movieService;
        this.cinemaService = cinemaService;
        this.bookingService = bookingService;
        this.userService = userService;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/home";
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute RegisterRequest registerRequest, Model model) {
        try {
            authService.register(registerRequest);
            model.addAttribute("email", registerRequest.getEmail());
            return "verify-email";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/login")
    public String showLoginForm(Model model) {
        return "login";
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        return "forgot-password";
    }

    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        
        // Nếu đã đăng nhập, cập nhật thông tin người dùng
        if (userId != null && "user".equals(role)) {
            try {
                Firestore db = FirestoreClient.getFirestore();
                DocumentSnapshot userDoc = db.collection("users").document(userId).get().get();
                if (userDoc.exists()) {
                    // Update lastLogin
                    db.collection("users").document(userId).update("lastLogin", Timestamp.now());
                    session.setAttribute("fullName", userDoc.getString("fullName"));
                    logger.info("User {} with role {} accessed /home", userId, role);
                    model.addAttribute("role", role);
                    model.addAttribute("fullName", userDoc.getString("fullName"));
                }
            } catch (Exception e) {
                logger.error("Error accessing /home for user {}: {}", userId, e.getMessage());
            }
        }
        
        return "home";
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(HttpSession session, Model model) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/dashboard, userId: {}, role: {}", userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentSnapshot adminDoc = db.collection("admin").document(userId).get().get();
            if (!adminDoc.exists()) {
                logger.warn("Admin {} not found in Firestore", userId);
                return "redirect:/login?error=Admin not found";
            }
            session.setAttribute("email", adminDoc.getString("email"));
            logger.info("Admin {} with role {} accessed /admin/dashboard", userId, role);
            model.addAttribute("role", role);
            model.addAttribute("email", adminDoc.getString("email"));
            
            // Lấy số lượng phim đang chiếu
            long nowShowingCount = movieService.getNowShowingMoviesCount();
            model.addAttribute("nowShowingCount", nowShowingCount);
            
            // Lấy số lượng phim mới trong 7 ngày
            long newMoviesCount = movieService.getNewMoviesCount();
            model.addAttribute("newMoviesCount", newMoviesCount);
            
            // Lấy số lượng rạp chiếu
            long cinemasCount = cinemaService.getAllCinemas().size();
            model.addAttribute("cinemasCount", cinemasCount);

            // Lấy tổng doanh thu
            double totalRevenue = bookingService.getTotalRevenue();
            model.addAttribute("totalRevenue", totalRevenue);

            // Lấy số lượng vé đã bán
            long totalTickets = bookingService.getTotalTickets();
            model.addAttribute("totalTickets", totalTickets);

            // Lấy số lượng người dùng
            long totalUsers = userService.getTotalUsers();
            model.addAttribute("totalUsers", totalUsers);

            // Lấy doanh thu theo tháng
            Map<String, Double> monthlyRevenue = bookingService.getMonthlyRevenue();
            model.addAttribute("monthlyRevenue", monthlyRevenue);

            // Lấy top phim phổ biến
            List<Map<String, Object>> popularMovies = bookingService.getPopularMovies();
            model.addAttribute("popularMovies", popularMovies);

            // Lấy thống kê theo giờ
            Map<String, Long> hourlyStats = bookingService.getHourlyStats();
            model.addAttribute("hourlyStats", hourlyStats);
            
            return "dashboard";
        } catch (Exception e) {
            logger.error("Error accessing /admin/dashboard for admin {}: {}", userId, e.getMessage());
            return "redirect:/login?error=Server error";
        }
    }

    @GetMapping("/admin/settings")
    public String settings(HttpSession session, Model model) {
        String email = (String) session.getAttribute("email");
        if (email == null) {
            return "redirect:/login?error=Session expired";
        }
        model.addAttribute("email", email);
        return "settings";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login?logout";
    }

    @GetMapping("/verify-email")
    public String showVerifyEmailPage(@RequestParam(required = false) String email, Model model) {
        if (email != null) {
            model.addAttribute("email", email);
        }
        return "verify-email";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password, HttpSession session, Model model) {
        try {
            UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmail(email);
            
            // Bỏ qua xác thực email cho tài khoản admin
            if (!userRecord.isEmailVerified() && !email.equals("admin@cineai.com")) {
                model.addAttribute("email", email);
                return "verify-email";
            }

            // Set session attributes
            session.setAttribute("userId", userRecord.getUid());
            session.setAttribute("role", email.equals("admin@cineai.com") ? "admin" : "user");
            
            return "redirect:/" + (email.equals("admin@cineai.com") ? "admin/dashboard" : "home");
        } catch (Exception e) {
            return "redirect:/login?error=" + e.getMessage();
        }
    }

    // Movie Management Endpoints
    @GetMapping("/admin/movies")
    public String listMovies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            Model model, 
            HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        String email = (String) session.getAttribute("email");

        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access attempt to movies list");
            return "redirect:/login";
        }

        try {
            List<Map<String, Object>> movies = movieService.getAllMovies();
            
            // Lọc theo từ khóa tìm kiếm
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                movies = movies.stream()
                    .filter(movie -> {
                        String title = String.valueOf(movie.get("title")).toLowerCase();
                        String director = String.valueOf(movie.get("director")).toLowerCase();
                        Object castObj = movie.get("cast");
                        String cast = castObj instanceof List ? 
                            ((List<?>) castObj).stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(" ")).toLowerCase() :
                            String.valueOf(castObj).toLowerCase();
                            
                        return title.contains(searchLower) || 
                               director.contains(searchLower) || 
                               cast.contains(searchLower);
                    })
                    .collect(Collectors.toList());
            }
            
            // Lọc theo trạng thái
            if (status != null && !status.isEmpty()) {
                boolean isNowShowing = "showing".equals(status);
                movies = movies.stream()
                    .filter(movie -> {
                        Boolean nowShowing = (Boolean) movie.get("isNowShowing");
                        return isNowShowing == nowShowing;
                    })
                    .collect(Collectors.toList());
            }

            model.addAttribute("movies", movies);
            model.addAttribute("searchQuery", search);
            model.addAttribute("selectedStatus", status);
            model.addAttribute("email", email);
            return "admin/movies/list";
        } catch (Exception e) {
            logger.error("Error getting movies list", e);
            model.addAttribute("error", "Có lỗi xảy ra khi lấy danh sách phim");
            return "admin/movies/list";
        }
    }

    @GetMapping("/admin/movies/create")
    public String showCreateForm(Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/movies/create, userId: {}, role: {}", userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        model.addAttribute("movie", new MovieRequest());
        model.addAttribute("email", session.getAttribute("email"));
        return "admin/movies/create";
    }

    @PostMapping("/admin/movies/create")
    public String createMovie(@ModelAttribute MovieRequest movieRequest, RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to POST /admin/movies/create, userId: {}, role: {}", userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            String movieId = movieService.createMovie(movieRequest);
            redirectAttributes.addFlashAttribute("success", "Phim đã được thêm thành công!");
            return "redirect:/admin/movies";
        } catch (Exception e) {
            logger.error("Error creating movie: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Lỗi khi thêm phim: " + e.getMessage());
            return "redirect:/admin/movies/create";
        }
    }

    @GetMapping("/admin/movies/edit/{id}")
    public String showEditForm(@PathVariable String id, Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/movies/edit/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            Map<String, Object> movie = movieService.getMovieById(id);
            if (movie != null) {
                model.addAttribute("movie", movie);
                model.addAttribute("email", session.getAttribute("email"));
                return "admin/movies/edit";
            }
            return "redirect:/admin/movies";
        } catch (Exception e) {
            logger.error("Error accessing /admin/movies/edit/{}: {}", id, e.getMessage());
            return "redirect:/admin/movies?error=" + e.getMessage();
        }
    }

    @PostMapping("/admin/movies/edit/{id}")
    public String updateMovie(@PathVariable String id, @ModelAttribute MovieRequest movieRequest, 
                            RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to POST /admin/movies/edit/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            movieService.updateMovie(id, movieRequest);
            redirectAttributes.addFlashAttribute("success", "Phim đã được cập nhật thành công!");
            return "redirect:/admin/movies";
        } catch (Exception e) {
            logger.error("Error updating movie {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật phim: " + e.getMessage());
            return "redirect:/admin/movies/edit/" + id;
        }
    }

    @PostMapping("/admin/movies/delete/{id}")
    public String deleteMovie(@PathVariable String id, RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to POST /admin/movies/delete/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            movieService.deleteMovie(id);
            redirectAttributes.addFlashAttribute("success", "Phim đã được xóa thành công!");
        } catch (Exception e) {
            logger.error("Error deleting movie {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa phim: " + e.getMessage());
        }
        return "redirect:/admin/movies";
    }

    @GetMapping("/admin/movies/now-showing")
    public String getNowShowingMovies(Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/movies/now-showing, userId: {}, role: {}", userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            List<Map<String, Object>> movies = movieService.getNowShowingMovies();
            model.addAttribute("movies", movies);
            model.addAttribute("email", session.getAttribute("email"));
            return "admin/movies/now-showing";
        } catch (Exception e) {
            logger.error("Error accessing /admin/movies/now-showing: {}", e.getMessage());
            return "redirect:/admin/dashboard?error=" + e.getMessage();
        }
    }

    @GetMapping("/admin/movies/coming-soon")
    public String getComingSoonMovies(Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/movies/coming-soon, userId: {}, role: {}", userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            List<Map<String, Object>> movies = movieService.getComingSoonMovies();
            model.addAttribute("movies", movies);
            model.addAttribute("email", session.getAttribute("email"));
            return "admin/movies/coming-soon";
        } catch (Exception e) {
            logger.error("Error accessing /admin/movies/coming-soon: {}", e.getMessage());
            return "redirect:/admin/dashboard?error=" + e.getMessage();
        }
    }

    // Cinema Management Endpoints
    @GetMapping("/admin/cinemas")
    public String listCinemas(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String city,
            Model model, 
            HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        String email = (String) session.getAttribute("email");

        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access attempt to cinemas list");
            return "redirect:/login";
        }

        try {
            List<Map<String, Object>> cinemas = cinemaService.getAllCinemas();
            
            // Lọc theo từ khóa tìm kiếm
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                cinemas = cinemas.stream()
                    .filter(cinema -> {
                        String name = String.valueOf(cinema.get("name")).toLowerCase();
                        String address = String.valueOf(cinema.get("address")).toLowerCase();
//                        String facilities = String.valueOf(cinema.get("facilities")).toLowerCase();
                        return name.contains(searchLower) || 
                               address.contains(searchLower);
//                        || facilities.contains(searchLower);
                    })
                    .collect(Collectors.toList());
            }
            
            // Lọc theo thành phố
            if (city != null && !city.isEmpty()) {
                cinemas = cinemas.stream()
                    .filter(cinema -> city.equals(cinema.get("city")))
                    .collect(Collectors.toList());
            }

            // Lấy danh sách các thành phố duy nhất
            List<String> cities = cinemas.stream()
                .map(cinema -> String.valueOf(cinema.get("city")))
                .distinct()
                .sorted()
                .collect(Collectors.toList());

            model.addAttribute("cinemas", cinemas);
            model.addAttribute("cities", cities);
            model.addAttribute("searchQuery", search);
            model.addAttribute("selectedCity", city);
            model.addAttribute("email", email);
            return "admin/cinemas/list";
        } catch (Exception e) {
            logger.error("Error getting cinemas list", e);
            model.addAttribute("error", "Có lỗi xảy ra khi lấy danh sách rạp chiếu");
            return "admin/cinemas/list";
        }
    }

    @GetMapping("/admin/cinemas/create")
    public String showCreateCinemaForm(Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/cinemas/create, userId: {}, role: {}", userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        model.addAttribute("cinema", new CinemaRequest());
        model.addAttribute("email", session.getAttribute("email"));
        return "admin/cinemas/create";
    }

    @PostMapping("/admin/cinemas/create")
    public String createCinema(@ModelAttribute CinemaRequest cinemaRequest, RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to POST /admin/cinemas/create, userId: {}, role: {}", userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            String cinemaId = cinemaService.createCinema(cinemaRequest);
            redirectAttributes.addFlashAttribute("success", "Rạp chiếu phim đã được thêm thành công!");
            return "redirect:/admin/cinemas";
        } catch (Exception e) {
            logger.error("Error creating cinema: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Lỗi khi thêm rạp chiếu phim: " + e.getMessage());
            return "redirect:/admin/cinemas/create";
        }
    }

    @GetMapping("/admin/cinemas/edit/{id}")
    public String showEditCinemaForm(@PathVariable String id, Model model, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to /admin/cinemas/edit/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            Map<String, Object> cinema = cinemaService.getCinemaById(id);
            if (cinema != null) {
                model.addAttribute("cinema", cinema);
                model.addAttribute("email", session.getAttribute("email"));
                return "admin/cinemas/edit";
            }
            return "redirect:/admin/cinemas";
        } catch (Exception e) {
            logger.error("Error accessing /admin/cinemas/edit/{}: {}", id, e.getMessage());
            return "redirect:/admin/cinemas?error=" + e.getMessage();
        }
    }

    @PostMapping("/admin/cinemas/edit/{id}")
    public String updateCinema(@PathVariable String id, @ModelAttribute CinemaRequest cinemaRequest, 
                            RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to POST /admin/cinemas/edit/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            cinemaService.updateCinema(id, cinemaRequest);
            redirectAttributes.addFlashAttribute("success", "Rạp chiếu phim đã được cập nhật thành công!");
            return "redirect:/admin/cinemas";
        } catch (Exception e) {
            logger.error("Error updating cinema {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Lỗi khi cập nhật rạp chiếu phim: " + e.getMessage());
            return "redirect:/admin/cinemas/edit/" + id;
        }
    }

    @PostMapping("/admin/cinemas/delete/{id}")
    public String deleteCinema(@PathVariable String id, RedirectAttributes redirectAttributes, HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        String role = (String) session.getAttribute("role");
        if (userId == null || !"admin".equals(role)) {
            logger.warn("Unauthorized access to POST /admin/cinemas/delete/{}, userId: {}, role: {}", id, userId, role);
            return "redirect:/login?error=Unauthorized";
        }
        try {
            cinemaService.deleteCinema(id);
            redirectAttributes.addFlashAttribute("success", "Rạp chiếu phim đã được xóa thành công!");
        } catch (Exception e) {
            logger.error("Error deleting cinema {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa rạp chiếu phim: " + e.getMessage());
        }
        return "redirect:/admin/cinemas";
    }
}