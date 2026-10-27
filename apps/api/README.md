# xx-platform HTTP API

Java 17 / Spring Boot 3.5.16 / Maven 3.9+.
The machine contract is [openapi.yaml](../../contracts/api/openapi.yaml).
See [conventions](../../contracts/api/api-conventions.md),
[ownership](../../docs/project-governance/BACKEND_TWO_DEV_SPLIT.md) and
[data integration](DATA_INTEGRATION.md).

## Build and run

Run from apps/api, in one Maven invocation:

    mvn -B -Pgenerate-api clean verify

Equivalent wrapper entry points:

    .\mvnw.cmd -B -Pgenerate-api clean verify
    ./mvnw -B -Pgenerate-api clean verify

This generates interfaces/models, compiles, executes tests and packages.
Use JDK 17 and Maven 3.9+. First resolve dependencies/plugins online.
Offline mode requires the full dependency set to be cached.
Generated files live under target/generated-sources/openapi and are not committed.
Retain -Pgenerate-api in every compile/test/package/run invocation.
Separate generation followed by profile-free clean/test is insufficient.

Start the dev endpoint:

    mvn -B -Pgenerate-api spring-boot:run "-Dspring-boot.run.profiles=dev"

The API base path is /api/v1 on port 8080. GET /api/v1/smoke exists only
in dev. Frontend Vite proxies /api/v1 to this server; preserve the prefix.

From the repository root, the environment/build check is:

    powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/dev-check.ps1

CI runs the same clean verification in .github/workflows/backend.yml.

## Generation and integration

Spring Generator 7.25.0 uses interfaceOnly, useSpringBoot3,
useOneOfInterfaces, useTags, java8 dates and openApiNullable=false.
Keep setTaskId=setOperationId mapping; JSON contract names remain unchanged.
Swagger annotations are retained with swagger-annotations-jakarta as an explicit
compile dependency. Do not edit generated files or mix annotation strategies.

M03 ingestion and PLATE capture/map domain implementations are present.
Real shared authorization, approval, TaskStore and data-query ports still
require provider integration. Missing providers return 503 SYSTEM_UNAVAILABLE;
fixture tests do not prove real-source acceptance.
Local fixtures require dev,ingestion-mock and a loopback server address.

Shared common/infra/config and database migration integration belong to DEV01.
DEV02 consumes these capabilities and owns data-domain orchestration.
DEV04 supplies parameterized queries and Worker execution.
Do not create a second TaskStore or authorization implementation.

真实鉴权、审批、共享任务与数据查询的联通尚待完成；模拟测试通过不代表真实业务链路验收完成。
