package courses.concordia.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import javax.persistence.FetchType;
import javax.persistence.OneToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Random;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Document(collection = "tokens")
public class Token {
    @MongoId
    @OneToOne(targetEntity = User.class, fetch = FetchType.EAGER)
    private User user;
    private String token;

    @Temporal(TemporalType.TIMESTAMP)
    @CreatedDate
    private LocalDateTime createTime;
    private LocalDateTime expireTime;


    public Token(User user){
        this.user = user;
        this.createTime = LocalDateTime.now();
        this.expireTime = createTime.plusMinutes(5);
        this.token = tokenGenerator();
    }

    public boolean isExpired(){
        return LocalDateTime.now().isAfter(expireTime);
    }

    public String tokenGenerator(){
        Random random = new Random();
        StringBuilder str= new StringBuilder();
        for(int i = 0; i < 6; i ++){
            str.append(random.nextInt(10));
        }
        return str.toString();
    }
}
