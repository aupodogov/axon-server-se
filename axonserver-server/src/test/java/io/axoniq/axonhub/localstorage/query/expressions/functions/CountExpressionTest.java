package io.axoniq.axonhub.localstorage.query.expressions.functions;

import io.axoniq.axonhub.localstorage.query.ExpressionContext;
import io.axoniq.axonhub.localstorage.query.ExpressionResult;
import io.axoniq.axonhub.localstorage.query.expressions.Identifier;
import org.junit.*;

import static io.axoniq.axonhub.localstorage.query.expressions.ResultFactory.*;
import static org.junit.Assert.*;

public class CountExpressionTest {

    private CountExpression testSubject;
    private ExpressionContext expressionContext;

    @Before
    public void setUp() {
        testSubject = new CountExpression("count", new Identifier("value"));
        expressionContext = new ExpressionContext();
    }

    @Test
    public void testCountValues() {
        ExpressionResult actual1 = testSubject.apply(expressionContext, mapValue("value", stringValue("string")));
        ExpressionResult actual2 = testSubject.apply(expressionContext, mapValue("value", numericValue(1L)));
        ExpressionResult actual3 = testSubject.apply(expressionContext, mapValue("value", stringValue("string")));
        assertEquals(1, actual1.getNumericValue().longValue());
        assertEquals(2, actual2.getNumericValue().longValue());
        assertEquals(3, actual3.getNumericValue().longValue());
    }

    @Test
    public void testNullValuesNotCounted() {
        ExpressionResult actual1 = testSubject.apply(expressionContext, mapValue("value", stringValue("string")));
        ExpressionResult actual2 = testSubject.apply(expressionContext, mapValue("value", nullValue()));
        ExpressionResult actual3 = testSubject.apply(expressionContext, mapValue("value", numericValue(1L)));
        assertEquals(1, actual1.getNumericValue().longValue());
        assertEquals(1, actual2.getNumericValue().longValue());
        assertEquals(2, actual3.getNumericValue().longValue());
    }

    @Test
    public void testCountIncreasedByCollectionSize() {
        ExpressionResult actual1 = testSubject.apply(expressionContext, mapValue("value", stringValue("string")));
        ExpressionResult actual2 = testSubject.apply(expressionContext, mapValue("value", listValue(listValue(), nullValue(), stringValue("string"))));
        ExpressionResult actual3 = testSubject.apply(expressionContext, mapValue("value", numericValue(1L)));
        assertEquals(1, actual1.getNumericValue().longValue());
        // emptly list and stringValue are counted. nullValue isn't
        assertEquals(3, actual2.getNumericValue().longValue());
        assertEquals(4, actual3.getNumericValue().longValue());
    }
}
