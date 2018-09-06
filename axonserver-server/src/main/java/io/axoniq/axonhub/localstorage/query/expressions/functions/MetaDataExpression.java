package io.axoniq.axonhub.localstorage.query.expressions.functions;

import io.axoniq.axonhub.localstorage.query.Expression;
import io.axoniq.axonhub.localstorage.query.ExpressionContext;
import io.axoniq.axonhub.localstorage.query.ExpressionResult;

import java.util.Objects;

public class MetaDataExpression implements Expression {

    private final String alias;
    private final Expression[] expressions;

    public MetaDataExpression(String alias, Expression[] keyPath) {
        this.alias = alias == null ? buildAlias(keyPath) : alias;
        this.expressions = keyPath;
    }

    private String buildAlias(Expression[] keyPath) {
        StringBuilder sb = new StringBuilder("metaData");
        for (Expression expression : keyPath) {
            sb.append(".");
            sb.append(expression.alias());
        }
        return sb.toString();
    }

    @Override
    public ExpressionResult apply(ExpressionContext expressionContext, ExpressionResult input) {
        ExpressionResult result = input.getByIdentifier("metaData");
        for (Expression id : expressions) {
            String identifier = Objects.toString(id.apply(expressionContext, input).getValue());
            result = result.getByIdentifier(identifier);
        }
        return result;
    }

    @Override
    public String alias() {
        return alias;
    }
}
