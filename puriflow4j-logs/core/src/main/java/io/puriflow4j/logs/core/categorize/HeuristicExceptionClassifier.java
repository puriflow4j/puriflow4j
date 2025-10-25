/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core.categorize;

import io.puriflow4j.logs.core.model.ThrowableView;

/** Lightweight heuristics based on class names/packages. */
public final class HeuristicExceptionClassifier implements ExceptionClassifier {

    @Override
    public CategoryResult classify(ThrowableView tv) {
        String cn = tv.className();
        if (cn == null) return CategoryResult.NONE;

        String lcn = cn.toLowerCase();

        // DB-like
        if (lcn.contains("sqlexception")
                || lcn.contains("jdbc")
                || lcn.contains("postgres")
                || lcn.contains("mysql")
                || lcn.contains("oracle")
                || lcn.contains("mongo")
                || lcn.contains("redis")
                || lcn.contains("sql")
                || lcn.contains("datasource")) {
            return new CategoryResult("DB");
        }

        // JSON
        if (lcn.contains("json") || lcn.contains("jackson")) {
            return new CategoryResult("JSON");
        }

        // HTTP / servlet
        if (lcn.contains("http") || lcn.contains("servlet") || lcn.contains("feign") || lcn.contains("restclient")) {
            return new CategoryResult("HTTP");
        }

        // IO / filesystem / sockets
        if (lcn.endsWith("ioexception")
                || lcn.contains("socket")
                || lcn.contains("channel")
                || lcn.contains("nio")
                || lcn.contains("io.")) {
            return new CategoryResult("IO");
        }

        // Security / auth
        if (lcn.contains("accessdenied")
                || lcn.contains("authentication")
                || lcn.contains("authorization")
                || lcn.contains("security")
                || lcn.contains("forbidden")
                || lcn.contains("unauthor")) {
            return new CategoryResult("SECURITY");
        }

        // Timeouts
        if (lcn.contains("timeout") || lcn.contains("timedout")) {
            return new CategoryResult("TIMEOUT");
        }

        return CategoryResult.NONE;
    }
}
