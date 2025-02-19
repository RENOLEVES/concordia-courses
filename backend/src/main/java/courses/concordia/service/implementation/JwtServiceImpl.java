package courses.concordia.service.implementation;

import courses.concordia.config.JwtConfigProperties;
import courses.concordia.config.RtConfigProperties;
import courses.concordia.config.TokenType;
import courses.concordia.service.JwtService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
public class JwtServiceImpl implements JwtService {
    private final JwtConfigProperties jwtConfigProperties;
    private final RtConfigProperties rtConfigProperties;
    private final Key signInKey;
    private final Key refreshKey;


    public JwtServiceImpl(JwtConfigProperties jwtConfigProperties, RtConfigProperties rtConfigProperties) {
        this.jwtConfigProperties = jwtConfigProperties;
        this.signInKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtConfigProperties.getSecret()));
        this.rtConfigProperties = rtConfigProperties;
        this.refreshKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(rtConfigProperties.getSecret()));
    }

    @Override
    public String extractUsername(String token) {
        return extractUsername(token, TokenType.ACCESS_TOKEN);
    }
    @Override
    public String extractUsername(String token, TokenType tokenType) {
        return extractClaim(token, Claims::getSubject, tokenType);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver, TokenType tokenType) {
        final Claims claims = extractAllClaims(token, tokenType);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails, TokenType tokenType) {
        return generateToken(new HashMap<>(), userDetails, tokenType);
    }

    public String generateToken(
            Map<String, Object> claims,
            UserDetails userDetails,
            TokenType tokenType
    ) {
        long expirationTimeLong = 0;
        if(tokenType == TokenType.ACCESS_TOKEN) {
            expirationTimeLong = jwtConfigProperties.getExp() * 1000; // Convert seconds to milliseconds
        }if(tokenType == TokenType.REFRESH_TOKEN){
            expirationTimeLong = rtConfigProperties.getExp() * 1000;
        }
        final Date createdDate = new Date();
        final Date expirationDate = new Date(createdDate.getTime() + expirationTimeLong);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(createdDate)
                .setExpiration(expirationDate)
                .signWith(tokenType == TokenType.ACCESS_TOKEN ? signInKey : refreshKey,
                        SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails, TokenType tokenType) {
        final String username = extractUsername(token,tokenType);
        return (username.equals(userDetails.getUsername())) &&
                !isTokenExpired(token, tokenType);
    }

    public Date extractExpiration(String token, TokenType tokenType) {
        return extractClaim(token, Claims::getExpiration, tokenType);
    }
    public boolean isTokenExpired(String token, TokenType tokenType) {
        return extractExpiration(token,tokenType).before(new Date());
    }

    private Claims extractAllClaims(String token, TokenType tokenType) throws JwtException{
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(tokenType == TokenType.ACCESS_TOKEN ? signInKey : refreshKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.info("Token expired: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.error("Could not parse token: {}", e.getMessage());
            throw e;
        }
    }
}
