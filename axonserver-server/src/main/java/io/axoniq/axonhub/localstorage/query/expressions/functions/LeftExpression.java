package io.axoniq.axonhub.localstorage.query.expressions.functions;

import io.axoniq.axonhub.localstorage.query.Expression;
import io.axoniq.axonhub.localstorage.query.ExpressionContext;
import io.axoniq.axonhub.localstorage.query.ExpressionResult;
import io.axoniq.axonhub.localstorage.query.result.NullExpressionResult;
import io.axoniq.axonhub.localstorage.query.result.StringExpressionResult;

/**
 * Author: marc
 * left(value, chars)
 */
public class LeftExpression implements Expression {

    private final String alias;
    private final Expression valueExpression;
    private final Expression charsExpression;

    public LeftExpression(String alias, Expression[] expressions) {
        this.alias = alias;
        this.valueExpression = expressions[0];
        this.charsExpression = expressions[1];
    }

    @Override
    public ExpressionResult apply(ExpressionContext context, ExpressionResult input) {
        ExpressionResult value = valueExpression.apply(context, input);
        if( value == null || ! value.isNonNull() ) return  NullExpressionResult.INSTANCE;

        int chars = charsExpression.apply(context, input).getNumericValue().intValue();

        String string = value.toString();
        if( string.length() < chars) return new StringExpressionResult(string);
        return new StringExpressionResult(string.substring(0, chars));
    }

    @Override
    public String alias() {
        return alias;
    }

}
