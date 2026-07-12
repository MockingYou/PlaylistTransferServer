package com.app.transfer.user;

import com.app.transfer.domain.StreamingProvider;
import com.app.transfer.security.TokenEncryptionConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "provider_connection", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"}))
@Getter
@Setter
@NoArgsConstructor
public class ProviderConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private StreamingProvider provider;

    @Convert(converter = TokenEncryptionConverter.class)
    @Column(length = 2048)
    private String refreshToken;

    private Instant updatedAt = Instant.now();
}