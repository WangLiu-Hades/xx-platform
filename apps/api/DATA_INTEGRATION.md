# DEV2 data API integration

The normal application profile now registers the seven M03 operations and the
PLATE capture/search/map-feature controllers. No profile flag is required.
The existing local fixture mode remains restricted to
`dev,ingestion-mock` with a loopback server address.

`DataIntegrationConfiguration` creates the services and
`DataServiceBindings`. Supply Spring beans implementing the existing ports:

| Port | Provider / responsibility |
| --- | --- |
| `DataServiceBindings.SharedAccess` | DEV1: current session, CSRF/Origin, permission and fresh scopes |
| `IngestionDomainGateway.AtomicSubmission` | Joint bridge review: DEV2 domain planning participates in DEV1 shared batch/task/idempotency/approval transaction |
| `IngestionQueryAdapter.Client` | DEV4 integration: batch/detail/errors/quality invocation |
| `IngestionQueryAdapter.WindowPolicy` | Server-approved quality bounds and query budget |
| `PlateQueryAdapter.Client` | DEV4 integration: scoped query sessions, capture details and map features |
| `PlateQueryAdapter.TargetLookup` | Approved target lookup, without creating identities |
| `CaptureExportSourceAdapter.ContextLookup` | DEV1: authorized complete frozen Context |
| `java.time.Clock` (optional) | UTC system clock is used when absent |

Each call resolves the supplied port, preserves the original exception and
forwards the trusted scope, opaque cursor and query budget unchanged.
An absent port returns 503 SYSTEM_UNAVAILABLE with Retry-After rather than a
successful empty response or a synthetic task receipt. Fixture data is never
used by the normal configuration.

DEV1's shared export orchestration can inject `CaptureExportSourceAdapter`
and call `prepare` to obtain the full frozen query and current authority.
DEV1 still owns task submission, execution, file publication and download;
execution must recheck authorization and Context expiry.
DEV2 does not add a second /exports controller or TaskStore.

These are consumption-side bindings, not a new HTTP/Python protocol.
This checkout contains no runnable DEV4 Worker or DEV1 shared services.
Their concrete port implementations must use the jointly agreed invocation
mechanism; registering these bindings alone does not connect a remote service.

Run the full suite from apps/api with:

```text
mvn -B -Pgenerate-api clean verify
```

The integration tests cover normal-profile HTTP routing, supplied upstream
ports, submission/replay/backfill, query scope/cursor/budget propagation,
export Context consumption, domain failures and absent dependencies.


The ports above are proposed consumption interfaces, not already published
shared-service signatures. Concrete Java bridge ownership, transaction
participation, exception mapping and version inputs require joint review.
DEV2 owns domain planning/validation; DEV1 owns shared authorization, approval,
TaskStore and transaction capabilities; DEV4 owns parameterized data queries.

AtomicSubmission must reauthorize even an idempotent replay, return the original
committed receipt for the same request, and use IDEMPOTENCY_CONFLICT for the same
key with different input. A new action verifies independent approval, proposal
digest, resource/policy versions and active file validity in the transaction
that consumes approval and commits domain rows, shared task and receipt.
A UUID header alone is not authorization. TASK_CONFLICT keeps its task meaning.

Queries must receive trusted typed scope grants per source and preserve snapshot,
current scope version, stable ordering and cursor bindings. Full Context lookup
must not substitute preview targets. Shared TaskStore remains the sole authority
for leases, cancellation, retries and publication; publication/download recheck
current access and reject stale/cancelled attempts.

Outstanding acceptance requires real shared services and declared data sources:
HTTP submission -> authorization/approval/idempotency -> shared task -> Worker ->
transactional publication -> query readback after refresh. Include scope
reduction, cancellation/lost lease and recovery. Current fixture/port tests do
not establish that acceptance.
