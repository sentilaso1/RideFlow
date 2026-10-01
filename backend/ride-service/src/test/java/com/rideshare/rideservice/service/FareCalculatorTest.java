package com.rideshare.rideservice.service;

import com.rideshare.rideservice.config.FareProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FareCalculatorTest {

    private final FareCalculator calculator =
            new FareCalculator(new FareProperties(new BigDecimal("50"), new BigDecimal("12"), "INR"));

    @Test
    void samePointCostsBaseFare() {
        FareCalculator.FareQuote quote = calculator.quote(12.97, 77.59, 12.97, 77.59);
        assertThat(quote.distanceKm()).isZero();
        assertThat(quote.fare()).isEqualByComparingTo("50.00");
        assertThat(quote.currency()).isEqualTo("INR");
    }

    @Test
    void mgRoadToKoramangalaIsAboutFiveKm() {
        // Sample trip used by the web app.
        FareCalculator.FareQuote quote = calculator.quote(12.9716, 77.5946, 12.9352, 77.6245);
        assertThat(quote.distanceKm()).isCloseTo(5.18, within(0.05));
        assertThat(quote.fare()).isEqualByComparingTo(
                new BigDecimal("50").add(new BigDecimal("12").multiply(BigDecimal.valueOf(
                        FareCalculator.haversineKm(12.9716, 77.5946, 12.9352, 77.6245))))
                        .setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(quote.fare().scale()).isEqualTo(2);
    }

    @Test
    void haversineMatchesKnownDistance() {
        // London -> Paris is roughly 344 km.
        assertThat(FareCalculator.haversineKm(51.5074, -0.1278, 48.8566, 2.3522)).isCloseTo(343.5, within(1.0));
    }
}
