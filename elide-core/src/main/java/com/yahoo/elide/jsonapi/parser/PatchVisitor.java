/*
 * Copyright 2015, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.jsonapi.parser;

import com.yahoo.elide.core.RequestScope;
import com.yahoo.elide.generated.parsers.CoreParser.QueryContext;
import com.yahoo.elide.jsonapi.models.JsonApiDocument;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Supplier;

/**
 * PATCH handler.
 */
public class PatchVisitor extends BaseVisitor {

    /**
     * Constructor.
     *
     * @param requestScope the request scope
     */
    public PatchVisitor(RequestScope requestScope) {
        super(requestScope);
    }

    @Override
    public Supplier<Pair<Integer, JsonApiDocument>> visitQuery(QueryContext ctx) {
        return state.handlePatch();
    }
}
