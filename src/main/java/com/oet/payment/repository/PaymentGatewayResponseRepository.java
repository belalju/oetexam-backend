package com.oet.payment.repository;

import com.oet.payment.entity.PaymentGatewayResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentGatewayResponseRepository extends JpaRepository<PaymentGatewayResponse, Long> {

    List<PaymentGatewayResponse> findByTransactionIdOrderByCreatedAtAsc(String transactionId);
}
