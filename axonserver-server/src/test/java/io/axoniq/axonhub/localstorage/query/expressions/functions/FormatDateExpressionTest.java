package io.axoniq.axonhub.localstorage.query.expressions.functions;

import io.axoniq.axonhub.localstorage.query.ExpressionContext;
import io.axoniq.axonhub.localstorage.query.ExpressionResult;
import io.axoniq.axonhub.localstorage.query.expressions.Identifier;
import io.axoniq.axonhub.localstorage.query.expressions.StringLiteral;
import org.junit.*;

import static io.axoniq.axonhub.localstorage.query.expressions.ResultFactory.*;
import static org.junit.Assert.*;

/**
 * Author: marc
 */
public class FormatDateExpressionTest {
    private FormatDateExpression testSubject;
    private ExpressionContext expressionContext;

    @Before
    public void setUp() {
        testSubject = new FormatDateExpression(null, new Identifier("value"),
                new Identifier("format"), new StringLiteral("UTC"));
        expressionContext = new ExpressionContext();
    }

    @Test
    public void apply() {
        ExpressionResult actual = testSubject.apply(expressionContext, mapValue("value", numericValue(1511442724079L),
                "format", stringValue("dd-MM-yyyy")));
        assertEquals("23-11-2017", actual.getValue());
    }

    @Test
    public void applyDay() {
        ExpressionResult actual = testSubject.apply(expressionContext, mapValue("value", numericValue(1511442724079L),
                "format", stringValue("F")));
        assertEquals("4", actual.getValue());
    }

    @Test
    public void applyWeek() {
        ExpressionResult actual = testSubject.apply(expressionContext, mapValue("value", numericValue(1511442724079L),
                "format", stringValue("w")));
        assertEquals("47", actual.getValue());
    }

    @Test
    public void applyMonth() {
        ExpressionResult actual = testSubject.apply(expressionContext, mapValue("value", numericValue(1511442724079L),
                "format", stringValue("MM")));
        assertEquals("11", actual.getValue());
    }

    @Test
    public void applyYear() {
        ExpressionResult actual = testSubject.apply(expressionContext, mapValue("value", numericValue(1511442724079L),
                "format", stringValue("yyyy")));
        assertEquals("2017", actual.getValue());
    }

    @Test
    public void applyHour() {
        ExpressionResult actual = testSubject.apply(expressionContext, mapValue("value", numericValue(1511442724079L),
                "format", stringValue("HH")));
        assertEquals("13", actual.getValue());
    }
}