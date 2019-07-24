package com.flutterwave.raveandroid.validators;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class AmountValidatorTest {

    AmountValidator SUT;

    @Before
    public void setUp() throws Exception {
        SUT = new AmountValidator();
    }

    @Test
    public void isAmountvalid_isCorrectAmountPassed_returnsTrue(){
        Double amount = 1.0;
        boolean isAmountValid = SUT.isAmountValid(amount);
        assertThat(true, is(isAmountValid));
    }

    @Test
    public void isAmountvalid_isNegativeAmountPassed_returnsFalse(){
        Double amount = -1.0;
        boolean isAmountValid = SUT.isAmountValid(amount);
        assertThat(false, is(isAmountValid));
    }

}