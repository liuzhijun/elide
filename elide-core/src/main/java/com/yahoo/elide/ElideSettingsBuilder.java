/*
 * Copyright 2017, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide;

import com.yahoo.elide.core.RequestScope;
import com.yahoo.elide.core.audit.AuditLogger;
import com.yahoo.elide.core.audit.Slf4jLogger;
import com.yahoo.elide.core.datastore.DataStore;
import com.yahoo.elide.core.dictionary.EntityDictionary;
import com.yahoo.elide.core.exceptions.ErrorMapper;
import com.yahoo.elide.core.exceptions.HttpStatus;
import com.yahoo.elide.core.filter.dialect.RSQLFilterDialect;
import com.yahoo.elide.core.filter.dialect.graphql.FilterDialect;
import com.yahoo.elide.core.filter.dialect.jsonapi.DefaultFilterDialect;
import com.yahoo.elide.core.filter.dialect.jsonapi.JoinFilterDialect;
import com.yahoo.elide.core.filter.dialect.jsonapi.SubqueryFilterDialect;
import com.yahoo.elide.core.pagination.PaginationImpl;
import com.yahoo.elide.core.security.PermissionExecutor;
import com.yahoo.elide.core.security.executors.ActivePermissionExecutor;
import com.yahoo.elide.core.security.executors.VerbosePermissionExecutor;
import com.yahoo.elide.core.utils.coerce.converters.EpochToDateConverter;
import com.yahoo.elide.core.utils.coerce.converters.ISO8601DateSerde;
import com.yahoo.elide.core.utils.coerce.converters.InstantSerde;
import com.yahoo.elide.core.utils.coerce.converters.OffsetDateTimeSerde;
import com.yahoo.elide.core.utils.coerce.converters.Serde;
import com.yahoo.elide.core.utils.coerce.converters.TimeZoneSerde;
import com.yahoo.elide.core.utils.coerce.converters.URLSerde;
import com.yahoo.elide.jsonapi.JsonApiMapper;
import com.yahoo.elide.jsonapi.links.JSONApiLinks;
import com.yahoo.elide.utils.HeaderUtils;

import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;

/**
 * Builder for ElideSettings.
 */
public class ElideSettingsBuilder {
    private final DataStore dataStore;
    private AuditLogger auditLogger;
    private JsonApiMapper jsonApiMapper;
    private ErrorMapper errorMapper;
    private EntityDictionary entityDictionary;
    private Function<RequestScope, PermissionExecutor> permissionExecutorFunction = ActivePermissionExecutor::new;
    private List<JoinFilterDialect> joinFilterDialects;
    private List<SubqueryFilterDialect> subqueryFilterDialects;
    private FilterDialect graphqlFilterDialect;
    private JSONApiLinks jsonApiLinks;
    private HeaderUtils.HeaderProcessor headerProcessor;
    private Map<Class, Serde> serdes;
    private int defaultMaxPageSize = PaginationImpl.MAX_PAGE_LIMIT;
    private int defaultPageSize = PaginationImpl.DEFAULT_PAGE_LIMIT;
    private int updateStatusCode;
    private boolean enableJsonLinks;
    private boolean strictQueryParams = true;
    private String baseUrl = "";
    private String jsonApiPath;
    private String graphQLApiPath;
    private String exportApiPath;

    /**
     * A new builder used to generate Elide instances. Instantiates an {@link EntityDictionary} without
     * providing a mapping of security checks and uses the provided {@link Slf4jLogger} for audit.
     *
     * @param dataStore the datastore used to communicate with the persistence layer
     */
    public ElideSettingsBuilder(DataStore dataStore) {
        this.dataStore = dataStore;
        this.auditLogger = new Slf4jLogger();
        this.jsonApiMapper = new JsonApiMapper();
        this.joinFilterDialects = new ArrayList<>();
        this.subqueryFilterDialects = new ArrayList<>();
        this.headerProcessor = HeaderUtils::lowercaseAndRemoveAuthHeaders;
        updateStatusCode = HttpStatus.SC_NO_CONTENT;
        this.serdes = new LinkedHashMap<>();
        this.enableJsonLinks = false;

        //By default, Elide supports epoch based dates.
        this.withEpochDates();
        this.withDefaultSerdes();
    }

    public ElideSettings build() {
        if (joinFilterDialects.isEmpty()) {
            joinFilterDialects.add(new DefaultFilterDialect(entityDictionary));
            joinFilterDialects.add(RSQLFilterDialect.builder().dictionary(entityDictionary).build());
        }

        if (subqueryFilterDialects.isEmpty()) {
            subqueryFilterDialects.add(new DefaultFilterDialect(entityDictionary));
            subqueryFilterDialects.add(RSQLFilterDialect.builder().dictionary(entityDictionary).build());
        }

        if (graphqlFilterDialect == null) {
            graphqlFilterDialect = RSQLFilterDialect.builder().dictionary(entityDictionary).build();
        }

        if (entityDictionary == null) {
            throw new IllegalStateException("EntityDictionary must be set in ElideSettings.");
        }

        return new ElideSettings(
                auditLogger,
                dataStore,
                entityDictionary,
                jsonApiMapper,
                errorMapper,
                permissionExecutorFunction,
                joinFilterDialects,
                subqueryFilterDialects,
                graphqlFilterDialect,
                jsonApiLinks,
                headerProcessor,
                defaultMaxPageSize,
                defaultPageSize,
                updateStatusCode,
                serdes,
                enableJsonLinks,
                strictQueryParams,
                baseUrl,
                jsonApiPath,
                graphQLApiPath,
                exportApiPath);
    }

    public ElideSettingsBuilder withAuditLogger(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
        return this;
    }

    public ElideSettingsBuilder withEntityDictionary(EntityDictionary entityDictionary) {
        this.entityDictionary = entityDictionary;
        return this;
    }

    public ElideSettingsBuilder withJsonApiMapper(JsonApiMapper jsonApiMapper) {
        this.jsonApiMapper = jsonApiMapper;
        return this;
    }


    public ElideSettingsBuilder withErrorMapper(ErrorMapper errorMapper) {
        this.errorMapper = errorMapper;
        return this;
    }

    public ElideSettingsBuilder withJoinFilterDialect(JoinFilterDialect dialect) {
        joinFilterDialects.add(dialect);
        return this;
    }

    public ElideSettingsBuilder withSubqueryFilterDialect(SubqueryFilterDialect dialect) {
        subqueryFilterDialects.add(dialect);
        return this;
    }

    public ElideSettingsBuilder withDefaultMaxPageSize(int maxPageSize) {
        defaultMaxPageSize = maxPageSize;
        return this;
    }

    public ElideSettingsBuilder withDefaultPageSize(int pageSize) {
        defaultPageSize = pageSize;
        return this;
    }

    public ElideSettingsBuilder withUpdate200Status() {
        updateStatusCode = HttpStatus.SC_OK;
        return this;
    }

    public ElideSettingsBuilder withUpdate204Status() {
        updateStatusCode = HttpStatus.SC_NO_CONTENT;
        return this;
    }

    /**
     * Sets the base URL that will be returned in URLs generated by Elide.
     * @param baseUrl The base URL that clients use in queries.
     * @return the settings builder.
     */
    public ElideSettingsBuilder withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public ElideSettingsBuilder withGraphQLDialect(FilterDialect dialect) {
        graphqlFilterDialect = dialect;
        return this;
    }

    public ElideSettingsBuilder withVerboseErrors() {
        permissionExecutorFunction = VerbosePermissionExecutor::new;
        return this;
    }

    public ElideSettingsBuilder withISO8601Dates(String dateFormat, TimeZone tz) {
        serdes.put(Date.class, new ISO8601DateSerde(dateFormat, tz));
        serdes.put(java.sql.Date.class, new ISO8601DateSerde(dateFormat, tz, java.sql.Date.class));
        serdes.put(java.sql.Time.class, new ISO8601DateSerde(dateFormat, tz, java.sql.Time.class));
        serdes.put(java.sql.Timestamp.class, new ISO8601DateSerde(dateFormat, tz, java.sql.Timestamp.class));
        return this;
    }

    public ElideSettingsBuilder withEpochDates() {
        serdes.put(Date.class, new EpochToDateConverter<>(Date.class));
        serdes.put(java.sql.Date.class, new EpochToDateConverter<>(java.sql.Date.class));
        serdes.put(java.sql.Time.class, new EpochToDateConverter<>(java.sql.Time.class));
        serdes.put(java.sql.Timestamp.class, new EpochToDateConverter<>(java.sql.Timestamp.class));
        return this;
    }

    public ElideSettingsBuilder withDefaultSerdes() {
        serdes.put(Instant.class, new InstantSerde());
        serdes.put(OffsetDateTime.class, new OffsetDateTimeSerde());
        serdes.put(TimeZone.class, new TimeZoneSerde());
        serdes.put(URL.class, new URLSerde());
        return this;
    }

    public ElideSettingsBuilder withJSONApiLinks(JSONApiLinks links) {
        this.enableJsonLinks = true;
        this.jsonApiLinks = links;
        return this;
    }

    public ElideSettingsBuilder withHeaderProcessor(HeaderUtils.HeaderProcessor headerProcessor) {
        this.headerProcessor = headerProcessor;
        return this;
    }

    public ElideSettingsBuilder withJsonApiPath(String jsonApiPath) {
        this.jsonApiPath = jsonApiPath;
        return this;
    }

    public ElideSettingsBuilder withGraphQLApiPath(String graphQLApiPath) {
        this.graphQLApiPath = graphQLApiPath;
        return this;
    }

    public ElideSettingsBuilder withExportApiPath(String exportApiPath) {
        this.exportApiPath = exportApiPath;
        return this;
    }

    public ElideSettingsBuilder withStrictQueryParams(boolean enabled) {
        this.strictQueryParams = enabled;
        return this;
    }
}
