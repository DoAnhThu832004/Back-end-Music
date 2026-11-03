package com.devteria.identityservice.service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.devteria.identityservice.dto.response.PageResponse;
import com.devteria.identityservice.dto.response.RoleResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.devteria.identityservice.dto.request.UserRolesUpdateRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.devteria.identityservice.constant.PredefinedRole;
import com.devteria.identityservice.dto.request.ChangePasswordRequest;
import com.devteria.identityservice.dto.request.UserCreationRequest;
import com.devteria.identityservice.dto.request.UserUpdateRequest;
import com.devteria.identityservice.dto.response.UserResponse;
import com.devteria.identityservice.entity.Genre;
import com.devteria.identityservice.entity.Role;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.mapper.UserMapper;
import com.devteria.identityservice.repository.GenreRepository;
import com.devteria.identityservice.repository.RoleRepository;
import com.devteria.identityservice.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor // TÄ‚Â¡Ă‚Â»Ă‚Â± Ä‚â€Ă¢â‚¬ËœÄ‚Â¡Ă‚Â»Ă¢â€Â¢ng sinh ra constructor chÄ‚Â¡Ă‚Â»Ă‚Â©a tÄ‚Â¡Ă‚ÂºĂ‚Â¥t cÄ‚Â¡Ă‚ÂºĂ‚Â£ cĂ„â€Ă‚Â¡c field final hoÄ‚Â¡Ă‚ÂºĂ‚Â·c cĂ„â€Ă‚Â³ @NonNull trong class.
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserService {
    UserRepository userRepository;
    RoleRepository roleRepository;
    GenreRepository genreRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    Cloudinary cloudinary;

    @Transactional
    public UserResponse createUser(UserCreationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        HashSet<Role> roles = new HashSet<>();
        roleRepository.findById(PredefinedRole.USER_ROLE).ifPresent(roles::add);
        user.setRoles(roles);

        // XÄ‚Â¡Ă‚Â»Ă‚Â­ lĂ„â€Ă‚Â½ preferred genres Ä‚Â¢Ă¢â€Â¬Ă¢â‚¬Â load Genre entities tÄ‚Â¡Ă‚Â»Ă‚Â« DB theo danh sĂ„â€Ă‚Â¡ch ID Ä‚â€Ă¢â‚¬ËœÄ‚â€ Ă‚Â°Ä‚Â¡Ă‚Â»Ă‚Â£c cung cÄ‚Â¡Ă‚ÂºĂ‚Â¥p
        if (request.getPreferredGenreIds() != null && !request.getPreferredGenreIds().isEmpty()) {
            var genres = genreRepository.findAllById(request.getPreferredGenreIds());
            user.setPreferredGenres(new HashSet<>(genres));
            log.info("User {} Ä‚â€Ă¢â‚¬ËœĂ„â€Ă‚Â£ chÄ‚Â¡Ă‚Â»Ă‚Ân {} thÄ‚Â¡Ă‚Â»Ă†â€™ loÄ‚Â¡Ă‚ÂºĂ‚Â¡i nhÄ‚Â¡Ă‚ÂºĂ‚Â¡c yĂ„â€Ă‚Âªu thĂ„â€Ă‚Â­ch khi Ä‚â€Ă¢â‚¬ËœÄ‚â€Ă†â€™ng kĂ„â€Ă‚Â½",
                    request.getUsername(), genres.size());
        }

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        return userMapper.toUserResponse(user);
    }

    public UserResponse getMyInfo() {
        var context = SecurityContextHolder.getContext();
        String name = context.getAuthentication().getName();

        User user = userRepository.findByUsername(name).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return userMapper.toUserResponse(user);
    }
    @Transactional
    public UserResponse updateUser(String userId, UserUpdateRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String currentUsername = authentication != null ? authentication.getName() : null;

        if (!isAdmin && (currentUsername == null || !currentUsername.equals(user.getUsername()))) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Update cÄ‚Â¡c field cĂ†Â¡ bĂ¡ÂºÂ£n (trĂ¡Â»Â« password, genres)
        userMapper.updateUser(user, request);

        // Password
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(
                    passwordEncoder.encode(request.getPassword())
            );
        }

        // Preferred Genres
        if (request.getPreferredGenreIds() != null) {
            Set<Genre> genres = new HashSet<>(
                    genreRepository.findAllById(request.getPreferredGenreIds())
            );
            user.setPreferredGenres(genres);
        }

        userRepository.save(user);
        return userMapper.toUserResponse(user);
    }

    /**
     * [ADMIN] CĂ¡ÂºÂ­p nhĂ¡ÂºÂ­t vai trÄ‚Â² (roles) cĂ¡Â»Â§a ngĂ†Â°Ă¡Â»Âi dÄ‚Â¹ng Ă¢â‚¬â€ ChĂ¡Â»â€° ADMIN Ă„â€˜Ă†Â°Ă¡Â»Â£c phÄ‚Â©p.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUserRoles(String userId, UserRolesUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (request.getRoles() != null) {
            Set<Role> roles = new HashSet<>(
                    roleRepository.findAllById(request.getRoles())
            );
            user.setRoles(roles);
        }

        userRepository.save(user);
        return userMapper.toUserResponse(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }

    /**
     * ChÄ‚Â¡Ă‚ÂºĂ‚Â·n tĂ„â€Ă‚Â i khoÄ‚Â¡Ă‚ÂºĂ‚Â£n ngÄ‚â€ Ă‚Â°Ä‚Â¡Ă‚Â»Ă‚Âi dĂ„â€Ă‚Â¹ng Ä‚Â¢Ă¢â€Â¬Ă¢â‚¬Â chÄ‚Â¡Ă‚Â»Ă¢â‚¬Â° ADMIN Ä‚â€Ă¢â‚¬ËœÄ‚â€ Ă‚Â°Ä‚Â¡Ă‚Â»Ă‚Â£c phĂ„â€Ă‚Â©p.
     * Sau khi bÄ‚Â¡Ă‚Â»Ă¢â‚¬Â¹ chÄ‚Â¡Ă‚ÂºĂ‚Â·n, user sÄ‚Â¡Ă‚ÂºĂ‚Â½ khĂ„â€Ă‚Â´ng thÄ‚Â¡Ă‚Â»Ă†â€™ Ä‚â€Ă¢â‚¬ËœÄ‚â€Ă†â€™ng nhÄ‚Â¡Ă‚ÂºĂ‚Â­p cho Ä‚â€Ă¢â‚¬ËœÄ‚Â¡Ă‚ÂºĂ‚Â¿n khi Ä‚â€Ă¢â‚¬ËœÄ‚â€ Ă‚Â°Ä‚Â¡Ă‚Â»Ă‚Â£c mÄ‚Â¡Ă‚Â»Ă…Â¸ khoĂ„â€Ă‚Â¡.
     *
     * @param userId ID cÄ‚Â¡Ă‚Â»Ă‚Â§a user cÄ‚Â¡Ă‚ÂºĂ‚Â§n chÄ‚Â¡Ă‚ÂºĂ‚Â·n
     * @return UserResponse vÄ‚Â¡Ă‚Â»Ă¢â‚¬Âºi blocked = true
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse blockUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        user.setBlocked(true);
        log.info("Admin Ä‚â€Ă¢â‚¬ËœĂ„â€Ă‚Â£ chÄ‚Â¡Ă‚ÂºĂ‚Â·n tĂ„â€Ă‚Â i khoÄ‚Â¡Ă‚ÂºĂ‚Â£n user: {}", user.getUsername());
        return userMapper.toUserResponse(userRepository.save(user));
    }

    /**
     * HuÄ‚Â¡Ă‚Â»Ă‚Â· chÄ‚Â¡Ă‚ÂºĂ‚Â·n tĂ„â€Ă‚Â i khoÄ‚Â¡Ă‚ÂºĂ‚Â£n ngÄ‚â€ Ă‚Â°Ä‚Â¡Ă‚Â»Ă‚Âi dĂ„â€Ă‚Â¹ng Ä‚Â¢Ă¢â€Â¬Ă¢â‚¬Â chÄ‚Â¡Ă‚Â»Ă¢â‚¬Â° ADMIN Ä‚â€Ă¢â‚¬ËœÄ‚â€ Ă‚Â°Ä‚Â¡Ă‚Â»Ă‚Â£c phĂ„â€Ă‚Â©p.
     *
     * @param userId ID cÄ‚Â¡Ă‚Â»Ă‚Â§a user cÄ‚Â¡Ă‚ÂºĂ‚Â§n mÄ‚Â¡Ă‚Â»Ă…Â¸ khoĂ„â€Ă‚Â¡
     * @return UserResponse vÄ‚Â¡Ă‚Â»Ă¢â‚¬Âºi blocked = false
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse unblockUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        user.setBlocked(false);
        log.info("Admin Ä‚â€Ă¢â‚¬ËœĂ„â€Ă‚Â£ mÄ‚Â¡Ă‚Â»Ă…Â¸ khoĂ„â€Ă‚Â¡ tĂ„â€Ă‚Â i khoÄ‚Â¡Ă‚ÂºĂ‚Â£n user: {}", user.getUsername());
        return userMapper.toUserResponse(userRepository.save(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> getUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<User> pageUser = userRepository.findAll(pageable);

        // MAPPING THÄ‚Â¡Ă‚Â»Ă‚Â¦ CĂ„â€Ă¢â‚¬ÂNG TÄ‚Â¡Ă‚Â»Ă‚Âª ENTITY SANG DTO
        List<UserResponse> userResponses = pageUser.getContent().stream()
                .map(user -> {
                    UserResponse res = new UserResponse();
                    res.setId(user.getId());
                    res.setUsername(user.getUsername());
                    res.setFirstName(user.getFirstName()); // Map tay tÄ‚Â¡Ă‚Â»Ă‚Â«ng trÄ‚â€ Ă‚Â°Ä‚Â¡Ă‚Â»Ă‚Âng
                    res.setLastName(user.getLastName());
                    res.setDob(user.getDob());
                    res.setImageUrl(user.getImageUrl());
                    res.setBlocked(user.isBlocked()); // TrÄ‚Â¡Ă‚ÂºĂ‚Â¡ng thĂ„â€Ă‚Â¡i chÄ‚Â¡Ă‚ÂºĂ‚Â·n/mÄ‚Â¡Ă‚Â»Ă…Â¸ khoĂ„â€Ă‚Â¡

                    // NÄ‚Â¡Ă‚ÂºĂ‚Â¿u roles lĂ„â€Ă‚Â  mÄ‚Â¡Ă‚Â»Ă¢â€Â¢t Set<Role>, bÄ‚Â¡Ă‚ÂºĂ‚Â¡n cÄ‚â€¦Ă‚Â©ng phÄ‚Â¡Ă‚ÂºĂ‚Â£i map nĂ„â€Ă‚Â³ qua RoleResponse
                    if (user.getRoles() != null) {
                        res.setRoles(user.getRoles().stream()
                                .map(role -> {
                                    RoleResponse roleRes = new RoleResponse();
                                    roleRes.setName(role.getName());
                                    roleRes.setDescription(role.getDescription());
                                    return roleRes;
                                })
                                .collect(Collectors.toSet()));
                    }

                    return res;
                })
                .toList();

        return new PageResponse<>(
                page,
                pageUser.getTotalPages(),
                pageUser.getSize(),
                pageUser.getTotalElements(),
                userResponses
        );
    }


    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getUser(String id) {
        return userMapper.toUserResponse(
                userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED)));
    }
    @Transactional
    public UserResponse uploadProfileImage(String userId, MultipartFile image) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String currentUsername = authentication != null ? authentication.getName() : null;

        if (!isAdmin && (currentUsername == null || !currentUsername.equals(user.getUsername()))) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (image == null || image.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
        Map uploadResult = cloudinary.uploader().upload(image.getBytes(),
                ObjectUtils.asMap("folder", "user_app/images"));

        String imageUrl = uploadResult.get("secure_url").toString();
        user.setImageUrl(imageUrl);
        return userMapper.toUserResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        // LÄ‚Â¡Ă‚ÂºĂ‚Â¥y username tÄ‚Â¡Ă‚Â»Ă‚Â« token cÄ‚Â¡Ă‚Â»Ă‚Â§a ngÄ‚â€ Ă‚Â°Ä‚Â¡Ă‚Â»Ă‚Âi Ä‚â€Ă¢â‚¬Ëœang Ä‚â€Ă¢â‚¬ËœÄ‚â€Ă†â€™ng nhÄ‚Â¡Ă‚ÂºĂ‚Â­p
        var context = SecurityContextHolder.getContext();
        String username = context.getAuthentication().getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // KiÄ‚Â¡Ă‚Â»Ă†â€™m tra mÄ‚Â¡Ă‚ÂºĂ‚Â­t khÄ‚Â¡Ă‚ÂºĂ‚Â©u cÄ‚â€¦Ă‚Â© cĂ„â€Ă‚Â³ Ä‚â€Ă¢â‚¬ËœĂ„â€Ă‚Âºng khĂ„â€Ă‚Â´ng
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.WRONG_PASSWORD);
        }

        // KiÄ‚Â¡Ă‚Â»Ă†â€™m tra mÄ‚Â¡Ă‚ÂºĂ‚Â­t khÄ‚Â¡Ă‚ÂºĂ‚Â©u mÄ‚Â¡Ă‚Â»Ă¢â‚¬Âºi vĂ„â€Ă‚Â  xĂ„â€Ă‚Â¡c nhÄ‚Â¡Ă‚ÂºĂ‚Â­n mÄ‚Â¡Ă‚ÂºĂ‚Â­t khÄ‚Â¡Ă‚ÂºĂ‚Â©u cĂ„â€Ă‚Â³ khÄ‚Â¡Ă‚Â»Ă¢â‚¬Âºp khĂ„â€Ă‚Â´ng
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        // MĂ„â€Ă‚Â£ hĂ„â€Ă‚Â³a vĂ„â€Ă‚Â  lÄ‚â€ Ă‚Â°u mÄ‚Â¡Ă‚ÂºĂ‚Â­t khÄ‚Â¡Ă‚ÂºĂ‚Â©u mÄ‚Â¡Ă‚Â»Ă¢â‚¬Âºi
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("User {} Ä‚â€Ă¢â‚¬ËœĂ„â€Ă‚Â£ Ä‚â€Ă¢â‚¬ËœÄ‚Â¡Ă‚Â»Ă¢â‚¬Â¢i mÄ‚Â¡Ă‚ÂºĂ‚Â­t khÄ‚Â¡Ă‚ÂºĂ‚Â©u thĂ„â€Ă‚Â nh cĂ„â€Ă‚Â´ng", username);
    }
}
