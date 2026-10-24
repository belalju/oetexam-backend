package com.oet.payment.service;

import com.oet.common.exception.NotFoundException;
import com.oet.payment.client.SslCommerzClient;
import com.oet.payment.config.SslCommerzProperties;
import com.oet.payment.dto.CreatePaymentSessionRequest;
import com.oet.payment.dto.PaymentSessionResponse;
import com.oet.payment.dto.SslCommerzSessionResponse;
import com.oet.payment.entity.Payment;
import com.oet.payment.entity.PricingPackage;
import com.oet.payment.exception.PaymentValidationException;
import com.oet.payment.repository.PaymentRepository;
import com.oet.user.entity.User;
import com.oet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String EMAIL = "applicant@example.com";

    @Mock
    private SslCommerzClient sslCommerzClient;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserRepository userRepository;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        SslCommerzProperties properties = new SslCommerzProperties(
                "https://sandbox.sslcommerz.com", "store", "secret",
                "https://app/success", "https://app/fail", "https://app/cancel", "https://app/payment", null,
                Duration.ofSeconds(10), Duration.ofSeconds(30));
        service = new PaymentService(sslCommerzClient, properties, paymentRepository, userRepository);
    }

    @Test
    void createSession_matchingPrice_chargesServerPriceAndStoresPackage() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(User.builder().email(EMAIL).build()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sslCommerzClient.createSession(any())).thenReturn(
                new SslCommerzSessionResponse("SUCCESS", null, "SESSION1", "https://gateway/pay"));

        PaymentSessionResponse response = service.createSession(request("silver", "39.990", "USD"), EMAIL);

        assertThat(response.gatewayPageUrl()).isEqualTo("https://gateway/pay");
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getPricingPackage()).isEqualTo(PricingPackage.SILVER);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("39.99");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultiValueMap<String, String>> params = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(sslCommerzClient).createSession(params.capture());
        assertThat(params.getValue().getFirst("total_amount")).isEqualTo("39.99");
        assertThat(params.getValue().getFirst("product_name")).isEqualTo("Silver Package");
    }

    @Test
    void createSession_tamperedAmount_isRejectedBeforeAnyPaymentIsCreated() {
        assertThatThrownBy(() -> service.createSession(request("gold", "10.00", "USD"), EMAIL))
                .isInstanceOf(PaymentValidationException.class);
        verify(paymentRepository, never()).save(any());
        verify(sslCommerzClient, never()).createSession(any());
    }

    @Test
    void createSession_wrongCurrency_isRejected() {
        assertThatThrownBy(() -> service.createSession(request("gold", "54.99", "BDT"), EMAIL))
                .isInstanceOf(PaymentValidationException.class);
    }

    @Test
    void createSession_unknownPackage_throwsNotFound() {
        assertThatThrownBy(() -> service.createSession(request("platinum", "99.99", "USD"), EMAIL))
                .isInstanceOf(NotFoundException.class);
    }

    private CreatePaymentSessionRequest request(String packageId, String amount, String currency) {
        return new CreatePaymentSessionRequest(packageId, new BigDecimal(amount), currency,
                "Jane Doe", "01700000000", "House 1, Road 2", "Dhaka", "1207", "Bangladesh");
    }
}
