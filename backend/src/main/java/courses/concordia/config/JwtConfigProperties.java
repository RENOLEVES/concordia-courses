package courses.concordia.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
@Component
public class JwtConfigProperties {
    private String tokenName;
    private String secret;
    private long exp;
}