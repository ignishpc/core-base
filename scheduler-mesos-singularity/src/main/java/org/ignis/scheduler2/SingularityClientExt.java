package org.ignis.scheduler2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.rholder.retry.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hubspot.horizon.HttpClient;
import com.hubspot.horizon.HttpRequest;
import com.hubspot.horizon.HttpRequest.Method;
import com.hubspot.horizon.HttpResponse;
import com.hubspot.horizon.RetryStrategy;
import com.hubspot.mesos.json.MesosFileChunkObject;
import com.hubspot.singularity.*;
import com.hubspot.singularity.api.*;
import com.hubspot.singularity.client.SingularityClientException;
import com.hubspot.singularity.client.SingularityClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@SuppressWarnings("unchecked")
public class SingularityClientExt {
    private static final Logger LOG = LoggerFactory.getLogger(SingularityClientExt.class);

    private static final String BASE_API_FORMAT = "%s://%s/%s";

    private static final String AUTH_FORMAT = "%s/auth";
    private static final String AUTH_CHECK_FORMAT = AUTH_FORMAT + "/%s/auth-check";
    private static final String AUTH_CHECK_USER_FORMAT = AUTH_CHECK_FORMAT + "/%s";
    private static final String AUTH_GROUPS_CHECK_FORMAT =
            AUTH_FORMAT + "/groups/auth-check";

    private static final String STATE_FORMAT = "%s/state";
    private static final String TASK_RECONCILIATION_FORMAT =
            STATE_FORMAT + "/task-reconciliation";

    private static final String USAGE_FORMAT = "%s/usage";
    private static final String CLUSTER_UTILIZATION_FORMAT =
            USAGE_FORMAT + "/cluster/utilization";

    private static final String RACKS_FORMAT = "%s/racks";
    private static final String RACKS_DECOMISSION_FORMAT =
            RACKS_FORMAT + "/rack/%s/decommission";
    private static final String RACKS_FREEZE_FORMAT = RACKS_FORMAT + "/rack/%s/freeze";
    private static final String RACKS_ACTIVATE_FORMAT = RACKS_FORMAT + "/rack/%s/activate";
    private static final String RACKS_DELETE_FORMAT = RACKS_FORMAT + "/rack/%s";

    private static final String AGENTS_FORMAT = "%s/agents";
    private static final String AGENT_DETAIL_FORMAT = AGENTS_FORMAT + "/agent/%s/details";
    private static final String AGENTS_DECOMISSION_FORMAT =
            AGENTS_FORMAT + "/agent/%s/decommission";
    private static final String AGENTS_FREEZE_FORMAT = AGENTS_FORMAT + "/agent/%s/freeze";
    private static final String AGENTS_ACTIVATE_FORMAT =
            AGENTS_FORMAT + "/agent/%s/activate";
    private static final String AGENTS_DELETE_FORMAT = AGENTS_FORMAT + "/agent/%s";

    private static final String INACTIVE_AGENTS_FORMAT = "%s/inactive";

    private static final String TASKS_FORMAT = "%s/tasks";
    private static final String TASKS_KILL_TASK_FORMAT = TASKS_FORMAT + "/task/%s";
    private static final String TASKS_GET_ACTIVE_FORMAT = TASKS_FORMAT + "/active";
    private static final String TASKS_GET_ACTIVE_IDS_FORMAT =
            TASKS_GET_ACTIVE_FORMAT + "/ids";
    private static final String TASKS_GET_ACTIVE_STATES_FORMAT =
            TASKS_GET_ACTIVE_FORMAT + "/states";
    private static final String TASKS_GET_ACTIVE_ON_AGENT_FORMAT =
            TASKS_FORMAT + "/active/slave/%s";
    private static final String TASKS_GET_ACTIVE_IDS_ON_SLAVE_FORMAT =
            TASKS_GET_ACTIVE_ON_AGENT_FORMAT + "/ids";
    private static final String TASKS_GET_SCHEDULED_FORMAT = TASKS_FORMAT + "/scheduled";
    private static final String TASKS_GET_SCHEDULED_IDS_FORMAT =
            TASKS_GET_SCHEDULED_FORMAT + "/ids";
    private static final String TASKS_BY_STATE_FORMAT = TASKS_FORMAT + "/ids/request/%s";
    private static final String TASK_COUNTS_BY_STATE_FORMAT =
            TASKS_FORMAT + "/counts/request/%s";

    private static final String SHELL_COMMAND_FORMAT = TASKS_FORMAT + "/task/%s/command";
    private static final String SHELL_COMMAND_UPDATES_FORMAT =
            SHELL_COMMAND_FORMAT + "/%s/%s";

    private static final String HISTORY_FORMAT = "%s/history";
    private static final String TASKS_HISTORY_FORMAT = HISTORY_FORMAT + "/tasks";
    private static final String TASKS_HISTORY_WITHMETADATA_FORMAT =
            HISTORY_FORMAT + "/tasks/withmetadata";
    private static final String TASK_HISTORY_FORMAT = HISTORY_FORMAT + "/task/%s";
    private static final String REQUEST_HISTORY_FORMAT =
            HISTORY_FORMAT + "/request/%s/requests";
    private static final String TASK_HISTORY_BY_RUN_ID_FORMAT =
            HISTORY_FORMAT + "/request/%s/run/%s";
    private static final String REQUEST_ACTIVE_TASKS_HISTORY_FORMAT =
            HISTORY_FORMAT + "/request/%s/tasks/active";
    private static final String REQUEST_INACTIVE_TASKS_HISTORY_FORMAT =
            HISTORY_FORMAT + "/request/%s/tasks";
    private static final String REQUEST_DEPLOY_HISTORY_FORMAT =
            HISTORY_FORMAT + "/request/%s/deploy/%s";

    private static final String TASK_TRACKER_FORMAT = "%s/track";
    private static final String TRACK_BY_TASK_ID_FORMAT = TASK_TRACKER_FORMAT + "/task/%s";
    private static final String TRACK_BY_RUN_ID_FORMAT = TASK_TRACKER_FORMAT + "/run/%s/%s";

    private static final String REQUESTS_FORMAT = "%s/requests";
    private static final String REQUESTS_GET_BATCH_FORMAT = REQUESTS_FORMAT + "/batch";
    private static final String REQUESTS_GET_ACTIVE_FORMAT = REQUESTS_FORMAT + "/active";
    private static final String REQUESTS_GET_PAUSED_FORMAT = REQUESTS_FORMAT + "/paused";
    private static final String REQUESTS_GET_COOLDOWN_FORMAT =
            REQUESTS_FORMAT + "/cooldown";
    private static final String REQUESTS_GET_PENDING_FORMAT =
            REQUESTS_FORMAT + "/queued/pending";
    private static final String REQUESTS_GET_CLEANUP_FORMAT =
            REQUESTS_FORMAT + "/queued/cleanup";

    private static final String REQUEST_GROUPS_FORMAT = "%s/groups";
    private static final String REQUEST_GROUP_FORMAT = REQUEST_GROUPS_FORMAT + "/group/%s";

    private static final String REQUEST_GET_FORMAT = REQUESTS_FORMAT + "/request/%s";
    private static final String REQUEST_GET_SIMPLE_FORMAT =
            REQUESTS_FORMAT + "/request/%s/simple";
    private static final String REQUEST_CREATE_OR_UPDATE_FORMAT = REQUESTS_FORMAT;
    private static final String REQUEST_BY_RUN_ID_FORMAT = REQUEST_GET_FORMAT + "/run/%s";
    private static final String REQUEST_DELETE_ACTIVE_FORMAT =
            REQUESTS_FORMAT + "/request/%s";
    private static final String REQUEST_BOUNCE_FORMAT =
            REQUESTS_FORMAT + "/request/%s/bounce";
    private static final String REQUEST_PAUSE_FORMAT =
            REQUESTS_FORMAT + "/request/%s/pause";
    private static final String REQUEST_UNPAUSE_FORMAT =
            REQUESTS_FORMAT + "/request/%s/unpause";
    private static final String REQUEST_SCALE_FORMAT =
            REQUESTS_FORMAT + "/request/%s/scale";
    private static final String REQUEST_RUN_FORMAT = REQUESTS_FORMAT + "/request/%s/run";
    private static final String REQUEST_EXIT_COOLDOWN_FORMAT =
            REQUESTS_FORMAT + "/request/%s/exit-cooldown";
    private static final String REQUEST_GROUPS_UPDATE_FORMAT =
            REQUESTS_FORMAT + "/request/%s/groups";
    private static final String REQUEST_GROUPS_UPDATE_AUTH_CHECK_FORMAT =
            REQUEST_GROUPS_UPDATE_FORMAT + "/auth-check";

    private static final String CRASH_LOOPS = REQUESTS_FORMAT + "/crashloops";
    private static final String REQUEST_CRASH_LOOPS = REQUEST_GET_FORMAT + "/crashloops";

    private static final String DEPLOYS_FORMAT = "%s/deploys";
    private static final String DELETE_DEPLOY_FORMAT =
            DEPLOYS_FORMAT + "/deploy/%s/request/%s";
    private static final String UPDATE_DEPLOY_FORMAT = DEPLOYS_FORMAT + "/update";

    private static final String WEBHOOKS_FORMAT = "%s/webhooks";
    private static final String WEBHOOKS_DELETE_FORMAT = WEBHOOKS_FORMAT;
    private static final String WEBHOOKS_GET_QUEUED_DEPLOY_UPDATES_FORMAT =
            WEBHOOKS_FORMAT + "/deploy";
    private static final String WEBHOOKS_GET_QUEUED_REQUEST_UPDATES_FORMAT =
            WEBHOOKS_FORMAT + "/request";
    private static final String WEBHOOKS_GET_QUEUED_TASK_UPDATES_FORMAT =
            WEBHOOKS_FORMAT + "/task";

    private static final String SANDBOX_FORMAT = "%s/sandbox";
    private static final String SANDBOX_BROWSE_FORMAT = SANDBOX_FORMAT + "/%s/browse";
    private static final String SANDBOX_READ_FILE_FORMAT = SANDBOX_FORMAT + "/%s/read";

    private static final String S3_LOG_FORMAT = "%s/logs";
    private static final String S3_LOG_SEARCH_LOGS = S3_LOG_FORMAT + "/search";
    private static final String S3_LOG_GET_TASK_LOGS = S3_LOG_FORMAT + "/task/%s";
    private static final String S3_LOG_GET_REQUEST_LOGS = S3_LOG_FORMAT + "/request/%s";
    private static final String S3_LOG_GET_DEPLOY_LOGS =
            S3_LOG_FORMAT + "/request/%s/deploy/%s";

    private static final String DISASTERS_FORMAT = "%s/disasters";
    private static final String DISASTER_STATS_FORMAT = DISASTERS_FORMAT + "/stats";
    private static final String ACTIVE_DISASTERS_FORMAT = DISASTERS_FORMAT + "/active";
    private static final String DISABLE_AUTOMATED_ACTIONS_FORMAT =
            DISASTERS_FORMAT + "/disable";
    private static final String ENABLE_AUTOMATED_ACTIONS_FORMAT =
            DISASTERS_FORMAT + "/enable";
    private static final String DISASTER_FORMAT = DISASTERS_FORMAT + "/active/%s";
    private static final String DISABLED_ACTIONS_FORMAT =
            DISASTERS_FORMAT + "/disabled-actions";
    private static final String DISABLED_ACTION_FORMAT =
            DISASTERS_FORMAT + "/disabled-actions/%s";

    private static final String PRIORITY_FORMAT = "%s/priority";
    private static final String PRIORITY_FREEZE_FORMAT = PRIORITY_FORMAT + "/freeze";

    private static final TypeReference<Collection<SingularityRequestParent>> REQUESTS_COLLECTION = new TypeReference<Collection<SingularityRequestParent>>() {
    };
    private static final TypeReference<Collection<SingularityPendingRequest>> PENDING_REQUESTS_COLLECTION = new TypeReference<Collection<SingularityPendingRequest>>() {
    };
    private static final TypeReference<Collection<SingularityRequestCleanup>> CLEANUP_REQUESTS_COLLECTION = new TypeReference<Collection<SingularityRequestCleanup>>() {
    };
    private static final TypeReference<Collection<SingularityTask>> TASKS_COLLECTION = new TypeReference<Collection<SingularityTask>>() {
    };
    private static final TypeReference<Collection<SingularityTaskId>> TASK_IDS_COLLECTION = new TypeReference<Collection<SingularityTaskId>>() {
    };
    private static final TypeReference<Collection<SingularityTaskIdHistory>> TASKID_HISTORY_COLLECTION = new TypeReference<Collection<SingularityTaskIdHistory>>() {
    };
    private static final TypeReference<Collection<SingularityRack>> RACKS_COLLECTION = new TypeReference<Collection<SingularityRack>>() {
    };
    private static final TypeReference<Collection<SingularityAgent>> AGENTS_COLLECTION = new TypeReference<Collection<SingularityAgent>>() {
    };
    private static final TypeReference<Collection<SingularityWebhook>> WEBHOOKS_COLLECTION = new TypeReference<Collection<SingularityWebhook>>() {
    };
    private static final TypeReference<Collection<SingularityDeployUpdate>> DEPLOY_UPDATES_COLLECTION = new TypeReference<Collection<SingularityDeployUpdate>>() {
    };
    private static final TypeReference<Collection<SingularityRequestHistory>> REQUEST_UPDATES_COLLECTION = new TypeReference<Collection<SingularityRequestHistory>>() {
    };
    private static final TypeReference<Collection<SingularityTaskHistoryUpdate>> TASK_UPDATES_COLLECTION = new TypeReference<Collection<SingularityTaskHistoryUpdate>>() {
    };
    private static final TypeReference<Collection<SingularityTaskRequest>> TASKS_REQUEST_COLLECTION = new TypeReference<Collection<SingularityTaskRequest>>() {
    };
    private static final TypeReference<Collection<SingularityTaskShellCommandHistory>> SHELL_COMMAND_HISTORY = new TypeReference<Collection<SingularityTaskShellCommandHistory>>() {
    };
    private static final TypeReference<Collection<SingularityTaskShellCommandUpdate>> SHELL_COMMAND_UPDATES = new TypeReference<Collection<SingularityTaskShellCommandUpdate>>() {
    };
    private static final TypeReference<Collection<SingularityPendingTaskId>> PENDING_TASK_ID_COLLECTION = new TypeReference<Collection<SingularityPendingTaskId>>() {
    };
    private static final TypeReference<Collection<SingularityS3Log>> S3_LOG_COLLECTION = new TypeReference<Collection<SingularityS3Log>>() {
    };
    private static final TypeReference<Collection<SingularityRequestHistory>> REQUEST_HISTORY_COLLECTION = new TypeReference<Collection<SingularityRequestHistory>>() {
    };
    private static final TypeReference<Collection<SingularityRequestGroup>> REQUEST_GROUP_COLLECTION = new TypeReference<Collection<SingularityRequestGroup>>() {
    };
    private static final TypeReference<Collection<SingularityDisasterType>> DISASTERS_COLLECTION = new TypeReference<Collection<SingularityDisasterType>>() {
    };
    private static final TypeReference<Collection<SingularityDisabledAction>> DISABLED_ACTIONS_COLLECTION = new TypeReference<Collection<SingularityDisabledAction>>() {
    };
    private static final TypeReference<SingularityPaginatedResponse<SingularityTaskIdHistory>> PAGINATED_HISTORY = new TypeReference<SingularityPaginatedResponse<SingularityTaskIdHistory>>() {
    };
    private static final TypeReference<Collection<String>> STRING_COLLECTION = new TypeReference<Collection<String>>() {
    };
    private static final TypeReference<Collection<CrashLoopInfo>> CRASH_LOOP_INFO_COLLECTION = new TypeReference<Collection<CrashLoopInfo>>() {
    };
    private static final TypeReference<Map<String, List<CrashLoopInfo>>> CRASH_LOOP_MAP = new TypeReference<Map<String, List<CrashLoopInfo>>>() {
    };

    private final Random random;
    private final Provider<List<String>> hostsProvider;
    private final String contextPath;
    private final boolean ssl;

    private final HttpClient httpClient;
    private final Optional<SingularityClientCredentials> credentials;

    private final Retryer<HttpResponse> httpResponseRetryer;

    @Inject
    @Deprecated
    public SingularityClientExt(
            @Named(SingularityClientModule.CONTEXT_PATH) String contextPath,
            @Named(SingularityClientModule.HTTP_CLIENT_NAME) HttpClient httpClient,
            @Named(SingularityClientModule.HOSTS_PROPERTY_NAME) String hosts
    ) {
        this(contextPath, httpClient, Arrays.asList(hosts.split(",")), Optional.empty());
    }

    public SingularityClientExt(
            String contextPath,
            HttpClient httpClient,
            List<String> hosts,
            Optional<SingularityClientCredentials> credentials
    ) {
        this(
                contextPath,
                httpClient,
                ProviderUtils.of(ImmutableList.copyOf(hosts)),
                credentials
        );
    }

    public SingularityClientExt(
            String contextPath,
            HttpClient httpClient,
            Provider<List<String>> hostsProvider,
            Optional<SingularityClientCredentials> credentials
    ) {
        this(contextPath, httpClient, hostsProvider, credentials, false);
    }

    public SingularityClientExt(
            String contextPath,
            HttpClient httpClient,
            List<String> hosts,
            Optional<SingularityClientCredentials> credentials,
            boolean ssl
    ) {
        this(
                contextPath,
                httpClient,
                ProviderUtils.of(ImmutableList.copyOf(hosts)),
                credentials,
                ssl
        );
    }

    public SingularityClientExt(
            String contextPath,
            HttpClient httpClient,
            Provider<List<String>> hostsProvider,
            Optional<SingularityClientCredentials> credentials,
            boolean ssl
    ) {
        this(
                contextPath,
                httpClient,
                hostsProvider,
                credentials,
                ssl,
                3,
                HttpResponse::isServerError
        );
    }

    public SingularityClientExt(
            String contextPath,
            HttpClient httpClient,
            Provider<List<String>> hostsProvider,
            Optional<SingularityClientCredentials> credentials,
            boolean ssl,
            int retryAttempts,
            Predicate<HttpResponse> retryStrategy
    ) {
        this.httpClient = httpClient;
        this.contextPath = contextPath;

        this.hostsProvider = hostsProvider;
        this.random = new Random();

        this.credentials = credentials;
        this.ssl = ssl;

        this.httpResponseRetryer =
                RetryerBuilder
                        .<HttpResponse>newBuilder()
                        .withStopStrategy(StopStrategies.stopAfterAttempt(retryAttempts))
                        .withWaitStrategy(WaitStrategies.exponentialWait())
                        .retryIfResult(retryStrategy::test)
                        .retryIfException()
                        .build();
    }

    private String getApiBase(String host) {
        return String.format(BASE_API_FORMAT, ssl ? "https" : "http", host, contextPath);
    }

    //
    // HttpClient Methods
    //

    private void checkResponse(String type, HttpResponse response) {
        if (response.isError()) {
            throw fail(type, response);
        }
    }

    private SingularityClientException fail(String type, HttpResponse response) {
        String body = "";

        try {
            body = response.getAsString();
        } catch (Exception e) {
            LOG.warn("Unable to read body", e);
        }

        String uri = "";

        try {
            uri = response.getRequest().getUrl().toString();
        } catch (Exception e) {
            LOG.warn("Unable to read uri", e);
        }

        throw new SingularityClientException(
                String.format(
                        "Failed '%s' action on Singularity (%s) - code: %s, %s",
                        type,
                        uri,
                        response.getStatusCode(),
                        body
                ),
                response.getStatusCode()
        );
    }

    private <T> Optional<T> getSingle(
            Function<String, String> hostToUrl,
            String type,
            String id,
            Class<T> clazz
    ) {
        return getSingleWithParams(hostToUrl, type, id, Optional.empty(), clazz);
    }

    private <T> Optional<T> getSingleWithParams(
            Function<String, String> hostToUrl,
            String type,
            String id,
            Optional<Map<String, Object>> queryParams,
            Class<T> clazz
    ) {
        final long start = System.currentTimeMillis();
        HttpResponse response = executeGetSingleWithParams(hostToUrl, type, id, queryParams);

        if (response.getStatusCode() == 404) {
            return Optional.empty();
        }

        checkResponse(type, response);
        LOG.info("Got {} {} in {}ms", type, id, System.currentTimeMillis() - start);

        return Optional.ofNullable(response.getAs(clazz));
    }

    private <T> T getAs(
            Function<String, String> hostToUrl,
            String type,
            String id,
            Optional<Map<String, Object>> queryParams,
            TypeReference<T> ref
    ) {
        final long start = System.currentTimeMillis();
        HttpResponse response = executeGetSingleWithParams(hostToUrl, type, id, queryParams);

        checkResponse(type, response);
        LOG.info("Got {} {} in {}ms", type, id, System.currentTimeMillis() - start);

        return response.getAs(ref);
    }

    private <T> Optional<T> getSingleWithParams(
            Function<String, String> hostToUrl,
            String type,
            String id,
            Optional<Map<String, Object>> queryParams,
            TypeReference<T> typeReference
    ) {
        final long start = System.currentTimeMillis();
        HttpResponse response = executeGetSingleWithParams(hostToUrl, type, id, queryParams);

        if (response.getStatusCode() == 404) {
            return Optional.empty();
        }

        checkResponse(type, response);
        LOG.info("Got {} {} in {}ms", type, id, System.currentTimeMillis() - start);

        return Optional.ofNullable(response.getAs(typeReference));
    }

    private HttpResponse executeGetSingleWithParams(
            Function<String, String> hostToUrl,
            String type,
            String id,
            Optional<Map<String, Object>> queryParams
    ) {
        checkNotNull(id, String.format("Provide a %s id", type));

        LOG.info("Getting {} {} from Singularity host", type, id);

        return executeRequest(
                hostToUrl,
                Method.GET,
                Optional.empty(),
                queryParams.orElse(Collections.emptyMap())
        );
    }

    private <T> Collection<T> getCollection(
            Function<String, String> hostToUrl,
            String type,
            TypeReference<Collection<T>> typeReference
    ) {
        return getCollectionWithParams(hostToUrl, type, Optional.empty(), typeReference);
    }

    private <T> Collection<T> getCollectionWithParams(
            Function<String, String> hostToUrl,
            String type,
            Optional<Map<String, Object>> queryParams,
            TypeReference<Collection<T>> typeReference
    ) {
        final long start = System.currentTimeMillis();

        HttpResponse response = executeRequest(
                hostToUrl,
                Method.GET,
                Optional.empty(),
                queryParams.orElse(Collections.emptyMap())
        );

        if (response.getStatusCode() == 404) {
            return ImmutableList.of();
        }

        checkResponse(type, response);

        LOG.info("Got {} in {}ms", type, System.currentTimeMillis() - start);

        return response.getAs(typeReference);
    }

    private void addQueryParams(
            HttpRequest.Builder requestBuilder,
            Map<String, ?> queryParams
    ) {
        for (Entry<String, ?> queryParamEntry : queryParams.entrySet()) {
            if (queryParamEntry.getValue() instanceof String) {
                requestBuilder
                        .setQueryParam(queryParamEntry.getKey())
                        .to((String) queryParamEntry.getValue());
            } else if (queryParamEntry.getValue() instanceof Integer) {
                requestBuilder
                        .setQueryParam(queryParamEntry.getKey())
                        .to((Integer) queryParamEntry.getValue());
            } else if (queryParamEntry.getValue() instanceof Long) {
                requestBuilder
                        .setQueryParam(queryParamEntry.getKey())
                        .to((Long) queryParamEntry.getValue());
            } else if (queryParamEntry.getValue() instanceof Boolean) {
                requestBuilder
                        .setQueryParam(queryParamEntry.getKey())
                        .to((Boolean) queryParamEntry.getValue());
            } else if (queryParamEntry.getValue() instanceof Set) {
                requestBuilder
                        .setQueryParam(queryParamEntry.getKey())
                        .to((Set) queryParamEntry.getValue());
            } else {
                throw new RuntimeException(
                        String.format(
                                "The type '%s' of query param %s is not supported. Only String, long, int, Set and boolean values are supported",
                                queryParamEntry.getValue().getClass().getName(),
                                queryParamEntry.getKey()
                        )
                );
            }
        }
    }

    private void addCredentials(HttpRequest.Builder requestBuilder) {
        if (credentials.isPresent()) {
            requestBuilder.addHeader(
                    credentials.get().getHeaderName(),
                    credentials.get().getToken()
            );
        }
    }

    private void delete(Function<String, String> hostToUrl, String type, String id) {
        delete(hostToUrl, type, id, Optional.empty());
    }

    private <T> void delete(
            Function<String, String> hostToUrl,
            String type,
            String id,
            Optional<?> body
    ) {
        delete(hostToUrl, type, id, body, Optional.<Class<T>>empty());
    }

    private <T> Optional<T> delete(
            Function<String, String> hostToUrl,
            String type,
            String id,
            Optional<?> body,
            Optional<Class<T>> clazz
    ) {
        return deleteWithParams(hostToUrl, type, id, body, Optional.empty(), clazz);
    }

    private <T> Optional<T> deleteWithParams(
            Function<String, String> hostToUrl,
            String type,
            String id,
            Optional<?> body,
            Optional<Map<String, Object>> queryParams,
            Optional<Class<T>> clazz
    ) {
        LOG.info("Deleting {} {} from Singularity", type, id);

        final long start = System.currentTimeMillis();

        HttpResponse response = executeRequest(
                hostToUrl,
                Method.DELETE,
                body,
                queryParams.orElse(Collections.emptyMap())
        );

        if (response.getStatusCode() == 404) {
            LOG.info("{} ({}) was not found", type, id);
            return Optional.empty();
        }

        checkResponse(type, response);

        LOG.info(
                "Deleted {} ({}) from Singularity in %sms",
                type,
                id,
                System.currentTimeMillis() - start
        );

        if (clazz.isPresent()) {
            return Optional.of(response.getAs(clazz.get()));
        }

        return Optional.empty();
    }

    private HttpResponse put(
            Function<String, String> hostToUri,
            String type,
            Optional<?> body
    ) {
        return executeRequest(hostToUri, type, body, Method.PUT, Optional.empty());
    }

    private <T> Optional<T> post(
            Function<String, String> hostToUri,
            String type,
            Optional<?> body,
            Optional<Class<T>> clazz
    ) {
        try {
            HttpResponse response = executeRequest(
                    hostToUri,
                    type,
                    body,
                    Method.POST,
                    Optional.empty()
            );

            if (clazz.isPresent()) {
                return Optional.of(response.getAs(clazz.get()));
            }
        } catch (Exception e) {
            LOG.warn("Http post failed", e);
        }

        return Optional.empty();
    }

    private HttpResponse postWithParams(
            Function<String, String> hostToUri,
            String type,
            Optional<?> body,
            Optional<Map<String, Object>> queryParams
    ) {
        return executeRequest(hostToUri, type, body, Method.POST, queryParams);
    }

    private HttpResponse post(
            Function<String, String> hostToUri,
            String type,
            Optional<?> body
    ) {
        return executeRequest(hostToUri, type, body, Method.POST, Optional.empty());
    }

    private HttpResponse post(
            Function<String, String> hostToUri,
            String type,
            Optional<?> body,
            Map<String, Object> queryParams
    ) {
        return executeRequest(hostToUri, type, body, Method.POST, Optional.of(queryParams));
    }

    private HttpResponse executeRequest(
            Function<String, String> hostToUri,
            String type,
            Optional<?> body,
            Method method,
            Optional<Map<String, Object>> queryParams
    ) {
        final long start = System.currentTimeMillis();

        HttpResponse response = executeRequest(
                hostToUri,
                method,
                body,
                queryParams.orElse(Collections.emptyMap())
        );
        checkResponse(type, response);
        LOG.info(
                "Successfully {}ed {} in {}ms",
                method,
                type,
                System.currentTimeMillis() - start
        );

        return response;
    }

    private HttpResponse executeRequest(
            Function<String, String> hostToUri,
            Method method,
            Optional<?> body,
            Map<String, ?> queryParams
    ) {
        HttpRequest.Builder request = HttpRequest.newBuilder().setMethod(method);

        if (body.isPresent()) {
            request.setBody(body.get());
        }

        addQueryParams(request, queryParams);
        addCredentials(request);

        List<String> hosts = new ArrayList<>(hostsProvider.get());
        request.setRetryStrategy(RetryStrategy.NEVER_RETRY).setMaxRetries(1);

        try {
            return httpResponseRetryer.call(
                    () -> {
                        if (hosts.isEmpty()) {
                            // We've tried everything we started with. Look again.
                            hosts.addAll(hostsProvider.get());
                        }

                        int selection = random.nextInt(hosts.size());
                        String host = hosts.get(selection);
                        String url = hostToUri.apply(host);
                        hosts.remove(selection);
                        LOG.info("Making {} request to {}", method, url);
                        request.setUrl(url);
                        return httpClient.execute(request.build());
                    }
            );
        } catch (ExecutionException | RetryException exn) {
            if (exn instanceof RetryException) {
                RetryException retryExn = (RetryException) exn;
                if (retryExn.getLastFailedAttempt().hasException()) {
                    LOG.error(
                            "Failed request to Singularity",
                            retryExn.getLastFailedAttempt().getExceptionCause()
                    );
                } else {
                    LOG.error("Failed request to Singularity", exn);
                }
            } else {
                LOG.error("Failed request to Singularity", exn);
            }
            throw new SingularityClientException("Failed request to Singularity", exn);
        }
    }

    //
    // GLOBAL
    //

    public SingularityState getState(
            Optional<Boolean> skipCache,
            Optional<Boolean> includeRequestIds
    ) {
        final Function<String, String> uri = host ->
                String.format(STATE_FORMAT, getApiBase(host));

        LOG.info("Fetching state from {}", uri);

        final long start = System.currentTimeMillis();

        Map<String, Boolean> queryParams = new HashMap<>();
        if (skipCache.isPresent()) {
            queryParams.put("skipCache", skipCache.get());
        }
        if (includeRequestIds.isPresent()) {
            queryParams.put("includeRequestIds", includeRequestIds.get());
        }

        HttpResponse response = executeRequest(
                uri,
                Method.GET,
                Optional.empty(),
                queryParams
        );

        checkResponse("state", response);

        LOG.info("Got state in {}ms", System.currentTimeMillis() - start);

        return response.getAs(SingularityState.class);
    }

    public Optional<SingularityTaskReconciliationStatistics> getTaskReconciliationStatistics() {
        final Function<String, String> uri = host ->
                String.format(TASK_RECONCILIATION_FORMAT, getApiBase(host));

        LOG.info("Fetch task reconciliation statistics from {}", uri);

        final long start = System.currentTimeMillis();

        HttpResponse response = executeRequest(
                uri,
                Method.GET,
                Optional.empty(),
                Collections.emptyMap()
        );

        if (response.getStatusCode() == 404) {
            return Optional.empty();
        }

        checkResponse("task reconciliation statistics", response);

        LOG.info(
                "Got task reconciliation statistics in {}ms",
                System.currentTimeMillis() - start
        );

        return Optional.of(response.getAs(SingularityTaskReconciliationStatistics.class));
    }

    public Optional<SingularityClusterUtilization> getClusterUtilization() {
        final Function<String, String> uri = host ->
                String.format(CLUSTER_UTILIZATION_FORMAT, getApiBase(host));

        return getSingle(uri, "clusterUtilization", "", SingularityClusterUtilization.class);
    }

    //
    // ACTIONS ON A SINGLE SINGULARITY REQUEST
    //

    public Optional<SingularityRequestParent> getSingularityRequest(String requestId) {
        final Function<String, String> singularityApiRequestUri = host ->
                String.format(REQUEST_GET_FORMAT, getApiBase(host), requestId);

        return getSingle(
                singularityApiRequestUri,
                "request",
                requestId,
                SingularityRequestParent.class
        );
    }

    // Fetch only the request + state, no additional deploy/task data
    public Optional<SingularityRequestWithState> getSingularityRequestSimple(
            String requestId
    ) {
        final Function<String, String> singularityApiRequestUri = host ->
                String.format(REQUEST_GET_SIMPLE_FORMAT, getApiBase(host), requestId);

        return getSingle(
                singularityApiRequestUri,
                "request-simple",
                requestId,
                SingularityRequestWithState.class
        );
    }

    public Optional<SingularityTaskId> getTaskByRunIdForRequest(
            String requestId,
            String runId
    ) {
        final Function<String, String> singularityApiRequestUri = host ->
                String.format(REQUEST_BY_RUN_ID_FORMAT, getApiBase(host), requestId, runId);

        return getSingle(
                singularityApiRequestUri,
                "requestByRunId",
                runId,
                SingularityTaskId.class
        );
    }

    public void createOrUpdateSingularityRequest(SingularityRequest request) {
        checkNotNull(request.getId(), "A posted Singularity Request must have an id");

        final Function<String, String> requestUri = host ->
                String.format(REQUEST_CREATE_OR_UPDATE_FORMAT, getApiBase(host));

        post(requestUri, String.format("request %s", request.getId()), Optional.of(request));
    }

    /**
     * Delete a singularity request.
     * If the deletion is successful the singularity request is moved to a DELETING state and is returned.
     * If the request to be deleted is not found {code Optional.empty()} is returned
     * If an error occurs during deletion an exception is returned
     *
     * @param requestId the id of the singularity request to delete
     * @return the singularity request that has been moved to deleting
     */
    public Optional<SingularityRequest> deleteSingularityRequest(
            String requestId,
            Optional<SingularityDeleteRequestRequest> deleteRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_DELETE_ACTIVE_FORMAT, getApiBase(host), requestId);

        return delete(
                requestUri,
                "active request",
                requestId,
                deleteRequest,
                Optional.of(SingularityRequest.class)
        );
    }

    public void pauseSingularityRequest(
            String requestId,
            Optional<SingularityPauseRequest> pauseRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_PAUSE_FORMAT, getApiBase(host), requestId);

        post(requestUri, String.format("pause of request %s", requestId), pauseRequest);
    }

    public void unpauseSingularityRequest(
            String requestId,
            Optional<SingularityUnpauseRequest> unpauseRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_UNPAUSE_FORMAT, getApiBase(host), requestId);

        post(requestUri, String.format("unpause of request %s", requestId), unpauseRequest);
    }

    public void scaleSingularityRequest(
            String requestId,
            SingularityScaleRequest scaleRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_SCALE_FORMAT, getApiBase(host), requestId);
        put(
                requestUri,
                String.format("Scale of Request %s", requestId),
                Optional.of(scaleRequest)
        );
    }

    public SingularityPendingRequestParent runSingularityRequest(
            String requestId,
            Optional<SingularityRunNowRequest> runNowRequest
    ) {
        return runSingularityRequest(requestId, runNowRequest, false);
    }

    /**
     * @param requestId
     * @param runNowRequest
     * @param minimalReturn - if `true` will return a SingularityPendingRequestParent that is _not_ hydrated with extra task + deploy information
     * @return
     */
    public SingularityPendingRequestParent runSingularityRequest(
            String requestId,
            Optional<SingularityRunNowRequest> runNowRequest,
            boolean minimalReturn
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_RUN_FORMAT, getApiBase(host), requestId);

        final HttpResponse response = post(
                requestUri,
                String.format("run of request %s", requestId),
                runNowRequest,
                ImmutableMap.of("minimal", String.valueOf(minimalReturn))
        );

        return response.getAs(SingularityPendingRequestParent.class);
    }

    public void bounceSingularityRequest(
            String requestId,
            Optional<SingularityBounceRequest> bounceOptions
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_BOUNCE_FORMAT, getApiBase(host), requestId);

        post(requestUri, String.format("bounce of request %s", requestId), bounceOptions);
    }

    public void exitCooldown(
            String requestId,
            Optional<SingularityExitCooldownRequest> exitCooldownRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_EXIT_COOLDOWN_FORMAT, getApiBase(host), requestId);

        post(
                requestUri,
                String.format("exit cooldown of request %s", requestId),
                exitCooldownRequest
        );
    }

    public SingularityPendingRequestParent updateAuthorizedGroups(
            String requestId,
            SingularityUpdateGroupsRequest updateGroupsRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_GROUPS_UPDATE_FORMAT, getApiBase(host), requestId);

        final HttpResponse response = post(
                requestUri,
                String.format("update authorized groups of request %s", requestId),
                Optional.of(updateGroupsRequest)
        );

        return response.getAs(SingularityPendingRequestParent.class);
    }

    public boolean checkAuthForRequestGroupsUpdate(
            String requestId,
            SingularityUpdateGroupsRequest updateGroupsRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_GROUPS_UPDATE_AUTH_CHECK_FORMAT, getApiBase(host), requestId);

        final HttpResponse response = post(
                requestUri,
                String.format("check auth for update authorized groups of request %s", requestId),
                Optional.of(updateGroupsRequest)
        );

        return response.isSuccess();
    }

    //
    // ACTIONS ON A DEPLOY FOR A SINGULARITY REQUEST
    //

    public SingularityRequestParent createDeployForSingularityRequest(
            String requestId,
            SingularityDeploy pendingDeploy,
            Optional<Boolean> deployUnpause,
            Optional<String> message
    ) {
        return createDeployForSingularityRequest(
                requestId,
                pendingDeploy,
                deployUnpause,
                message,
                Optional.empty()
        );
    }

    public SingularityRequestParent createDeployForSingularityRequest(
            String requestId,
            SingularityDeploy pendingDeploy,
            Optional<Boolean> deployUnpause,
            Optional<String> message,
            Optional<SingularityRequest> updatedRequest
    ) {
        final Function<String, String> requestUri = (String host) ->
                String.format(DEPLOYS_FORMAT, getApiBase(host));

        HttpResponse response = post(
                requestUri,
                String.format(
                        "new deploy %s",
                        new SingularityDeployKey(requestId, pendingDeploy.getId())
                ),
                Optional.of(
                        new SingularityDeployRequest(
                                pendingDeploy,
                                deployUnpause,
                                message,
                                updatedRequest
                        )
                )
        );

        return getAndLogRequestAndDeployStatus(
                response.getAs(SingularityRequestParent.class)
        );
    }

    private SingularityRequestParent getAndLogRequestAndDeployStatus(
            SingularityRequestParent singularityRequestParent
    ) {
        String activeDeployId = singularityRequestParent.getActiveDeploy().isPresent()
                ? singularityRequestParent.getActiveDeploy().get().getId()
                : "No Active Deploy";
        String pendingDeployId = singularityRequestParent.getPendingDeploy().isPresent()
                ? singularityRequestParent.getPendingDeploy().get().getId()
                : "No Pending deploy";
        LOG.info(
                "Deploy status: Singularity request {} -> pending deploy: '{}', active deploy: '{}'",
                singularityRequestParent.getRequest().getId(),
                pendingDeployId,
                activeDeployId
        );

        return singularityRequestParent;
    }

    public SingularityRequestParent cancelPendingDeployForSingularityRequest(
            String requestId,
            String deployId
    ) {
        final Function<String, String> requestUri = host ->
                String.format(DELETE_DEPLOY_FORMAT, getApiBase(host), deployId, requestId);

        SingularityRequestParent singularityRequestParent = delete(
                requestUri,
                "pending deploy",
                new SingularityDeployKey(requestId, deployId).getId(),
                Optional.empty(),
                Optional.of(SingularityRequestParent.class)
        )
                .get();

        return getAndLogRequestAndDeployStatus(singularityRequestParent);
    }

    public SingularityRequestParent updateIncrementalDeployInstanceCount(
            SingularityUpdatePendingDeployRequest updateRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(UPDATE_DEPLOY_FORMAT, getApiBase(host));

        HttpResponse response = post(
                requestUri,
                String.format(
                        "update deploy %s",
                        new SingularityDeployKey(
                                updateRequest.getRequestId(),
                                updateRequest.getDeployId()
                        )
                ),
                Optional.of(updateRequest)
        );

        return getAndLogRequestAndDeployStatus(
                response.getAs(SingularityRequestParent.class)
        );
    }

    //
    // REQUESTS
    //

    public Collection<SingularityRequestParent> getSingularityRequests() {
        return getSingularityRequests(false);
    }

    public Collection<SingularityRequestParent> getSingularityRequests(
            boolean includeFullRequestData
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUESTS_FORMAT, getApiBase(host));

        return getCollectionWithParams(
                requestUri,
                "[ACTIVE, PAUSED, COOLDOWN] requests",
                Optional.of(ImmutableMap.of("includeFullRequestData", includeFullRequestData)),
                REQUESTS_COLLECTION
        );
    }

    /**
     * Get a specific batch of requests
     *
     * @return A SingularityRequestBatch containing the found request data and not found request ids
     */
    public SingularityRequestBatch getRequestsBatch(Set<String> requestIds) {
        final Function<String, String> requestUri = host ->
                String.format(REQUESTS_GET_BATCH_FORMAT, getApiBase(host));
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("id", requestIds);

        Optional<SingularityRequestBatch> maybeResult = getSingleWithParams(
                requestUri,
                "requests BATCH",
                "requests BATCH",
                Optional.of(queryParams),
                SingularityRequestBatch.class
        );
        if (!maybeResult.isPresent()) {
            throw new SingularityClientException("Singularity url not found", 404);
        } else {
            return maybeResult.get();
        }
    }

    /**
     * Check if a certain request exists
     *
     * @return the hash code of the current SingularityRequest object, empty otherwise
     * returns -1 if the ETag header is missing on the response but the request is present
     */
    public Optional<Integer> headRequest(String requestId) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_GET_FORMAT, getApiBase(host), requestId);
        HttpResponse response = executeRequest(
                requestUri,
                Method.HEAD,
                Optional.empty(),
                Collections.emptyMap()
        );
        if (response.isError()) {
            throw new SingularityClientException(
                    String.format("Could not head request: %s", response.getAsString())
            );
        }
        if (response.getStatusCode() == 404) {
            return Optional.empty();
        }
        return Optional.of(
                response
                        .getHeaders()
                        .get("ETag")
                        .stream()
                        .findFirst()
                        .map(Integer::parseInt)
                        .orElse(-1)
        );
    }

    /**
     * Get all requests that their state is ACTIVE
     *
     * @return All ACTIVE {@link SingularityRequestParent} instances
     */
    public Collection<SingularityRequestParent> getActiveSingularityRequests() {
        final Function<String, String> requestUri = host ->
                String.format(REQUESTS_GET_ACTIVE_FORMAT, getApiBase(host));

        return getCollection(requestUri, "ACTIVE requests", REQUESTS_COLLECTION);
    }

    /**
     * Get all requests that their state is PAUSED
     * ACTIVE requests are paused by users, which is equivalent to stop their tasks from running without undeploying them
     *
     * @return All PAUSED {@link SingularityRequestParent} instances
     */
    public Collection<SingularityRequestParent> getPausedSingularityRequests() {
        final Function<String, String> requestUri = host ->
                String.format(REQUESTS_GET_PAUSED_FORMAT, getApiBase(host));

        return getCollection(requestUri, "PAUSED requests", REQUESTS_COLLECTION);
    }

    /**
     * Get all requests that has been set to a COOLDOWN state by singularity
     *
     * @return All {@link SingularityRequestParent} instances that their state is COOLDOWN
     */
    public Collection<SingularityRequestParent> getCoolDownSingularityRequests() {
        final Function<String, String> requestUri = host ->
                String.format(REQUESTS_GET_COOLDOWN_FORMAT, getApiBase(host));

        return getCollection(requestUri, "COOLDOWN requests", REQUESTS_COLLECTION);
    }

    /**
     * Get all requests that are pending to become ACTIVE
     *
     * @return A collection of {@link SingularityPendingRequest} instances that hold information about the singularity requests that are pending to become ACTIVE
     */
    public Collection<SingularityPendingRequest> getPendingSingularityRequests() {
        final Function<String, String> requestUri = host ->
                String.format(REQUESTS_GET_PENDING_FORMAT, getApiBase(host));

        return getCollection(requestUri, "pending requests", PENDING_REQUESTS_COLLECTION);
    }

    /**
     * Get all requests that are cleaning up
     * Requests that are cleaning up are those that have been marked for removal and their tasks are being stopped/removed
     * before they are being removed. So after their have been cleaned up, these request cease to exist in Singularity.
     *
     * @return A collection of {@link SingularityRequestCleanup} instances that hold information about all singularity requests
     * that are marked for deletion and are currently cleaning up.
     */
    public Collection<SingularityRequestCleanup> getCleanupSingularityRequests() {
        final Function<String, String> requestUri = host ->
                String.format(REQUESTS_GET_CLEANUP_FORMAT, getApiBase(host));

        return getCollection(requestUri, "cleaning requests", CLEANUP_REQUESTS_COLLECTION);
    }

    public Map<String, List<CrashLoopInfo>> getAllCrashLoops() {
        final Function<String, String> requestUri = host ->
                String.format(CRASH_LOOPS, getApiBase(host));
        return getAs(
                requestUri,
                "crashloops",
                "crashloops",
                Optional.empty(),
                CRASH_LOOP_MAP
        );
    }

    public Collection<CrashLoopInfo> getCrashLoopsForRequest(String requestId) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_CRASH_LOOPS, getApiBase(host), requestId);
        return getCollection(requestUri, "crashloops", CRASH_LOOP_INFO_COLLECTION);
    }

    //
    // SINGULARITY TASK COLLECTIONS
    //

    //
    // ACTIVE TASKS
    //

    public Collection<SingularityTask> getActiveTasks() {
        final Function<String, String> requestUri = host ->
                String.format(TASKS_GET_ACTIVE_FORMAT, getApiBase(host));

        return getCollection(requestUri, "active tasks", TASKS_COLLECTION);
    }

    public Collection<SingularityTaskId> getActiveTasksIds() {
        final Function<String, String> requestUri = host ->
                String.format(TASKS_GET_ACTIVE_IDS_FORMAT, getApiBase(host));

        return getCollection(requestUri, "active tasks ids", TASK_IDS_COLLECTION);
    }

    public Map<SingularityTaskId, List<SingularityTaskHistoryUpdate>> getActiveTaskStates() {
        final Function<String, String> requestUri = host ->
                String.format(TASKS_GET_ACTIVE_STATES_FORMAT, getApiBase(host));

        return getSingleWithParams(
                requestUri,
                "active tasks ids",
                "all",
                Optional.empty(),
                new TypeReference<Map<SingularityTaskId, List<SingularityTaskHistoryUpdate>>>() {
                }
        )
                .orElse(Collections.emptyMap());
    }

    @Deprecated
    public Collection<SingularityTask> getActiveTasksOnSlave(final String agentId) {
        return getActiveTasksOnAgent(agentId);
    }

    public Collection<SingularityTask> getActiveTasksOnAgent(final String agentId) {
        final Function<String, String> requestUri = host ->
                String.format(TASKS_GET_ACTIVE_ON_AGENT_FORMAT, getApiBase(host), agentId);

        return getCollection(
                requestUri,
                String.format("active tasks on agent %s", agentId),
                TASKS_COLLECTION
        );
    }

    @Deprecated
    public Collection<SingularityTaskId> getActiveTaskIdsOnSlave(final String agentId) {
        return getActiveTaskIdsOnAgent(agentId);
    }

    public Collection<SingularityTaskId> getActiveTaskIdsOnAgent(final String agentId) {
        final Function<String, String> requestUri = host ->
                String.format(TASKS_GET_ACTIVE_IDS_ON_SLAVE_FORMAT, getApiBase(host), agentId);

        return getCollection(
                requestUri,
                String.format("active tasks on agent %s", agentId),
                TASK_IDS_COLLECTION
        );
    }

    public Optional<SingularityTaskCleanup> killTask(
            String taskId,
            Optional<SingularityKillTaskRequest> killTaskRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(TASKS_KILL_TASK_FORMAT, getApiBase(host), taskId);

        return delete(
                requestUri,
                "task",
                taskId,
                killTaskRequest,
                Optional.of(SingularityTaskCleanup.class)
        );
    }

    //
    // SCHEDULED TASKS
    //

    public Collection<SingularityTaskRequest> getScheduledTasks() {
        final Function<String, String> requestUri = host ->
                String.format(TASKS_GET_SCHEDULED_FORMAT, getApiBase(host));

        return getCollection(requestUri, "scheduled tasks", TASKS_REQUEST_COLLECTION);
    }

    public Collection<SingularityPendingTaskId> getScheduledTaskIds() {
        final Function<String, String> requestUri = host ->
                String.format(TASKS_GET_SCHEDULED_IDS_FORMAT, getApiBase(host));

        return getCollection(requestUri, "scheduled task ids", PENDING_TASK_ID_COLLECTION);
    }

    public Optional<SingularityTaskIdsByStatus> getTaskIdsByStatusForRequest(
            String requestId
    ) {
        final Function<String, String> requestUri = host ->
                String.format(TASKS_BY_STATE_FORMAT, getApiBase(host), requestId);

        return getSingle(
                requestUri,
                "task ids by state",
                requestId,
                SingularityTaskIdsByStatus.class
        );
    }

    public Optional<SingularityTaskCounts> getTaskCountsByStatusForRequest(
            String requestId
    ) {
        final Function<String, String> requestUri = host ->
                String.format(TASK_COUNTS_BY_STATE_FORMAT, getApiBase(host), requestId);

        return getSingle(
                requestUri,
                "task counts by state",
                requestId,
                SingularityTaskCounts.class
        );
    }

    public SingularityTaskShellCommandRequest startShellCommand(
            String taskId,
            SingularityShellCommand shellCommand
    ) {
        final Function<String, String> requestUri = host ->
                String.format(SHELL_COMMAND_FORMAT, getApiBase(host), taskId);

        return post(
                requestUri,
                "start shell command",
                Optional.of(shellCommand),
                Optional.of(SingularityTaskShellCommandRequest.class)
        )
                .orElse(null);
    }

    public Collection<SingularityTaskShellCommandHistory> getShellCommandHistory(
            String taskId
    ) {
        final Function<String, String> requestUri = host ->
                String.format(SHELL_COMMAND_FORMAT, getApiBase(host), taskId);

        return getCollection(requestUri, "get shell command history", SHELL_COMMAND_HISTORY);
    }

    public Collection<SingularityTaskShellCommandUpdate> getShellCommandUpdates(
            SingularityTaskShellCommandRequest shellCommandRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(
                        SHELL_COMMAND_UPDATES_FORMAT,
                        getApiBase(host),
                        shellCommandRequest.getTaskId(),
                        shellCommandRequest.getShellCommand().getName(),
                        shellCommandRequest.getTimestamp()
                );

        return getCollection(
                requestUri,
                "get shell command update history",
                SHELL_COMMAND_UPDATES
        );
    }

    //
    // RACKS
    //
    private Collection<SingularityRack> getRacks(Optional<MachineState> rackState) {
        final Function<String, String> requestUri = host ->
                String.format(RACKS_FORMAT, getApiBase(host));
        Optional<Map<String, Object>> maybeQueryParams = Optional.empty();

        String type = "racks";

        if (rackState.isPresent()) {
            maybeQueryParams =
                    Optional.of(ImmutableMap.of("state", rackState.get().toString()));

            type = String.format("%s racks", rackState.get().toString());
        }

        return getCollectionWithParams(requestUri, type, maybeQueryParams, RACKS_COLLECTION);
    }

    @Deprecated
    public void decomissionRack(String rackId) {
        decommissionRack(rackId, Optional.empty());
    }

    public void decommissionRack(
            String rackId,
            Optional<SingularityMachineChangeRequest> machineChangeRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(RACKS_DECOMISSION_FORMAT, getApiBase(host), rackId);

        post(
                requestUri,
                String.format("decommission rack %s", rackId),
                Optional.of(machineChangeRequest.orElse(SingularityMachineChangeRequest.empty()))
        );
    }

    public void freezeRack(
            String rackId,
            Optional<SingularityMachineChangeRequest> machineChangeRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(RACKS_FREEZE_FORMAT, getApiBase(host), rackId);

        post(
                requestUri,
                String.format("freeze rack %s", rackId),
                Optional.of(machineChangeRequest.orElse(SingularityMachineChangeRequest.empty()))
        );
    }

    public void activateRack(
            String rackId,
            Optional<SingularityMachineChangeRequest> machineChangeRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(RACKS_ACTIVATE_FORMAT, getApiBase(host), rackId);

        post(
                requestUri,
                String.format("activate rack %s", rackId),
                Optional.of(machineChangeRequest.orElse(SingularityMachineChangeRequest.empty()))
        );
    }

    public void deleteRack(String rackId) {
        final Function<String, String> requestUri = host ->
                String.format(RACKS_DELETE_FORMAT, getApiBase(host), rackId);

        delete(requestUri, "dead rack", rackId);
    }

    //
    // SLAVES
    //

    @Deprecated
    public Collection<SingularitySlave> getSlaves(Optional<MachineState> agentState) {
        return getAgents(agentState)
                .stream()
                .map(SingularitySlave::new)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve the list of all known agents, optionally filtering by a particular agent state
     *
     * @param agentState Optionally specify a particular state to filter slaves by
     * @return A collection of {@link SingularityAgent}
     */
    public Collection<SingularityAgent> getAgents(Optional<MachineState> agentState) {
        final Function<String, String> requestUri = host ->
                String.format(AGENTS_FORMAT, getApiBase(host));

        Optional<Map<String, Object>> maybeQueryParams = Optional.empty();

        String type = "agents";

        if (agentState.isPresent()) {
            maybeQueryParams =
                    Optional.of(ImmutableMap.of("state", agentState.get().toString()));

            type = String.format("%s agents", agentState.get().toString());
        }

        return getCollectionWithParams(requestUri, type, maybeQueryParams, AGENTS_COLLECTION);
    }

    /**
     * Retrieve a single agent by ID
     *
     * @param agentId The agent ID to search for
     * @return A {@link SingularityAgent}
     */
    public Optional<SingularityAgent> getAgent(String agentId) {
        final Function<String, String> requestUri = host ->
                String.format(AGENT_DETAIL_FORMAT, getApiBase(host), agentId);

        return getSingle(requestUri, "agent", agentId, SingularityAgent.class);
    }

    @Deprecated
    public Optional<SingularitySlave> getSlave(String agentId) {
        return getAgent(agentId).map(SingularitySlave::new);
    }

    @Deprecated
    public void decomissionSlave(String agentId) {
        decommissionAgent(agentId, Optional.empty());
    }

    @Deprecated
    public void decommissionSlave(
            String agentId,
            Optional<SingularityMachineChangeRequest> machineChangeRequest
    ) {
        decommissionAgent(agentId, machineChangeRequest);
    }

    public void decommissionAgent(
            String agentId,
            Optional<SingularityMachineChangeRequest> machineChangeRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(AGENTS_DECOMISSION_FORMAT, getApiBase(host), agentId);

        post(
                requestUri,
                String.format("decommission agent %s", agentId),
                Optional.of(machineChangeRequest.orElse(SingularityMachineChangeRequest.empty()))
        );
    }

    @Deprecated
    public void freezeSlave(
            String agentId,
            Optional<SingularityMachineChangeRequest> machineChangeRequest
    ) {
        freezeAgent(agentId, machineChangeRequest);
    }

    public void freezeAgent(
            String agentId,
            Optional<SingularityMachineChangeRequest> machineChangeRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(AGENTS_FREEZE_FORMAT, getApiBase(host), agentId);

        post(
                requestUri,
                String.format("freeze agent %s", agentId),
                Optional.of(machineChangeRequest.orElse(SingularityMachineChangeRequest.empty()))
        );
    }

    @Deprecated
    public void activateSlave(
            String agentId,
            Optional<SingularityMachineChangeRequest> machineChangeRequest
    ) {
        activateAgent(agentId, machineChangeRequest);
    }

    public void activateAgent(
            String agentId,
            Optional<SingularityMachineChangeRequest> machineChangeRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(AGENTS_ACTIVATE_FORMAT, getApiBase(host), agentId);

        post(
                requestUri,
                String.format("activate agent %s", agentId),
                Optional.of(machineChangeRequest.orElse(SingularityMachineChangeRequest.empty()))
        );
    }

    @Deprecated
    public void deleteSlave(String agentId) {
        deleteAgent(agentId);
    }

    public void deleteAgent(String agentId) {
        final Function<String, String> requestUri = host ->
                String.format(AGENTS_DELETE_FORMAT, getApiBase(host), agentId);

        delete(requestUri, "deleting agent", agentId);
    }

    //
    // REQUEST HISTORY
    //

    /**
     * Retrieve a paged list of updates for a particular {@link SingularityRequest}
     *
     * @param requestId      Request ID to look up
     * @param createdBefore  Long millis to filter request histories created before
     * @param createdAfter   Long millis to filter request histories created after
     * @param orderDirection Sort order by createdAt
     * @param count          Number of items to return per page
     * @param page           Which page of items to return
     * @return A list of {@link SingularityRequestHistory}
     */
    public Collection<SingularityRequestHistory> getHistoryForRequest(
            String requestId,
            Optional<Long> createdBefore,
            Optional<Long> createdAfter,
            Optional<OrderDirection> orderDirection,
            Optional<Integer> count,
            Optional<Integer> page
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_HISTORY_FORMAT, getApiBase(host), requestId);

        Optional<Map<String, Object>> maybeQueryParams = Optional.empty();

        ImmutableMap.Builder<String, Object> queryParamsBuilder = ImmutableMap.builder();

        if (createdBefore.isPresent()) {
            queryParamsBuilder.put("createdBefore", createdBefore.get());
        }

        if (createdAfter.isPresent()) {
            queryParamsBuilder.put("createdAfter", createdAfter.get());
        }

        if (orderDirection.isPresent()) {
            queryParamsBuilder.put("orderDirection", orderDirection.get().toString());
        }

        if (count.isPresent()) {
            queryParamsBuilder.put("count", count.get());
        }

        if (page.isPresent()) {
            queryParamsBuilder.put("page", page.get());
        }

        Map<String, Object> queryParams = queryParamsBuilder.build();
        if (!queryParams.isEmpty()) {
            maybeQueryParams = Optional.of(queryParams);
        }

        return getCollectionWithParams(
                requestUri,
                "request history",
                maybeQueryParams,
                REQUEST_HISTORY_COLLECTION
        );
    }

    public Collection<SingularityRequestHistory> getHistoryForRequest(
            String requestId,
            Optional<Integer> count,
            Optional<Integer> page
    ) {
        return getHistoryForRequest(
                requestId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                count,
                page
        );
    }

    //
    // Inactive/Bad Agents
    //

    @Deprecated
    public Collection<String> getInactiveSlaves() {
        return getInactiveAgents();
    }

    public Collection<String> getInactiveAgents() {
        final Function<String, String> requestUri = host ->
                String.format(INACTIVE_AGENTS_FORMAT, getApiBase(host));
        return getCollection(requestUri, "inactiveAgents", STRING_COLLECTION);
    }

    @Deprecated
    public void markSlaveAsInactive(String host) {
        markAgentAsInactive(host);
    }

    public void markAgentAsInactive(String host) {
        final Function<String, String> requestUri = singularityHost ->
                String.format(INACTIVE_AGENTS_FORMAT, getApiBase(singularityHost));
        Map<String, Object> params = Collections.singletonMap("host", host);
        postWithParams(requestUri, "deactivateAgent", Optional.empty(), Optional.of(params));
    }

    @Deprecated
    public void clearInactiveSlave(String host) {
        clearInactiveAgent(host);
    }

    public void clearInactiveAgent(String host) {
        final Function<String, String> requestUri = singularityHost ->
                String.format(INACTIVE_AGENTS_FORMAT, getApiBase(host));
        Map<String, Object> params = Collections.singletonMap("host", host);
        deleteWithParams(
                requestUri,
                "clearInactiveAgent",
                host,
                Optional.empty(),
                Optional.of(params),
                Optional.empty()
        );
    }

    //
    // TASK HISTORY
    //

    /**
     * Retrieve information about an inactive task by its id
     *
     * @param taskId The task ID to search for
     * @return A {@link SingularityTaskIdHistory} object if the task exists
     */
    public Optional<SingularityTaskHistory> getHistoryForTask(String taskId) {
        final Function<String, String> requestUri = host ->
                String.format(TASK_HISTORY_FORMAT, getApiBase(host), taskId);

        return getSingle(requestUri, "task history", taskId, SingularityTaskHistory.class);
    }

    public Collection<SingularityTaskIdHistory> getActiveTaskHistoryForRequest(
            String requestId
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_ACTIVE_TASKS_HISTORY_FORMAT, getApiBase(host), requestId);

        final String type = String.format("active task history for %s", requestId);

        return getCollection(requestUri, type, TASKID_HISTORY_COLLECTION);
    }

    public Collection<SingularityTaskIdHistory> getInactiveTaskHistoryForRequest(
            String requestId
    ) {
        return getInactiveTaskHistoryForRequest(requestId, 100, 1);
    }

    public Collection<SingularityTaskIdHistory> getInactiveTaskHistoryForRequest(
            String requestId,
            String deployId
    ) {
        return getInactiveTaskHistoryForRequest(
                requestId,
                100,
                1,
                Optional.empty(),
                Optional.of(deployId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public Collection<SingularityTaskIdHistory> getInactiveTaskHistoryForRequest(
            String requestId,
            int count,
            int page
    ) {
        return getInactiveTaskHistoryForRequest(
                requestId,
                count,
                page,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public Collection<SingularityTaskIdHistory> getInactiveTaskHistoryForRequest(
            String requestId,
            int count,
            int page,
            Optional<String> host,
            Optional<String> runId,
            Optional<ExtendedTaskState> lastTaskStatus,
            Optional<Long> startedBefore,
            Optional<Long> startedAfter,
            Optional<Long> updatedBefore,
            Optional<Long> updatedAfter,
            Optional<OrderDirection> orderDirection
    ) {
        final Function<String, String> requestUri = singularityHost ->
                String.format(
                        REQUEST_INACTIVE_TASKS_HISTORY_FORMAT,
                        getApiBase(singularityHost),
                        requestId
                );

        final String type = String.format(
                "inactive (failed, killed, lost) task history for request %s",
                requestId
        );

        Map<String, Object> params = taskSearchParams(
                Optional.of(requestId),
                Optional.empty(),
                runId,
                host,
                lastTaskStatus,
                startedBefore,
                startedAfter,
                updatedBefore,
                updatedAfter,
                orderDirection,
                count,
                page
        );

        return getCollectionWithParams(
                requestUri,
                type,
                Optional.of(params),
                TASKID_HISTORY_COLLECTION
        );
    }

    public Collection<SingularityTaskIdHistory> getInactiveTaskHistoryForRequest(
            String requestId,
            int count,
            int page,
            Optional<String> host,
            Optional<String> deployId,
            Optional<String> runId,
            Optional<ExtendedTaskState> lastTaskStatus,
            Optional<Long> startedBefore,
            Optional<Long> startedAfter,
            Optional<Long> updatedBefore,
            Optional<Long> updatedAfter,
            Optional<OrderDirection> orderDirection
    ) {
        final Function<String, String> requestUri = singularityHost ->
                String.format(
                        REQUEST_INACTIVE_TASKS_HISTORY_FORMAT,
                        getApiBase(singularityHost),
                        requestId
                );

        final String type = String.format(
                "inactive (failed, killed, lost) task history for request %s",
                requestId
        );

        Map<String, Object> params = taskSearchParams(
                Optional.of(requestId),
                deployId,
                runId,
                host,
                lastTaskStatus,
                startedBefore,
                startedAfter,
                updatedBefore,
                updatedAfter,
                orderDirection,
                count,
                page
        );

        return getCollectionWithParams(
                requestUri,
                type,
                Optional.of(params),
                TASKID_HISTORY_COLLECTION
        );
    }

    public Optional<SingularityDeployHistory> getHistoryForRequestDeploy(
            String requestId,
            String deployId
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_DEPLOY_HISTORY_FORMAT, getApiBase(host), requestId, deployId);

        return getSingle(
                requestUri,
                "deploy history",
                new SingularityDeployKey(requestId, deployId).getId(),
                SingularityDeployHistory.class
        );
    }

    /**
     * Retrieve the task id and state for an inactive task by its runId
     *
     * @param requestId The request ID to search for
     * @param runId     The run ID to search for
     * @return A {@link SingularityTaskIdHistory} object if the task exists
     * @deprecated use {@link #getTaskIdHistoryByRunId}
     */
    @Deprecated
    public Optional<SingularityTaskIdHistory> getHistoryForTask(
            String requestId,
            String runId
    ) {
        return getTaskIdHistoryByRunId(requestId, runId);
    }

    public Optional<SingularityTaskIdHistory> getTaskIdHistoryByRunId(
            String requestId,
            String runId
    ) {
        final Function<String, String> requestUri = host ->
                String.format(TASK_HISTORY_BY_RUN_ID_FORMAT, getApiBase(host), requestId, runId);

        return getSingle(
                requestUri,
                "task history",
                requestId,
                SingularityTaskIdHistory.class
        );
    }

    public Collection<SingularityTaskIdHistory> getTaskHistory(
            Optional<String> requestId,
            Optional<String> deployId,
            Optional<String> runId,
            Optional<String> host,
            Optional<ExtendedTaskState> lastTaskStatus,
            Optional<Long> startedBefore,
            Optional<Long> startedAfter,
            Optional<Long> updatedBefore,
            Optional<Long> updatedAfter,
            Optional<OrderDirection> orderDirection,
            Integer count,
            Integer page
    ) {
        final Function<String, String> requestUri = singularityHost ->
                String.format(TASKS_HISTORY_FORMAT, getApiBase(singularityHost));

        Map<String, Object> params = taskSearchParams(
                requestId,
                deployId,
                runId,
                host,
                lastTaskStatus,
                startedBefore,
                startedAfter,
                updatedBefore,
                updatedAfter,
                orderDirection,
                count,
                page
        );

        return getCollectionWithParams(
                requestUri,
                "task id history",
                Optional.of(params),
                TASKID_HISTORY_COLLECTION
        );
    }

    public Optional<SingularityPaginatedResponse<SingularityTaskIdHistory>> getTaskHistoryWithMetadata(
            Optional<String> requestId,
            Optional<String> deployId,
            Optional<String> runId,
            Optional<String> host,
            Optional<ExtendedTaskState> lastTaskStatus,
            Optional<Long> startedBefore,
            Optional<Long> startedAfter,
            Optional<Long> updatedBefore,
            Optional<Long> updatedAfter,
            Optional<OrderDirection> orderDirection,
            Integer count,
            Integer page
    ) {
        final Function<String, String> requestUri = singularityHost ->
                String.format(TASKS_HISTORY_WITHMETADATA_FORMAT, getApiBase(singularityHost));

        Map<String, Object> params = taskSearchParams(
                requestId,
                deployId,
                runId,
                host,
                lastTaskStatus,
                startedBefore,
                startedAfter,
                updatedBefore,
                updatedAfter,
                orderDirection,
                count,
                page
        );

        return getSingleWithParams(
                requestUri,
                "task id history with metadata",
                "",
                Optional.of(params),
                PAGINATED_HISTORY
        );
    }

    private Map<String, Object> taskSearchParams(
            Optional<String> requestId,
            Optional<String> deployId,
            Optional<String> runId,
            Optional<String> host,
            Optional<ExtendedTaskState> lastTaskStatus,
            Optional<Long> startedBefore,
            Optional<Long> startedAfter,
            Optional<Long> updatedBefore,
            Optional<Long> updatedAfter,
            Optional<OrderDirection> orderDirection,
            Integer count,
            Integer page
    ) {
        Map<String, Object> params = new HashMap<>();
        if (requestId.isPresent()) {
            params.put("requestId", requestId.get());
        }
        if (deployId.isPresent()) {
            params.put("deployId", deployId.get());
        }
        if (runId.isPresent()) {
            params.put("runId", runId.get());
        }
        if (host.isPresent()) {
            params.put("host", host.get());
        }
        if (lastTaskStatus.isPresent()) {
            params.put("lastTaskStatus", lastTaskStatus.get().toString());
        }
        if (startedBefore.isPresent()) {
            params.put("startedBefore", startedBefore.get());
        }
        if (startedAfter.isPresent()) {
            params.put("startedAfter", startedAfter.get());
        }
        if (updatedBefore.isPresent()) {
            params.put("updatedBefore", updatedBefore.get());
        }
        if (updatedAfter.isPresent()) {
            params.put("updatedAfter", updatedAfter.get());
        }
        if (orderDirection.isPresent()) {
            params.put("orderDirection", orderDirection.get().toString());
        }
        params.put("count", count);
        params.put("page", page);
        return params;
    }

    //
    // WEBHOOKS
    //

    public Optional<SingularityCreateResult> addWebhook(SingularityWebhook webhook) {
        final Function<String, String> requestUri = host ->
                String.format(WEBHOOKS_FORMAT, getApiBase(host));

        return post(
                requestUri,
                String.format("webhook %s", webhook.getUri()),
                Optional.of(webhook),
                Optional.of(SingularityCreateResult.class)
        );
    }

    public Optional<SingularityDeleteResult> deleteWebhook(String webhookId) {
        final Function<String, String> requestUri = host ->
                String.format(WEBHOOKS_DELETE_FORMAT, getApiBase(host));

        Builder<String, Object> queryParamBuider = ImmutableMap
                .<String, Object>builder()
                .put("webhookId", webhookId);

        return deleteWithParams(
                requestUri,
                String.format("webhook with id %s", webhookId),
                webhookId,
                Optional.empty(),
                Optional.of(queryParamBuider.build()),
                Optional.of(SingularityDeleteResult.class)
        );
    }

    public Collection<SingularityWebhook> getActiveWebhook() {
        final Function<String, String> requestUri = host ->
                String.format(WEBHOOKS_FORMAT, getApiBase(host));

        return getCollection(requestUri, "active webhooks", WEBHOOKS_COLLECTION);
    }

    public Collection<SingularityDeployUpdate> getQueuedDeployUpdates(String webhookId) {
        final Function<String, String> requestUri = host ->
                String.format(WEBHOOKS_GET_QUEUED_DEPLOY_UPDATES_FORMAT, getApiBase(host));

        Builder<String, Object> queryParamBuider = ImmutableMap
                .<String, Object>builder()
                .put("webhookId", webhookId);

        return getCollectionWithParams(
                requestUri,
                "deploy updates",
                Optional.of(queryParamBuider.build()),
                DEPLOY_UPDATES_COLLECTION
        );
    }

    public Collection<SingularityRequestHistory> getQueuedRequestUpdates(String webhookId) {
        final Function<String, String> requestUri = host ->
                String.format(WEBHOOKS_GET_QUEUED_REQUEST_UPDATES_FORMAT, getApiBase(host));

        Builder<String, Object> queryParamBuider = ImmutableMap
                .<String, Object>builder()
                .put("webhookId", webhookId);

        return getCollectionWithParams(
                requestUri,
                "request updates",
                Optional.of(queryParamBuider.build()),
                REQUEST_UPDATES_COLLECTION
        );
    }

    public Collection<SingularityTaskHistoryUpdate> getQueuedTaskUpdates(String webhookId) {
        final Function<String, String> requestUri = host ->
                String.format(WEBHOOKS_GET_QUEUED_TASK_UPDATES_FORMAT, getApiBase(host));

        Builder<String, Object> queryParamBuider = ImmutableMap
                .<String, Object>builder()
                .put("webhookId", webhookId);

        return getCollectionWithParams(
                requestUri,
                "request updates",
                Optional.of(queryParamBuider.build()),
                TASK_UPDATES_COLLECTION
        );
    }

    //
    // SANDBOX
    //

    /**
     * Retrieve information about a specific task's sandbox
     *
     * @param taskId The task ID to browse
     * @param path   The path to browse from.
     *               if not specified it will browse from the sandbox root.
     * @return A {@link SingularitySandbox} object that captures the information for the path to a specific task's Mesos sandbox
     */
    public Optional<SingularitySandbox> browseTaskSandBox(String taskId, String path) {
        final Function<String, String> requestUrl = host ->
                String.format(SANDBOX_BROWSE_FORMAT, getApiBase(host), taskId);

        return getSingleWithParams(
                requestUrl,
                "browse sandbox for task",
                taskId,
                Optional.of(ImmutableMap.of("path", path)),
                SingularitySandbox.class
        );
    }

    /**
     * Retrieve part of the contents of a file in a specific task's sandbox.
     *
     * @param taskId The task ID of the sandbox to read from
     * @param path   The path to the file to be read. Relative to the sandbox root (without a leading slash)
     * @param grep   Optional string to grep for
     * @param offset Byte offset to start reading from
     * @param length Maximum number of bytes to read
     * @return A {@link MesosFileChunkObject} that contains the requested partial file contents
     */
    public Optional<MesosFileChunkObject> readSandBoxFile(
            String taskId,
            String path,
            Optional<String> grep,
            Optional<Long> offset,
            Optional<Long> length
    ) {
        final Function<String, String> requestUrl = host ->
                String.format(SANDBOX_READ_FILE_FORMAT, getApiBase(host), taskId);

        Builder<String, Object> queryParamBuider = ImmutableMap
                .<String, Object>builder()
                .put("path", path);

        if (grep.isPresent()) {
            queryParamBuider.put("grep", grep.get());
        }
        if (offset.isPresent()) {
            queryParamBuider.put("offset", offset.get());
        }
        if (length.isPresent()) {
            queryParamBuider.put("length", length.get());
        }

        return getSingleWithParams(
                requestUrl,
                "Read sandbox file for task",
                taskId,
                Optional.of(queryParamBuider.build()),
                MesosFileChunkObject.class
        );
    }

    //
    // S3 LOGS
    //

    /**
     * Retrieve the list of logs stored in S3 based on a specified search request
     *
     * @param searchRequest The parameters upon which to base the search
     * @return A result in the form of a {@link SingularityS3SearchResult}
     */
    public SingularityS3SearchResult getLogs(SingularityS3SearchRequest searchRequest) {
        final Function<String, String> requestUri = host ->
                String.format(S3_LOG_SEARCH_LOGS, getApiBase(host));

        final Optional<SingularityS3SearchResult> maybeResult = post(
                requestUri,
                "S3 log search",
                Optional.of(searchRequest),
                Optional.of(SingularityS3SearchResult.class)
        );

        if (!maybeResult.isPresent()) {
            throw new SingularityClientException("Singularity url not found", 404);
        } else {
            return maybeResult.get();
        }
    }

    /**
     * Retrieve the list of logs stored in S3 for a specific task
     *
     * @param taskId The task ID to search for
     * @return A collection of {@link SingularityS3Log}
     */
    public Collection<SingularityS3Log> getTaskLogs(String taskId) {
        final Function<String, String> requestUri = host ->
                String.format(S3_LOG_GET_TASK_LOGS, getApiBase(host), taskId);

        final String type = String.format("S3 logs for task %s", taskId);

        return getCollection(requestUri, type, S3_LOG_COLLECTION);
    }

    /**
     * Retrieve the list of logs stored in S3 for a specific request
     *
     * @param requestId The request ID to search for
     * @return A collection of {@link SingularityS3Log}
     */
    public Collection<SingularityS3Log> getRequestLogs(String requestId) {
        return getRequestLogs(requestId, Optional.empty(), Optional.empty());
    }

    public Collection<SingularityS3Log> getRequestLogs(
            String requestId,
            Optional<Long> start,
            Optional<Long> end
    ) {
        final Function<String, String> requestUri = host ->
                String.format(S3_LOG_GET_REQUEST_LOGS, getApiBase(host), requestId);

        final String type = String.format("S3 logs for request %s", requestId);

        Map<String, Object> dateRange = new HashMap<>();
        if (start.isPresent()) {
            dateRange.put("start", start.get());
        }

        if (end.isPresent()) {
            dateRange.put("end", end.get());
        }

        return getCollectionWithParams(
                requestUri,
                type,
                Optional.of(dateRange),
                S3_LOG_COLLECTION
        );
    }

    /**
     * Retrieve the list of logs stored in S3 for a specific deploy if a singularity request
     *
     * @param requestId The request ID to search for
     * @param deployId  The deploy ID (within the specified request) to search for
     * @return A collection of {@link SingularityS3Log}
     */
    public Collection<SingularityS3Log> getDeployLogs(String requestId, String deployId) {
        final Function<String, String> requestUri = host ->
                String.format(S3_LOG_GET_DEPLOY_LOGS, getApiBase(host), requestId, deployId);

        final String type = String.format(
                "S3 logs for deploy %s of request %s",
                deployId,
                requestId
        );

        return getCollection(requestUri, type, S3_LOG_COLLECTION);
    }

    /**
     * Retrieve the list of request groups
     *
     * @return A collection of {@link SingularityRequestGroup}
     */
    public Collection<SingularityRequestGroup> getRequestGroups() {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_GROUPS_FORMAT, getApiBase(host));

        return getCollection(requestUri, "request groups", REQUEST_GROUP_COLLECTION);
    }

    /**
     * Retrieve a specific request group by id
     *
     * @param requestGroupId The request group ID to search for
     * @return A {@link SingularityRequestGroup}
     */
    public Optional<SingularityRequestGroup> getRequestGroup(String requestGroupId) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_GROUP_FORMAT, getApiBase(host), requestGroupId);

        return getSingle(
                requestUri,
                "request group",
                requestGroupId,
                SingularityRequestGroup.class
        );
    }

    /**
     * Update or add a {@link SingularityRequestGroup}
     *
     * @param requestGroup The request group to update or add
     * @return A {@link SingularityRequestGroup} if the update was successful
     */
    public Optional<SingularityRequestGroup> saveRequestGroup(
            SingularityRequestGroup requestGroup
    ) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_GROUPS_FORMAT, getApiBase(host));

        return post(
                requestUri,
                "request group",
                Optional.of(requestGroup),
                Optional.of(SingularityRequestGroup.class)
        );
    }

    /**
     * Remove a {@link SingularityRequestGroup}
     *
     * @param requestGroupId The request group ID to search for
     */
    public void deleteRequestGroup(String requestGroupId) {
        final Function<String, String> requestUri = host ->
                String.format(REQUEST_GROUP_FORMAT, getApiBase(host), requestGroupId);

        delete(requestUri, "request group", requestGroupId);
    }

    //
    // DISASTERS
    //

    public Optional<SingularityDisastersData> getDisasterStats() {
        final Function<String, String> requestUri = host ->
                String.format(DISASTER_STATS_FORMAT, getApiBase(host));
        return getSingle(requestUri, "disaster stats", "", SingularityDisastersData.class);
    }

    public Collection<SingularityDisasterType> getActiveDisasters() {
        final Function<String, String> requestUri = host ->
                String.format(ACTIVE_DISASTERS_FORMAT, getApiBase(host));
        return getCollection(requestUri, "active disasters", DISASTERS_COLLECTION);
    }

    public void disableAutomatedDisasterCreation() {
        final Function<String, String> requestUri = host ->
                String.format(DISABLE_AUTOMATED_ACTIONS_FORMAT, getApiBase(host));
        post(requestUri, "disable automated disasters", Optional.empty());
    }

    public void enableAutomatedDisasterCreation() {
        final Function<String, String> requestUri = host ->
                String.format(ENABLE_AUTOMATED_ACTIONS_FORMAT, getApiBase(host));
        post(requestUri, "enable automated disasters", Optional.empty());
    }

    public void removeDisaster(SingularityDisasterType disasterType) {
        final Function<String, String> requestUri = host ->
                String.format(DISASTER_FORMAT, getApiBase(host), disasterType);
        delete(requestUri, "remove disaster", disasterType.toString());
    }

    public void activateDisaster(SingularityDisasterType disasterType) {
        final Function<String, String> requestUri = host ->
                String.format(DISASTER_FORMAT, getApiBase(host), disasterType);
        post(requestUri, "activate disaster", Optional.empty());
    }

    public Collection<SingularityDisabledAction> getDisabledActions() {
        final Function<String, String> requestUri = host ->
                String.format(DISABLED_ACTIONS_FORMAT, getApiBase(host));
        return getCollection(requestUri, "disabled actions", DISABLED_ACTIONS_COLLECTION);
    }

    public void disableAction(
            SingularityAction action,
            Optional<SingularityDisabledActionRequest> request
    ) {
        final Function<String, String> requestUri = host ->
                String.format(DISABLED_ACTION_FORMAT, getApiBase(host), action);
        post(requestUri, "disable action", request);
    }

    public void enableAction(SingularityAction action) {
        final Function<String, String> requestUri = host ->
                String.format(DISABLED_ACTION_FORMAT, getApiBase(host), action);
        delete(requestUri, "disable action", action.toString());
    }

    //
    // PRIORITY
    //

    public Optional<SingularityPriorityFreezeParent> getActivePriorityFreeze() {
        final Function<String, String> requestUri = host ->
                String.format(PRIORITY_FREEZE_FORMAT, getApiBase(host));
        return getSingle(
                requestUri,
                "priority freeze",
                "",
                SingularityPriorityFreezeParent.class
        );
    }

    public Optional<SingularityPriorityFreezeParent> createPriorityFreeze(
            SingularityPriorityFreeze priorityFreezeRequest
    ) {
        final Function<String, String> requestUri = host ->
                String.format(PRIORITY_FREEZE_FORMAT, getApiBase(host));
        return post(
                requestUri,
                "priority freeze",
                Optional.of(priorityFreezeRequest),
                Optional.of(SingularityPriorityFreezeParent.class)
        );
    }

    public void deletePriorityFreeze() {
        final Function<String, String> requestUri = host ->
                String.format(PRIORITY_FREEZE_FORMAT, getApiBase(host));
        delete(requestUri, "priority freeze", "");
    }

    //
    // Auth
    //

    /**
     * Check if a user is authorized for the specified scope on the specified request
     *
     * @param requestId The request to check authorization on
     * @param userId    The user whose authorization will be checked
     * @param scope     The scope to check that `user` has
     * @return true if the user is authorized for scope, false otherwise
     */
    public boolean isUserAuthorized(
            String requestId,
            String userId,
            SingularityAuthorizationScope scope
    ) {
        final Function<String, String> requestUri = host ->
                String.format(AUTH_CHECK_USER_FORMAT, getApiBase(host), requestId, userId);
        Map<String, Object> params = Collections.singletonMap("scope", scope.name());
        HttpResponse response = executeGetSingleWithParams(
                requestUri,
                "auth check",
                "",
                Optional.of(params)
        );
        return response.isSuccess();
    }

    /**
     * Check if the current client's user is authorized for the specified scope on the specified request
     *
     * @param requestId The request to check authorization on
     * @param userId    The user whose authorization will be checked
     * @param scope     The scope to check that `user` has
     * @return true if the user is authorized for scope, false otherwise
     */
    public boolean isUserAuthorized(String requestId, SingularityAuthorizationScope scope) {
        final Function<String, String> requestUri = host ->
                String.format(AUTH_CHECK_FORMAT, getApiBase(host), requestId);
        Map<String, Object> params = Collections.singletonMap("scope", scope.name());
        HttpResponse response = executeGetSingleWithParams(
                requestUri,
                "auth check",
                "",
                Optional.of(params)
        );
        return response.isSuccess();
    }

    //
    // TASK STATE
    //

    /**
     * Get the current state of a task by its task ID, will only search active/inactive tasks, not pending
     *
     * @param taskId The task ID to search for
     * @return A {@link SingularityTaskState} if the task was found among active or inactive tasks
     */
    public Optional<SingularityTaskState> getTaskState(String taskId) {
        final Function<String, String> requestUri = host ->
                String.format(TRACK_BY_TASK_ID_FORMAT, getApiBase(host), taskId);
        return getSingle(requestUri, "track by task id", taskId, SingularityTaskState.class);
    }

    /**
     * Get the current state of a task by its run IDg
     *
     * @param requestId The request ID to search for the specified runId
     * @param runId     The run ID to search for
     * @return A {@link SingularityTaskState} if the task was found among pending, active or inactive tasks
     */
    public Optional<SingularityTaskState> getTaskState(String requestId, String runId) {
        final Function<String, String> requestUri = host ->
                String.format(TRACK_BY_RUN_ID_FORMAT, getApiBase(host), requestId, runId);
        return getSingle(
                requestUri,
                "track by task id",
                String.format("%s-%s", requestId, runId),
                SingularityTaskState.class
        );
    }

    public Optional<SingularityTask> getSingularityTask(String taskId) {
        final Function<String, String> requestUri = host ->
                String.format(TASKS_KILL_TASK_FORMAT, getApiBase(host), taskId);

        return getSingle(requestUri, "task info", taskId, SingularityTask.class);
    }

}
