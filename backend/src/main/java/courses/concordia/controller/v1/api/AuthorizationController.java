package courses.concordia.controller.v1.api;

import courses.concordia.config.JwtConfigProperties;
import courses.concordia.config.RtConfigProperties;
import courses.concordia.config.TokenType;
import courses.concordia.controller.v1.request.AuthenticationRequest;
import courses.concordia.controller.v1.request.LoginRequest;
import courses.concordia.controller.v1.request.SignupRequest;
import courses.concordia.dto.model.user.UserDto;
import courses.concordia.dto.model.user.UserResponseDto;
import courses.concordia.dto.response.AuthenticationResponse;
import courses.concordia.dto.response.Response;
import courses.concordia.model.Token;
import courses.concordia.repository.TokenRepository;
import courses.concordia.service.JwtService;
import courses.concordia.service.TokenBlacklistService;
import courses.concordia.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

import static courses.concordia.util.Misc.getTokenFromCookie;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthorizationController {
    private final UserService userService;
    private final JwtService jwtService;
    private final TokenRepository tokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtConfigProperties jwtConfigProperties;
    private final RtConfigProperties rtConfigProperties;

    @GetMapping("/user")
    public Response<?> getUser(HttpServletRequest request) {
        String token = getTokenFromCookie(request, jwtConfigProperties.getTokenName());
        if (token == null || tokenBlacklistService.isTokenBlacklisted(token)) {
            return Response.unauthorized();
        }

        String username = jwtService.extractUsername(token);
        boolean isVerified = userService.isUserVerified(username);
        String userId = userService.getUserIdFromUsername(username);

        return Response.ok().setPayload(new UserResponseDto(userId, username, isVerified));
    }

    @PostMapping("/signin")
    public Response<?> signIn(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        AuthenticationResponse res = userService.authenticate(loginRequest);
        addTokenCookie(response, res.getToken(),TokenType.accessToken);
        addTokenCookie(response, res.getRefreshToken(),TokenType.refreshToken);
        return Response.ok().setPayload("Boom! You're in");
    }

    @GetMapping("/signout")
    public Response<?> signOut(HttpServletRequest request, HttpServletResponse response) {
        String token = getTokenFromCookie(request, jwtConfigProperties.getTokenName());

        if (token == null || tokenBlacklistService.isTokenBlacklisted(token)) {
            return Response.unauthorized();
        }

        Date expiration = jwtService.extractExpiration(token);
        long durationInSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
        if (durationInSeconds > 0) {
            tokenBlacklistService.blacklistToken(token, durationInSeconds);
        }
        clearTokenCookie(response);

        return Response.ok().setPayload("And you're out! Come back soon!");
    }

    @PostMapping("/signup")
    public Response<?> signUp(@Valid @RequestBody SignupRequest signupRequest, HttpServletResponse response) {

        if (!userService.checkIfUserExist(signupRequest.getUsername())) {
            if(!signupRequest.getEmail().endsWith("concordia.ca")){
                return Response.badRequest().setErrors("Email must be a Concordia email address");
            }
        }
        UserDto userDto = new UserDto(
                signupRequest.getUsername(),
                signupRequest.getEmail(),
                signupRequest.getPassword(),
                false
        );
        AuthenticationResponse res = userService.signup(userDto);

        addTokenCookie(response, res.getToken(),TokenType.accessToken);
        addTokenCookie(response, res.getRefreshToken(),TokenType.refreshToken);
        
        return Response.ok().setPayload("Almost there! Just need to verify your email to make sure it's really you.");
    }

    @PostMapping("/authorized")
    public Response<?> confirmUserAccount(@Valid @RequestBody AuthenticationRequest authenticationRequest) {
        String token = authenticationRequest.getToken();
        Token t = tokenRepository.findByToken(token).orElse(null);

        if(t == null) {
            return Response.validationException().setPayload("Oops! Wrong token. Let's retry with the correct one.");
        }

        if(t.isExpired()) {
            //if the token is expired, delete the token
            tokenRepository.delete(t);
            return Response.badRequest().setPayload("Whoops! Looks like this token has expired. Time to grab a fresh one!");
        }

        userService.verifyToken(token);
        return Response.ok().setPayload("Welcome aboard! You've successfully joined the cool zone.");
    }

    @GetMapping("/resend_token")
    public Response<?> resendVerificationToken(HttpServletRequest request) {
        String token = getTokenFromCookie(request, jwtConfigProperties.getTokenName());
        if (token == null) {
            return Response.unauthorized();
        }

        String username = jwtService.extractUsername(token);

        userService.resendToken(username);
        return Response.ok().setPayload("Your new code is in your inbox!");
    }

    private void clearTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(jwtConfigProperties.getTokenName(), null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0); // Expire the cookie immediately
        response.addCookie(cookie);
    }

    private void addTokenCookie(HttpServletResponse response, String token, TokenType tokenType) {
        ResponseCookie cookie = ResponseCookie.from(
                tokenType == TokenType.accessToken ?
                        jwtConfigProperties.getTokenName() : rtConfigProperties.getTokenName(), token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(tokenType == TokenType.accessToken ?
                        jwtConfigProperties.getExp() : rtConfigProperties.getExp())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

