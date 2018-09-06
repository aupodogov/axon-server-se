package io.axoniq.axonhub.localstorage.query;

import io.axoniq.axondb.query.Query;
import io.axoniq.axondb.query.QueryElement;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;

/**
 * Author: marc
 */
public class QueryProcessor {

    private static ExpressionRegistry expressionRegistry = new ExpressionRegistry();

    public Pipeline buildPipeline(Query query, Function<QueryResult, Boolean> terminal) {
        List<? extends QueryElement> queryElements = query == null ? Collections.emptyList() : query.getParameters();
        if (queryElements.isEmpty()) {
            return terminal::apply;
        }

        Queue<PipeExpression> pipeExpressions = new LinkedList<>();
        for (QueryElement pipelineEntry : queryElements) {
            pipeExpressions.add(expressionRegistry.resolvePipeExpression(pipelineEntry));
        }
        return new ChainedPipeExpression(pipeExpressions, terminal);
    }


    private class ChainedPipeExpression implements Pipeline {

        private final PipeExpression expression;
        private final Pipeline next;
        private final ExpressionContext context;

        public ChainedPipeExpression(Queue<PipeExpression> pipeExpressions, Function<QueryResult, Boolean> terminal) {
            expression = pipeExpressions.poll();
            if (pipeExpressions.isEmpty()) {
                next = terminal::apply;
            } else {
                next = new ChainedPipeExpression(pipeExpressions, terminal);
            }
            this.context = new ExpressionContext();
        }

        @Override
        public boolean process(QueryResult value) {
            return expression.process(context, value, next);
        }

        @Override
        public List<String> columnNames(List<String> inputColumns) {
            if( next == null) return expression.getColumnNames(inputColumns);
            return next.columnNames(expression.getColumnNames(inputColumns));
        }
    }
}
