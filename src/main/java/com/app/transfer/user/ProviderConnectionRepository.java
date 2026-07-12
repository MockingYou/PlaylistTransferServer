package com.app.transfer.user;

import com.app.transfer.domain.StreamingProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderConnectionRepository extends JpaRepository<ProviderConnection, Long> {
    Optional<ProviderConnection> findByUserAndProvider(User user, StreamingProvider provider);
}