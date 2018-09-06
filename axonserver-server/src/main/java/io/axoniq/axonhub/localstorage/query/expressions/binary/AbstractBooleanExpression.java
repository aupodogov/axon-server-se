package io.axoniq.axonhub.localstorage.query.expressions.binary;

import io.axoniq.axonhub.localstorage.query.Expression;
import io.axoniq.axonhub.localstorage.query.ExpressionContext;
import io.axoniq.axonhub.localstorage.query.ExpressionResult;
import io.axoniq.axonhub.localstorage.query.PipeExpression;
import io.axoniq.axonhub.localstorage.query.Pipeline;
import io.axoniq.axonhub.localstorage.query.QueryResult;
import io.axoniq.axonhub.localstorage.query.result.BooleanExpressionResult;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractBooleanExpression implements PipeExpression, Expression {

    private final String alias;
    protected final Expression first;
    protected final Expression second;

    public AbstractBooleanExpression(String alias, Expression[] parameters) {
        this.alias = alias;
        this.first = parameters[0];
        this.second = parameters[1];
    }

    @Override
    public ExpressionResult apply(ExpressionContext context, ExpressionResult data) {
        ExpressionResult val1 = first.apply(context, data);
        ExpressionResult val2 = second.apply(context, data);
        return BooleanExpressionResult.forValue(doEvaluate(val1, val2));
    }

    protected abstract boolean doEvaluate(ExpressionResult first, ExpressionResult second);

    @Override
    public String alias() {
        return alias;
    }

    @Override
    public boolean process(ExpressionContext context, QueryResult result, Pipeline next) {
        if (apply(context, result.getValue()).isTrue()) {
            if (result.getId() != null) {
                sentIdentifiers(context).add(result.getId());
            }
            return next.process(result);
        } else if (result.getId() != null && sentIdentifiers(context).remove(result.getId())) {
            return next.process(result.deleted());
        }
        return true;
    }

    private Set<ExpressionResult> sentIdentifiers(ExpressionContext context) {
        return context.scoped(this).computeIfAbsent("results", () -> new ConcurrentHashMap<ExpressionResult, Object>().keySet(new Object()));
    }
}
