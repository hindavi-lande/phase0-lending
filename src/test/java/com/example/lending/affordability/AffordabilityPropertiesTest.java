package com.example.lending.affordability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AffordabilityPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesTestConfiguration.class);

    @Test
    void defaultsBindWhenNoPropertiesArePresent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            AffordabilityProperties properties = context.getBean(AffordabilityProperties.class);
            assertThat(properties.getDefaultAnnualInterestRate()).isEqualByComparingTo("12.50");
            assertThat(properties.getPreferredMaxDti()).isEqualByComparingTo("40.00");
            assertThat(properties.getHardMaxDti()).isEqualByComparingTo("50.00");
        });
    }

    @Test
    void propertyOverridesBind() {
        contextRunner
                .withPropertyValues(
                        "lending.affordability.default-annual-interest-rate=10.25",
                        "lending.affordability.preferred-max-dti=35.00",
                        "lending.affordability.hard-max-dti=45.00")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AffordabilityProperties properties = context.getBean(AffordabilityProperties.class);
                    assertThat(properties.getDefaultAnnualInterestRate()).isEqualByComparingTo("10.25");
                    assertThat(properties.getPreferredMaxDti()).isEqualByComparingTo("35.00");
                    assertThat(properties.getHardMaxDti()).isEqualByComparingTo("45.00");
                });
    }

    @Test
    void preferredThresholdEqualToHardThresholdIsRejected() {
        contextRunner
                .withPropertyValues("lending.affordability.preferred-max-dti=50.00")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void preferredThresholdAboveHardThresholdIsRejected() {
        contextRunner
                .withPropertyValues("lending.affordability.preferred-max-dti=55.00")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void preferredThresholdOutsideZeroToOneHundredIsRejected() {
        assertRejected("lending.affordability.preferred-max-dti", "-0.01");
        assertRejected("lending.affordability.preferred-max-dti", "100.01");
    }

    @Test
    void hardThresholdOutsideZeroToOneHundredIsRejected() {
        assertRejected("lending.affordability.hard-max-dti", "-0.01");
        assertRejected("lending.affordability.hard-max-dti", "100.01");
    }

    @Test
    void defaultRateOutsideZeroToThirtySixIsRejected() {
        assertRejected("lending.affordability.default-annual-interest-rate", "-0.01");
        assertRejected("lending.affordability.default-annual-interest-rate", "36.01");
    }

    private void assertRejected(String property, String value) {
        contextRunner
                .withPropertyValues(property + "=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @TestConfiguration
    @EnableConfigurationProperties(AffordabilityProperties.class)
    static class PropertiesTestConfiguration {
    }
}
