package io.axoniq.axonhub.localstorage.query.expressions.functions;

import io.axoniq.axonhub.localstorage.query.ExpressionContext;
import io.axoniq.axonhub.localstorage.query.ExpressionResult;
import io.axoniq.axonhub.localstorage.query.expressions.Identifier;
import org.junit.*;

import java.math.BigDecimal;

import static io.axoniq.axonhub.localstorage.query.expressions.ResultFactory.*;
import static org.junit.Assert.*;

public class AvgExpressionTest {

    private AvgExpression testSubject;
    private ExpressionContext expressionContext;

    @Before
    public void setUp() {
        testSubject = new AvgExpression("average", new Identifier("value"));
        expressionContext = new ExpressionContext();
    }

    @Test
    public void testNoValueAvailableLeavesEmptyAverage() {
        ExpressionResult actual = testSubject.apply(expressionContext, emptyMapValue());
        assertTrue(actual.isNumeric());
        assertNull(actual.getValue());
    }

    @Test
    public void testAdditionalValuesModifyAverage() {
        ExpressionResult actual1 = testSubject.apply(expressionContext, mapValue("value", numericValue(1L)));
        ExpressionResult actual2 = testSubject.apply(expressionContext, mapValue("value", numericValue(2L)));
        ExpressionResult actual3 = testSubject.apply(expressionContext, mapValue("value", numericValue(6L)));

        assertEquals(BigDecimal.ONE, actual1.getNumericValue());
        assertEquals(BigDecimal.valueOf(15, 1), actual2.getNumericValue());
        assertEquals(BigDecimal.valueOf(3).toPlainString(), actual3.getNumericValue().toPlainString());
    }

    @Test
    public void testNonNumericValuesKeepExistingAverage() {
        ExpressionResult actual1 = testSubject.apply(expressionContext, mapValue("value", numericValue(1L)));
        ExpressionResult actual2 = testSubject.apply(expressionContext, mapValue("value", stringValue("string")));
        ExpressionResult actual3 = testSubject.apply(expressionContext, mapValue("value", numericValue(19L)));

        assertEquals(BigDecimal.ONE, actual1.getNumericValue());
        assertEquals(BigDecimal.ONE, actual2.getNumericValue());
        assertEquals(BigDecimal.TEN.toPlainString(), actual3.getNumericValue().toPlainString());
    }

    @Test
    public void testNullValuesKeepExistingAverage() {
        ExpressionResult actual1 = testSubject.apply(expressionContext, mapValue("value", numericValue(1L)));
        ExpressionResult actual2 = testSubject.apply(expressionContext, mapValue("value", nullValue()));
        ExpressionResult actual3 = testSubject.apply(expressionContext, mapValue("value", numericValue(19L)));

        assertEquals(BigDecimal.ONE, actual1.getNumericValue());
        assertEquals(BigDecimal.ONE, actual2.getNumericValue());
        assertEquals(BigDecimal.TEN.toPlainString(), actual3.getNumericValue().toPlainString());
    }

}
