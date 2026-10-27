export type paths = {
    "/action-approvals": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询本人申请或待本人复核的审批
         * @description 按PD-2026-10-19.v1暂定开发；只返回申请人/被指定审批人当前有权对象。审批人必须具备对应动作权限和范围，不能自批；当前密码仅即时校验、不存储或回显。真实凭据/密钥未就绪不得伪造通过。
         */
        get: operations["listActionApprovals"];
        put?: never;
        /**
         * 创建冻结的敏感动作审批申请
         * @description 按PD-2026-10-19.v1暂定开发；只返回申请人/被指定审批人当前有权对象。审批人必须具备对应动作权限和范围，不能自批；当前密码仅即时校验、不存储或回显。真实凭据/密钥未就绪不得伪造通过。
         */
        post: operations["createActionApproval"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/action-approvals/{approvalId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询受控审批详情
         * @description 按PD-2026-10-19.v1暂定开发；只返回申请人/被指定审批人当前有权对象。审批人必须具备对应动作权限和范围，不能自批；当前密码仅即时校验、不存储或回显。真实凭据/密钥未就绪不得伪造通过。
         */
        get: operations["getActionApproval"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/action-approvals/{approvalId}/decisions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 提交独立复核决定
         * @description 按PD-2026-10-19.v1暂定开发；只返回申请人/被指定审批人当前有权对象。审批人必须具备对应动作权限和范围，不能自批；当前密码仅即时校验、不存储或回显。真实凭据/密钥未就绪不得伪造通过。
         */
        post: operations["decideActionApproval"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/alerts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 游标检索实时告警
         * @description 游标检索实时告警；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listAlerts"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/alerts/{alertId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看告警、源采集、规则快照与送达
         * @description 查看告警、源采集、规则快照与送达；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getAlert"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analysis-tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 按名称、类型和日期查询分析任务
         * @description 按名称、类型和日期查询分析任务；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listAnalysisTasks"];
        put?: never;
        /**
         * 创建伴随/碰撞/区域统计任务
         * @description 创建伴随/碰撞/区域统计任务；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["createAnalysisTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analysis-tasks/{analysisTaskId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看分析任务与参数快照
         * @description 查看分析任务与参数快照；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getAnalysisTask"];
        put?: never;
        post?: never;
        /**
         * 软删终态分析任务
         * @description 仅终态且无受保护引用时可软删；运行中须先请求取消。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deleteAnalysisTask"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analysis-tasks/{analysisTaskId}/results": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 分页查看分析结果
         * @description 分页查看分析结果；未SUCCESS返回409 TASK_RESULT_NOT_READY，结果已清理返回410 FILE_EXPIRED；成功空结果为items=[]（适用分页响应）。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listAnalysisResults"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analysis-tasks/{analysisTaskId}/results/{resultId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看分析结果及证据引用
         * @description 查看分析结果及证据引用；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getAnalysisResult"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analysis-tasks/{analysisTaskId}/results/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 异步导出分析结果
         * @description 异步导出分析结果；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["exportAnalysisResults"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analysis-tasks/{analysisTaskId}/revisions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 以新版本重建分析任务
         * @description 原任务及历史结果不覆盖；新参数产生新版本任务。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；只允许原任务终态SUCCESS/FAILED/CANCELLED，版本不匹配409 RESOURCE_VERSION_CONFLICT。
         */
        post: operations["reviseAnalysisTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/analysis-tasks/exports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 合并导出选定分析任务结果
         * @description 每个分析任务须有analysis:read且SUCCESS；任一无权/未完成则拒绝整个请求，不生成部分文件；导出包含任务标识与分析类型。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["exportAnalysisTasks"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/audit-events/{eventId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看操作日志详情
         * @description 查看操作日志详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getAuditEvent"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/audit-events/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 导出操作日志
         * @description 导出操作日志；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["exportAuditEvents"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/audit-events/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 查询操作日志
         * @description 查询操作日志；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["searchAuditEvents"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/csrf": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 获取 CSRF 令牌
         * @description 匿名/已登录均可。同源且Origin/Referer校验，设置HttpOnly Secure SameSite=Lax Path=/api/v1 CSRFID随机Cookie，将绑定的同步器token返回body。CSRFID不是身份。写请求同时携带Cookie与X-CSRF-Token；登录SID轮换后重新获取，退出清理。禁止缓存或跨域通配。
         */
        get: operations["getCsrfToken"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 登录并创建服务端会话
         * @description 校验 X-CSRF-Token 与 Origin，成功时设置 HttpOnly Secure SameSite=Lax SID Cookie；失败统一提示，避免账号枚举。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；返回会话idleExpiresAt/absoluteExpiresAt，登录轮换SID；前端withCredentials并刷新csrf。
         */
        post: operations["login"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 退出并撤销会话
         * @description 退出并撤销会话；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["logout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/auth/session/renew": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 续期当前 Cookie 会话
         * @description 续期当前 Cookie 会话；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；；返回会话idleExpiresAt/absoluteExpiresAt，登录轮换SID；前端withCredentials并刷新csrf。 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["renewSession"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/batch-deletions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 批量软删选定记录
         * @description 外层200表示处理完成（包括部分失败），items.errorCode表示引用/版本/状态失败。对整个resourceType无删除权限直接403，全批不执行。资源类型权限映射见api-conventions。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["deleteResourcesBatch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/batch-revocations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 批量撤销布控或通知计划
         * @description PDF p15/p24上方勾选撤销。整类权限不足403不执行；200逐项结果。WATCH_RULE需watch:revoke，NOTIFICATION_TASK需notification:cancel；状态行为等同单条revoke/cancel。
         */
        post: operations["revokeResourcesBatch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/captures/{captureId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看采集记录详情
         * @description 查看采集记录详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getCapture"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/captures/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 按类型检索采集记录
         * @description 同步游标查询；客户端可中断 HTTP 等待，服务端按查询预算终止或返回 422/429。固定筛选快照由 POST /contexts 建立。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["searchCaptures"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/captures/statistics": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 统计上下文内目标
         * @description 统计上下文内目标；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["getCaptureStatistics"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/client-config": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取非机密前端配置
         * @description 读取非机密前端配置；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getClientConfig"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/contexts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 建立跨页研判上下文
         * @description 建立跨页研判上下文；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["createContext"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/contexts/{contextId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 重校权限后读取上下文
         * @description 读取不可变上下文并重校权限；过期返回410 CONTEXT_EXPIRED；范围变更且原对象不可读返回403 PERMISSION_DATA_SCOPE。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getContext"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/dashboard/summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取系统首页 12 类区域
         * @description 读取系统首页 12 类区域；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getDashboardSummary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/datasets": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询数据集
         * @description 查询数据集；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listDatasets"];
        put?: never;
        /**
         * 创建空或上下文数据集
         * @description 创建空或上下文数据集；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["createDataset"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/datasets/{datasetId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看数据集详情
         * @description 查看数据集详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getDataset"];
        put?: never;
        post?: never;
        /**
         * 受引用保护地软删数据集
         * @description 受引用保护地软删数据集；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deleteDataset"];
        options?: never;
        head?: never;
        /**
         * 重命名数据集
         * @description 重命名数据集；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        patch: operations["updateDataset"];
        trace?: never;
    };
    "/datasets/{datasetId}/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 异步导出数据集
         * @description 异步导出数据集；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["exportDataset"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/datasets/{datasetId}/imports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 从查询上下文或文件导入成员
         * @description 从查询上下文或文件导入成员；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["importDatasetItems"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/datasets/{datasetId}/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 游标读取数据集成员
         * @description 游标读取数据集成员；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listDatasetItems"];
        put?: never;
        /**
         * 批量添加数据集成员
         * @description 批量添加数据集成员；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["addDatasetItems"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/datasets/{datasetId}/items/{itemId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * 移除数据集成员
         * @description 按listDatasetItems返回的itemId移除，TARGET/CAPTURE共用同一资源入口。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["removeDatasetItem"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/datasets/{datasetId}/versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看数据集版本
         * @description 查看数据集版本；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listDatasetVersions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/decrypt-requests": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 提交解密申请
         * @description 申请转交或查看脱敏标识的解密材料；capture:read及当前范围必须满足，recipient具备capture:decrypt和当前范围，非涉密账号可申请但不能自行取得明文。两名非申请人复核，受控结果接口单独校验。
         */
        post: operations["requestDecryption"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/decrypt-requests/{requestId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询解密申请状态
         * @description 查询解密申请状态；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["getDecryptionRequest"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/decrypt-requests/{requestId}/result": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 指定接收人一次性查看受控解密结果
         * @description 指定recipient具备capture:decrypt和当前范围，已审批且5分钟许可未用/未过期时一次性查看；审批和密钥未就绪不执行。无URL/无持久明文文件，Cache-Control:no-store，记录访问元数据不记录明文；消耗后再次访问409，过期410。
         */
        post: operations["readDecryptionResult"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/deliveries": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 游标查看送达记录
         * @description 游标查看送达记录；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listDeliveries"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/deliveries/{deliveryId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看送达状态与失败原因
         * @description 查看送达状态与失败原因；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getDelivery"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/deliveries/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 导出短信送达日志
         * @description 导出短信送达日志；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["exportSmsLogs"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/deliveries/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 查询短信送达日志
         * @description 只返回 channel=SMS 的送达记录；按任务名称、收件人和发送时间筛选，收件人条件只在请求体。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["searchSmsLogs"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-commands": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询设备命令任务
         * @description 查询设备命令任务；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listDeviceCommands"];
        put?: never;
        /**
         * 下发远程设备命令
         * @description 冻结设备清单；每台设备有独立结果和幂等键。RESET 还需 device:reset 权限。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createDeviceCommand"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-commands/{taskId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看设备命令任务
         * @description 查看设备命令任务；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getDeviceCommand"];
        put?: never;
        post?: never;
        /**
         * 软删终态配置任务
         * @description 仅SUCCESS/FAILED/CANCELLED可软删，无在途命令或引用；审计与逐设备回执保留。执行中409 TASK_CONFLICT。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deleteDeviceCommand"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-commands/{taskId}/results": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看逐设备执行结果
         * @description 查看逐设备执行结果；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listDeviceCommandResults"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-commands/{taskId}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 重试失败的设备命令
         * @description 重试失败的设备命令；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["retryDeviceCommand"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-commands/{taskId}/revisions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 重建参数配置任务
         * @description 仅APPLY_PARAMETERS且原任务终态允许；返回新taskId，新参数执行新任务，历史回执不覆盖。RESET不可编辑返回409 TASK_CONFLICT。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；；只允许原任务终态SUCCESS/FAILED/CANCELLED，版本不匹配409 RESOURCE_VERSION_CONFLICT。 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["reviseDeviceCommand"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-groups": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询设备分组
         * @description 查询设备分组；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listDeviceGroups"];
        put?: never;
        /**
         * 新建设备分组
         * @description 新建设备分组；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["createDeviceGroup"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-groups/{groupId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看设备分组详情
         * @description 查看设备分组详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getDeviceGroup"];
        /**
         * 编辑设备分组
         * @description 编辑设备分组；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        put: operations["updateDeviceGroup"];
        post?: never;
        /**
         * 删除设备分组
         * @description 分组被参数任务引用时返回 409 RESOURCE_CONFLICT。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deleteDeviceGroup"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-groups/{groupId}/members": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询分组设备
         * @description 查询分组设备；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listDeviceGroupMembers"];
        put?: never;
        /**
         * 添加分组设备
         * @description 添加分组设备；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["addDeviceGroupMembers"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/device-groups/{groupId}/members/{deviceId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * 移除分组设备
         * @description 移除分组设备；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["removeDeviceGroupMember"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询设备
         * @description 查询设备；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listDevices"];
        put?: never;
        /**
         * 新增设备
         * @description 新增设备；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createDevice"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{deviceId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看设备详情
         * @description 查看设备详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getDevice"];
        /**
         * 编辑设备信息
         * @description 编辑设备信息；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        put: operations["updateDevice"];
        post?: never;
        /**
         * 删除设备
         * @description 已有关联采集、场所任务或远控任务时返回 409 RESOURCE_CONFLICT。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deleteDevice"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{deviceId}/capture-statistics": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看设备采集历史
         * @description 查看设备采集历史；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["getDeviceCaptureStatistics"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{deviceId}/parameter-updates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 提交设备参数更新
         * @description 提交设备参数更新；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["updateDeviceParameters"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{deviceId}/parameters": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取设备参数
         * @description 读取设备参数；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["getDeviceParameters"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{deviceId}/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看设备实时状态
         * @description 查看设备实时状态；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getDeviceStatus"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/{deviceId}/status-history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看设备在线离线历史
         * @description 查看设备在线离线历史；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listDeviceStatusHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/exports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 导出设备清单
         * @description 导出设备清单；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["exportDevices"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/devices/imports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 批量导入设备
         * @description 批量导入设备；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["importDevices"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/dictionaries/{dictionaryCode}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取前端字典
         * @description 读取前端字典；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getDictionary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/exports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 创建异步导出
         * @description 创建异步导出；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["createExport"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/faces/search-tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 创建人像相似检索任务
         * @description 创建人像相似检索任务；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createFaceSearchTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/faces/search-tasks/{taskId}/results": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 分页读取人像候选
         * @description 分页读取人像候选；未SUCCESS返回409 TASK_RESULT_NOT_READY，结果已清理返回410 FILE_EXPIRED；成功空结果为items=[]（适用分页响应）。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["listFaceSearchResults"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/files/{fileAssetId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询文件处理状态
         * @description 查询文件处理状态；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getFileAsset"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/files/{fileAssetId}/download": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 下载受控文件
         * @description 要求 file:download，且依据文件所属领域再次校验对应权限（如 face:download、audit:export），并设置安全下载响应头。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；只允许ACTIVE且未过期文件。Content-Type按FileAsset.mimeType，Content-Disposition为attachment并使用RFC5987 UTF8 filename*，禁止永久裸URL。
         */
        get: operations["downloadFile"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/files/uploads": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 上传受控文件
         * @description multipart file+category。FACE_SEARCH/PROFILE_PORTRAIT仅JPEG/PNG，数据导入XLSX/CSV/JSON，RAW_INGESTION按已确认映射。客户端先读client-config.uploadPolicies；超过maxBytes返回413，媒体不符415，先PROCESSING→ACTIVE/QUARANTINED。无需把供应商原始字段向前端开放。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["uploadFile"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 存活检查
         * @description 存活检查；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getHealth"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/import-templates/{templateCode}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取导入模板列与文件约定
         * @description 固定三类v1模板，列名/格式/事务语义与文档一致；下载模板可由前端基于列目录生成，无需新增业务写入。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getImportTemplate"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ingestion-batches": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询接入批次
         * @description 查询接入批次；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listIngestionBatches"];
        put?: never;
        /**
         * 提交文件接入批次
         * @description 提交文件接入批次；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createIngestionBatch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ingestion-batches/{batchId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看接入批次详情
         * @description 查看接入批次详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getIngestionBatch"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ingestion-batches/{batchId}/errors": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看逐条接入错误
         * @description 查看逐条接入错误；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listIngestionErrors"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ingestion-batches/{batchId}/replay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 按版本回放批次
         * @description 按版本回放批次；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["replayIngestionBatch"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ingestion/backfills": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 创建补数任务
         * @description 创建补数任务；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createIngestionBackfill"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ingestion/quality-summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看接入质量汇总
         * @description 查看接入质量汇总；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getIngestionQualitySummary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/map/area-summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 统计选区内采集与设备
         * @description 统计选区内采集与设备；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["getMapAreaSummary"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/map/devices": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询设备点与聚合
         * @description 查询设备点与聚合；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getDeviceMapFeatures"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/map/features": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询采集点/聚合地图要素
         * @description 查询采集点/聚合地图要素；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getMapFeatures"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/map/heatmap": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 按时间窗读取设备采集热力
         * @description 按时间窗读取设备采集热力；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getMapHeatmap"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取当前用户、菜单与权限范围
         * @description 读取当前用户、菜单与权限范围；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getMe"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/me/password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 修改本人密码
         * @description currentPassword和newPassword必填；凭据错误401 AUTH_INVALID_CREDENTIALS，成功撤销本人其它会话。密码复杂度取已签认部署策略。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["changeMyPassword"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/menus": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取可分配菜单目录
         * @description 读取可分配菜单目录；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listMenuCatalog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/messages": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询本人平台消息
         * @description 仅当前用户，来自告警或通知计划；消息计数按当前加载记录/服务端摘要呈现，不等同告警总数。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listPlatformMessages"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/metrics/exports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 导出首页或运维指标图表
         * @description 返回导出任务，成功经文件二次授权下载。指标来源与汇总响应完全相同，不能以采集明细替代图表。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；；DASHBOARD需要dashboard:read；OPERATIONS同读权限，另需capture:export；该源/指标组合不匹配422 VALIDATION_ERROR。 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["exportMetrics"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/model-tags": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询模型标签目录
         * @description 查询模型标签目录；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["listModelTags"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/notification-tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询通知任务
         * @description 查询通知任务；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listNotificationTasks"];
        put?: never;
        /**
         * 创建定时通知任务
         * @description 同步创建持久通知计划返回201和notificationTaskId；到期每次执行创建独立taskId，lastExecutionTaskId可轮询。平台消息收件用户与SMS/EMAIL收件地址均显式提供。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；；创建禁止RETAINED；地址转换成服务端recipientId，详情返回脱敏摘要。 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createNotificationTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/notification-tasks/{notificationTaskId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看通知任务详情
         * @description 查看通知任务详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getNotificationTask"];
        /**
         * 更新未终结通知计划
         * @description 仅ACTIVE可编辑，原已发送Delivery不改变，新版从下一次计划执行生效；相同日期不得重复发送。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；；targets可用RETAINED保留既有地址；新地址只在请求体，不能用recipientMasked回填。 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        put: operations["updateNotificationTask"];
        post?: never;
        /**
         * 软删已终结通知任务
         * @description 只允许CANCELLED或COMPLETED且无在途执行，保留Delivery/审计。ACTIVE返回409 TASK_CONFLICT，先撤销。
         */
        delete: operations["deleteNotificationTask"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/notification-tasks/{notificationTaskId}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 请求取消未完成通知
         * @description 撤销计划停止下次调度；已发送副作用不撤回；在途执行发起取消，plan.status=CANCELLED，执行状态单独通过task读取。重复撤销返回当前计划。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["cancelNotificationTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/notification-tasks/{notificationTaskId}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 重试符合条件的失败通知
         * @description 仅最近一次执行明确FAILED且可重试Delivery时恢复同executionTaskId、attemptNo+1，只重试未成功收件人；更新lastExecutionTaskId并返回TaskCreated。UNKNOWN先对账，CANCELLED计划不能重试。；按暂定规则开发，实际通道未就绪返回503 SYSTEM_UNAVAILABLE。
         */
        post: operations["retryNotificationTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/notification-templates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询已批准通知模板
         * @description 根据purpose和channels选择模板，无可用模板时不能发送；正文由后台模板维护，不开放供应商管理。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["listNotificationTemplates"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/operations/summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取运维首页四类区域
         * @description granularity表示近WEEK/MONTH/QUARTER/YEAR展示范围，按机构时区向前滚动至asOf；timeRange返回实际边界，DAY桶用于前三类、MONTH用于YEAR。按byDataType显示四图，复用首页/报表相同口径。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getOperationSummary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/permissions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取可分配权限目录
         * @description 包含可分配按钮/API权限；不只限于当前用户已拥有的菜单。后端仍禁止授予超出授予者授权的权限。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listPermissionCatalog"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/person-libraries": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询人员库
         * @description 查询人员库；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listPersonLibraries"];
        put?: never;
        /**
         * 新建人员库
         * @description 新建人员库；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["createPersonLibrary"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/person-libraries/{libraryId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看人员库详情
         * @description 查看人员库详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getPersonLibrary"];
        /**
         * 编辑人员库
         * @description 编辑人员库；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        put: operations["updatePersonLibrary"];
        post?: never;
        /**
         * 删除人员库
         * @description 有布控、档案或任务引用时返回 409 RESOURCE_CONFLICT；删除写审计。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deletePersonLibrary"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/person-libraries/{libraryId}/exports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 导出人员库
         * @description 导出人员库；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["exportPersonLibrary"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/person-libraries/{libraryId}/imports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 从文件导入人员库成员
         * @description 从文件导入人员库成员；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["importPersonLibraryMembers"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/person-libraries/{libraryId}/members": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询人员库成员
         * @description 查询人员库成员；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listPersonLibraryMembers"];
        put?: never;
        /**
         * 添加人员库成员
         * @description 添加人员库成员；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["addPersonLibraryMembers"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/person-libraries/{libraryId}/members/{memberId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * 移除人员库成员
         * @description 移除人员库成员；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["removePersonLibraryMember"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/person-libraries/{libraryId}/persons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 新增库内人员及手工档案
         * @description 在同一事务登记目标、创建PERSON档案和库成员。明确重复已有关联标识时409 RESOURCE_CONFLICT，不能静默把不同人合并。编辑后续走档案corrections；删除成员不删除全局档案。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createLibraryPerson"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/person-libraries/exports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 导出选定多个人员库
         * @description 导出选定多个人员库；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["exportPersonLibraries"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/person-libraries/imports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 批量导入库列表和库内人员
         * @description 支持库名/分类与人员资料。导入同时登记手工档案/成员；全部行预检，任一错误回滚。错误走/tasks/{taskId}/errors，成功TaskResultReference指向PERSON_LIBRARY_IMPORT结果。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["importPersonLibraries"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/{profileId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看档案详情
         * @description 查看档案详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["getProfile"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/{profileId}/conflicts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取可复核档案冲突
         * @description 读取可复核档案冲突；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["listProfileConflicts"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/{profileId}/conflicts/{conflictId}/resolve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 处理档案证据冲突
         * @description 处理档案证据冲突；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["resolveProfileConflict"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/{profileId}/corrections": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 人工修订档案
         * @description 人工修订档案；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createProfileCorrection"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/{profileId}/evidence": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看档案证据
         * @description 查看档案证据；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listProfileEvidence"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/{profileId}/exports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 导出档案
         * @description 导出档案；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["exportProfile"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/{profileId}/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看档案修订历史
         * @description 查看档案修订历史；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listProfileHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/{profileId}/split": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 拆分档案
         * @description 拆分档案；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["splitProfile"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/{profileId}/targets": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看档案关联目标
         * @description 查看档案关联目标；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["listProfileTargets"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/{profileId}/trajectories": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看档案轨迹
         * @description 查看档案轨迹；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；；固定captureTime ASC/captureId ASC；各点target用于分组，不跨目标连线。 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["listProfileTrajectories"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/merge": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 合并档案
         * @description 合并档案；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["mergeProfiles"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/profiles/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 检索人车档案
         * @description 检索人车档案；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["searchProfiles"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ready": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 依赖就绪检查
         * @description 供受控探针使用，不暴露内部地址或凭据。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getReadiness"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/regions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取授权区域目录
         * @description 为地图区域、场所、报表选区提供code和名称；仅返回当前范围内区域。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listRegions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询报表任务
         * @description 查询报表任务；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listReports"];
        put?: never;
        /**
         * 创建报表任务
         * @description 创建报表任务；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createReport"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/{reportId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看报表详情
         * @description 查看报表详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getReport"];
        put?: never;
        post?: never;
        /**
         * 删除报表任务
         * @description 仅允许按任务生命周期清理；下载/审计记录另行保留。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deleteReport"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/{reportId}/download": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 下载报表文件
         * @description 成功任务经当前权限复核后流式下载 XLSX 或 ZIP。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["downloadReport"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/{reportId}/preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 预览单报表
         * @description 仅成功的 SINGLE 报表可预览；BUNDLE 返回 422。；未SUCCESS返回409 TASK_RESULT_NOT_READY，结果已清理返回410 FILE_EXPIRED；成功空结果为items=[]（适用分页响应）。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["getReportPreview"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/reports/{reportId}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 重试失败报表
         * @description 仅 FAILED 报表可重试；原参数版本保留。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["retryReport"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询角色
         * @description 查询角色；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listRoles"];
        put?: never;
        /**
         * 新增角色
         * @description 新增角色；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createRole"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/roles/{roleId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看角色详情
         * @description 查看角色详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getRole"];
        /**
         * 编辑角色基本信息
         * @description 编辑角色基本信息；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        put: operations["updateRole"];
        post?: never;
        /**
         * 删除未被引用的角色
         * @description 仍有用户关联（包括已禁用但未删除用户）时409 RESOURCE_CONFLICT；无引用可软删。必须保留可管理权限的管理员角色。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deleteRole"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/roles/{roleId}/permissions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * 设置角色菜单和按钮权限
         * @description 设置角色菜单和按钮权限；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        put: operations["setRolePermissions"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/roles/{roleId}/scopes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * 设置角色场所和数据范围
         * @description 设置角色场所和数据范围；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        put: operations["setRoleScopes"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/set-tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询集合运算任务
         * @description 查询集合运算任务；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listSetTasks"];
        put?: never;
        /**
         * 创建异步集合运算
         * @description 创建异步集合运算；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；表达式符号须全部来自operands、无多余未使用操作数；operandVersions校验失败409 RESOURCE_VERSION_CONFLICT。
         */
        post: operations["createSetTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/set-tasks/{setTaskId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看集合任务详情
         * @description 查看集合任务详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getSetTask"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/set-tasks/{setTaskId}/results": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 游标查看集合结果
         * @description 游标查看集合结果；未SUCCESS返回409 TASK_RESULT_NOT_READY，结果已清理返回410 FILE_EXPIRED；成功空结果为items=[]（适用分页响应）。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listSetTaskResults"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/set-tasks/preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 预览集合运算兼容性与估量
         * @description 预览集合运算兼容性与估量；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。；表达式符号须全部来自operands、无多余未使用操作数；operandVersions校验失败409 RESOURCE_VERSION_CONFLICT。
         */
        post: operations["previewSetTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/sites": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询场所
         * @description 查询场所；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listSites"];
        put?: never;
        /**
         * 新增场所
         * @description 新增场所；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["createSite"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/sites/{siteId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看场所详情
         * @description 查看场所详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getSite"];
        /**
         * 编辑场所
         * @description 编辑场所；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        put: operations["updateSite"];
        post?: never;
        /**
         * 删除场所
         * @description 存在设备、分组、任务或采集数据引用时返回 409 RESOURCE_CONFLICT。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deleteSite"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/targets/resolve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 批量解析或登记目标代理ID
         * @description 批量最多100。查找需capture:read；登记需watch:write或person-library:write，并校验类型/场所范围。登记不是身份关联，无采集也可布控。每项独立状态，目标唯一键为类型+规范化指纹，重复返回同ID。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["resolveTargets"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询当前可见任务
         * @description 用户仅看自身或其领域权限/数据范围内任务。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listTasks"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/{taskId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取任务状态与结果引用
         * @description 读取任务状态与结果引用；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getTask"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/{taskId}/attempts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看任务尝试历史
         * @description 查看任务尝试历史；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listTaskAttempts"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/{taskId}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 请求取消任务
         * @description PENDING/QUEUED可转CANCELLED；RUNNING只记cancelRequestedAt并返回202。SUCCESS/FAILED返回409 TASK_NOT_CANCELLABLE；CANCELLED重复取消返回202既有状态。已发送副作用不撤回。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["cancelTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/{taskId}/errors": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 分页查看文件导入的逐行错误
         * @description 仅文件导入任务有逐行错误；其它任务返回 409 TASK_CONFLICT。需同时满足任务所有权与所属领域读取权限。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listTaskRowErrors"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/{taskId}/import-result": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 读取成功导入结果和领域ID
         * @description 仅三类IMPORT且SUCCESS，未完成409 TASK_RESULT_NOT_READY，已清理410 FILE_EXPIRED；领域读权限和任务所有权均重校。
         */
        get: operations["getImportTaskResult"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/tasks/{taskId}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 重试失败任务并保留 taskId
         * @description 仅FAILED允许重试，同taskId新建尝试。其它状态409 TASK_CONFLICT；终态结果原子发布，外部回执UNKNOWN禁止自动重发。按所属领域重新授权。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["retryTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/trajectories/query": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 分页查询目标轨迹
         * @description 按 captureTime、captureId 稳定升序；输入敏感目标在请求体，输出轨迹点分页。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["queryTrajectory"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/trend-models": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询趋势模型
         * @description 查询趋势模型；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["listTrendModels"];
        put?: never;
        /**
         * 新建趋势模型
         * @description 新建趋势模型；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createTrendModel"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/trend-models/{modelId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看趋势模型详情
         * @description 查看趋势模型详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["getTrendModel"];
        /**
         * 编辑趋势模型
         * @description 编辑趋势模型；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        put: operations["updateTrendModel"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/trend-models/{modelId}/parameter-versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询模型参数版本
         * @description 查询模型参数版本；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listModelParameterVersions"];
        put?: never;
        /**
         * 新增模型参数版本
         * @description 新增模型参数版本；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createModelParameterVersion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/trend-models/{modelId}/parameter-versions/{parameterVersionId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看模型参数版本
         * @description 查看模型参数版本；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getModelParameterVersion"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/trend-models/{modelId}/results": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看模型执行结果
         * @description 查看模型执行结果；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["listTrendModelResults"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/trend-models/{modelId}/results/{resultId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看模型结果详情
         * @description 查看模型结果详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        get: operations["getTrendModelResult"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/trend-models/{modelId}/runs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 执行趋势模型
         * @description 执行趋势模型；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["runTrendModel"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询用户
         * @description 查询用户；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listUsers"];
        put?: never;
        /**
         * 新增用户
         * @description 新增用户；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/users/{userId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看用户详情
         * @description 查看用户详情；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getUser"];
        /**
         * 编辑用户
         * @description 编辑用户；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        put: operations["updateUser"];
        post?: never;
        /**
         * 删除已禁用用户
         * @description 仅DISABLED可软删，不撤回历史任务/审计；活跃任务引用409 RESOURCE_CONFLICT，历史引用不阻止软删。当前用户和最后一个超级管理员不可删。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deleteUser"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/users/{userId}/disable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 禁用用户
         * @description 禁用用户；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["disableUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/users/{userId}/enable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 启用用户
         * @description 启用用户；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["enableUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/users/{userId}/password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 修改用户密码
         * @description 管理员重置密码，不接收currentPassword；本人改密使用/me/password。审计用途必填reason，成功撤销该用户所有会话。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["changeUserPassword"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/watch-rules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查询布控规则
         * @description 查询布控规则；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["listWatchRules"];
        put?: never;
        /**
         * 创建布控草稿
         * @description 创建布控草稿；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["createWatchRule"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/watch-rules/{ruleId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看规则详情和当前版本
         * @description 查看规则详情和当前版本；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getWatchRule"];
        put?: never;
        post?: never;
        /**
         * 软删终态布控规则
         * @description 软删终态布控规则；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        delete: operations["deleteWatchRule"];
        options?: never;
        head?: never;
        /**
         * 修改规则并生成新版本
         * @description 修改规则并生成新版本；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        patch: operations["updateWatchRule"];
        trace?: never;
    };
    "/watch-rules/{ruleId}/activate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 激活布控草稿
         * @description 激活布控草稿；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。； 暂定开发规则见PD-2026-10-19.v1；真实资源缺失返回503 SYSTEM_UNAVAILABLE或按已公布能力原因禁用，不返回伪成功。
         */
        post: operations["activateWatchRule"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/watch-rules/{ruleId}/revoke": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * 撤销运行中的布控
         * @description 撤销后不再生成新命中；已发送告警和送达事件保留。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        post: operations["revokeWatchRule"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/watch-rules/{ruleId}/statistics": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * 查看实时命中数或最终统计
         * @description 仅FINISHED/REVOKED生成最终逐目标统计，ACTIVE/DRAFT返回409 TASK_RESULT_NOT_READY；实时命中调用/alerts?ruleId。每项目标可用于检索或创建伴随Context。；成功数据见具体响应Schema；空集合返回空数组。除PUBLIC外强制当前会话、所需权限、数据/场所范围；越权对象按404 DATA_NOT_FOUND隐藏存在性。
         */
        get: operations["getWatchStatistics"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
};
export type webhooks = Record<string, never>;
export type components = {
    schemas: {
        ActionApprovalDecision: {
            /** Format: date-time */
            decidedAt: string;
            /** @enum {string} */
            decision: "APPROVE" | "REJECT";
            /** Format: uuid */
            decisionId: string;
            reason: string;
            /** Format: uuid */
            reviewerUserId: string;
        };
        ActionApprovalDecisionInput: {
            /** Format: password */
            currentPassword: string;
            /** @enum {string} */
            decision: "APPROVE" | "REJECT";
            reason: string;
        };
        ActionApprovalDetail: {
            /** Format: uuid */
            approvalId: string;
            /** Format: date-time */
            createdAt: string;
            decisions: components["schemas"]["ActionApprovalDecision"][];
            /** Format: date-time */
            executionGrantExpiresAt: string | null;
            /** Format: uuid */
            executorUserId: string;
            /** Format: date-time */
            expiresAt: string;
            operationId: string;
            requestDigest: string;
            /** Format: uuid */
            requesterUserId: string;
            requiredApprovals: number;
            /** @description 服务端从冻结快照生成的完整重要字段清单，包含目标集合/当前版本/权限范围/用途；敏感值掩码，不含密码/密钥/标识明文。不得只给截断摘要就批准；超上限拒绝并要求拆分申请。 */
            reviewItems: {
                displayValue: string;
                fieldCode: string;
                label: string;
            }[];
            /** @enum {string} */
            status: "PENDING" | "APPROVED" | "REJECTED" | "EXPIRED" | "CONSUMED";
            summary: string;
            version: number;
        };
        ActionApprovalInput: {
            proposal: components["schemas"]["ActionApprovalProposal"];
            reason: string;
        };
        ActionApprovalPage: {
            items: components["schemas"]["ActionApprovalDetail"][];
            nextCursor: string | null;
            pageSize: number;
        };
        ActionApprovalPageResponse: {
            /** @enum {string} */
            code: "0";
            data: components["schemas"]["ActionApprovalPage"];
            message: string;
            traceId: string;
        };
        /** @description 按operationId分型的完整冻结提案；服务端计算摘要，私密字段加密存储、响应只返回脱敏summary。审批不接受任意未登记操作。 */
        ActionApprovalProposal: components["schemas"]["CreateDeviceCommandApprovalProposal"] | components["schemas"]["UpdateDeviceParametersApprovalProposal"] | components["schemas"]["RetryDeviceCommandApprovalProposal"] | components["schemas"]["ReviseDeviceCommandApprovalProposal"] | components["schemas"]["SetRolePermissionsApprovalProposal"] | components["schemas"]["SetRoleScopesApprovalProposal"] | components["schemas"]["CreateUserApprovalProposal"] | components["schemas"]["UpdateUserApprovalProposal"] | components["schemas"]["DeleteUserApprovalProposal"] | components["schemas"]["DisableUserApprovalProposal"] | components["schemas"]["EnableUserApprovalProposal"] | components["schemas"]["CreateRoleApprovalProposal"] | components["schemas"]["UpdateRoleApprovalProposal"] | components["schemas"]["DeleteRoleApprovalProposal"] | components["schemas"]["ChangeUserPasswordApprovalProposal"] | components["schemas"]["ReplayIngestionBatchApprovalProposal"] | components["schemas"]["CreateIngestionBackfillApprovalProposal"] | components["schemas"]["MergeProfilesApprovalProposal"] | components["schemas"]["SplitProfileApprovalProposal"] | components["schemas"]["CreateExportApprovalProposal"] | components["schemas"]["ExportAnalysisResultsApprovalProposal"] | components["schemas"]["ExportAnalysisTasksApprovalProposal"] | components["schemas"]["ExportDatasetApprovalProposal"] | components["schemas"]["ExportPersonLibraryApprovalProposal"] | components["schemas"]["ExportPersonLibrariesApprovalProposal"] | components["schemas"]["ExportProfileApprovalProposal"] | components["schemas"]["ExportMetricsApprovalProposal"] | components["schemas"]["ExportAuditEventsApprovalProposal"] | components["schemas"]["ExportSmsLogsApprovalProposal"] | components["schemas"]["ReadDecryptionResultApprovalProposal"];
        ActionApprovalResponse: {
            /** @enum {string} */
            code: "0";
            data: components["schemas"]["ActionApprovalDetail"];
            message: string;
            traceId: string;
        };
        /**
         * @description 操作结果；若对应新资源则 resourceId 必须返回。
         * @example {
         *       "accepted": true
         *     }
         */
        ActionResult: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            accepted: boolean;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resourceId?: string;
        };
        /** @description 接口模型 ActivateWatchRuleResponse；具体业务范围以使用它的操作为准。 */
        ActivateWatchRuleResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["WatchRuleDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 AddDatasetItemsResponse；具体业务范围以使用它的操作为准。 */
        AddDatasetItemsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DatasetDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 AddDeviceGroupMembersResponse；具体业务范围以使用它的操作为准。 */
        AddDeviceGroupMembersResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceGroupDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 AddPersonLibraryMembersResponse；具体业务范围以使用它的操作为准。 */
        AddPersonLibraryMembersResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["PersonLibraryDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 沿用LOW/MEDIUM/HIGH和OPEN/ACKNOWLEDGED/CLOSED；默认LOW，等级由规则配置，不表示犯罪概率。watch:write确认/关闭，reason必填；确认后不自动隐藏，保存一年（12个自然月）。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "alertId": "00000000-0000-4000-8000-000000000001",
         *       "ruleId": "00000000-0000-4000-8000-000000000001",
         *       "ruleVersion": 0,
         *       "captureId": "00000000-0000-4000-8000-000000000001",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "occurredAt": "2026-10-07T02:00:00Z",
         *       "severity": "LOW",
         *       "status": "OPEN",
         *       "deliveryStatus": "PENDING",
         *       "traceId": "mock-example"
         *     }
         */
        Alert: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            alertId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            captureId: string;
            deliveryStatus: components["schemas"]["DeliveryStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId?: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            occurredAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ruleId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            ruleVersion: number;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            severity: "LOW" | "MEDIUM" | "HIGH";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            status: "OPEN" | "ACKNOWLEDGED" | "CLOSED";
            target: components["schemas"]["ContextTarget"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 AlertDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "alert": {
         *         "alertId": "00000000-0000-4000-8000-000000000001",
         *         "ruleId": "00000000-0000-4000-8000-000000000001",
         *         "ruleVersion": 0,
         *         "captureId": "00000000-0000-4000-8000-000000000001",
         *         "target": {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         },
         *         "occurredAt": "2026-10-07T02:00:00Z",
         *         "severity": "LOW",
         *         "status": "OPEN",
         *         "deliveryStatus": "PENDING",
         *         "traceId": "mock-example"
         *       },
         *       "capture": {
         *         "captureId": "00000000-0000-4000-8000-000000000001",
         *         "dataType": "FENCE",
         *         "targetId": null,
         *         "deviceId": null,
         *         "siteId": null,
         *         "captureTime": "2026-10-07T02:00:00Z",
         *         "ingestedAt": "2026-10-07T02:00:00Z",
         *         "longitude": null,
         *         "latitude": null,
         *         "source": "mock-example",
         *         "status": "VALID",
         *         "displayTarget": "mock-example",
         *         "isMasked": true,
         *         "fence": {
         *           "imsiMasked": null,
         *           "imeiMasked": null
         *         },
         *         "targetType": "IMSI"
         *       },
         *       "watchRuleSnapshot": {
         *         "ruleId": "00000000-0000-4000-8000-000000000001",
         *         "version": 0,
         *         "name": "mock-example",
         *         "conditions": {
         *           "targets": [
         *             {
         *               "target": {
         *                 "targetId": "00000000-0000-4000-8000-000000000001",
         *                 "targetType": "IMSI"
         *               }
         *             }
         *           ],
         *           "siteIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "dataTypes": [
         *             "FENCE"
         *           ]
         *         },
         *         "effectiveFrom": "2026-10-07T02:00:00Z",
         *         "effectiveUntil": "2026-10-08T02:00:00Z"
         *       },
         *       "deliveries": [
         *         {
         *           "deliveryId": "00000000-0000-4000-8000-000000000001",
         *           "channel": "SMS",
         *           "recipientMasked": "mock-example",
         *           "status": "PENDING",
         *           "retryCount": 0,
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "traceId": "mock-example",
         *           "taskName": "mock-example",
         *           "contentSummary": "mock-example"
         *         }
         *       ]
         *     }
         */
        AlertDetail: {
            alert: components["schemas"]["Alert"];
            capture: components["schemas"]["CaptureRecord"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deliveries: components["schemas"]["Delivery"][];
            watchRuleSnapshot: components["schemas"]["WatchRuleSnapshot"];
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "alertId": "00000000-0000-4000-8000-000000000001",
         *           "ruleId": "00000000-0000-4000-8000-000000000001",
         *           "ruleVersion": 0,
         *           "captureId": "00000000-0000-4000-8000-000000000001",
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "occurredAt": "2026-10-07T02:00:00Z",
         *           "severity": "LOW",
         *           "status": "OPEN",
         *           "deliveryStatus": "PENDING",
         *           "traceId": "mock-example"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        AlertPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["Alert"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 访问有效期固定创建后30分钟，不因复用续期，到期410 CONTEXT_EXPIRED；物理元数据保存一年（12个自然月）。每次消费重校权限，缩权403；targets最多100个预览，不代表完整输入。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *       "sourceModule": "mock-example",
         *       "targets": [
         *         {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         }
         *       ],
         *       "filter": {
         *         "filterType": "GENERAL",
         *         "filterVersion": 1,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         }
         *       },
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "expiresAt": "2026-10-07T02:00:00Z",
         *       "permissionVersion": 0,
         *       "targetCount": 0,
         *       "targetsTruncated": true
         *     }
         */
        AnalysisContext: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            expiresAt: string;
            filter: components["schemas"]["ContextFilter"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ownerUserId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            permissionVersion: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            sourceModule: string;
            spatialRange?: components["schemas"]["ContextSpatialRange"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targets: components["schemas"]["ContextTarget"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetsTruncated: boolean;
        };
        /**
         * @description 创建时强制当前数据范围校验；Context 的 TTL 由服务端配置。；targets=[]适用于全量筛选/来源快照，非空仅限制指定目标。设备选区不是目标选择。来源筛选不展开到浏览器，无界目标集合保存服务端。；源选择不同时出现互相矛盾的target/context过滤，服务端422 VALIDATION_ERROR。；源选择不同时出现互相矛盾的target/context过滤，服务端422 VALIDATION_ERROR。
         * @example {
         *       "sourceModule": "M04",
         *       "targets": [
         *         {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         }
         *       ],
         *       "filter": {
         *         "filterType": "GENERAL",
         *         "filterVersion": 1,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         }
         *       }
         *     }
         */
        AnalysisContextInput: {
            filter: components["schemas"]["ContextFilter"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            sourceModule: "M04" | "M05" | "M06" | "M07" | "M08" | "M09" | "M10" | "M11" | "M12" | "M13";
            spatialRange?: components["schemas"]["ContextSpatialRange"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targets: components["schemas"]["ContextTarget"][];
        };
        /**
         * @description 接口模型 AnalysisResultItem；具体业务范围以使用它的操作为准。
         * @example {
         *       "resultId": "00000000-0000-4000-8000-000000000001",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "evidenceCaptureIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "algorithmVersion": "mock-example",
         *       "kind": "COMPANION",
         *       "commonOccurrenceCount": 0,
         *       "sharedSiteCount": 0,
         *       "trajectoryOverlapCount": 0,
         *       "displayTarget": "mock-example",
         *       "dataType": "FENCE",
         *       "captureCount": 0,
         *       "siteCount": 0,
         *       "regionCount": 0,
         *       "visitedRegionCodes": [
         *         "mock-example"
         *       ]
         *     }
         */
        AnalysisResultItem: components["schemas"]["CompanionResultItem"] | components["schemas"]["CollisionResultItem"] | components["schemas"]["RegionResultItem"];
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "resultId": "00000000-0000-4000-8000-000000000001",
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "evidenceCaptureIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "algorithmVersion": "mock-example",
         *           "kind": "COMPANION",
         *           "commonOccurrenceCount": 0,
         *           "sharedSiteCount": 0,
         *           "trajectoryOverlapCount": 0,
         *           "displayTarget": "mock-example",
         *           "dataType": "FENCE",
         *           "captureCount": 0,
         *           "siteCount": 0,
         *           "regionCount": 0,
         *           "visitedRegionCodes": [
         *             "mock-example"
         *           ]
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        AnalysisResultPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["AnalysisResultItem"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description analysisTaskId与taskId分别为领域资源和执行任务；修订返回新的两个ID，原结果不覆盖。
         * @example {
         *       "analysisTaskId": "00000000-0000-4000-8000-000000000001",
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "status": "PENDING",
         *       "createdAt": "2026-10-07T02:00:00Z"
         *     }
         */
        AnalysisTaskCreated: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            analysisTaskId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            status: components["schemas"]["TaskStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId: string;
        };
        /**
         * @description 接口模型 AnalysisTaskDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "task": {
         *         "taskId": "00000000-0000-4000-8000-000000000001",
         *         "taskType": "INGESTION",
         *         "status": "PENDING",
         *         "progress": 0,
         *         "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *         "createdAt": "2026-10-07T02:00:00Z",
         *         "retryCount": 0,
         *         "parameterSnapshot": {
         *           "schemaVersion": "mock-example",
         *           "submittedAt": "2026-10-07T02:00:00Z",
         *           "parameterHash": "0000000000000000000000000000000000000000000000000000000000000000"
         *         },
         *         "traceId": "mock-example",
         *         "name": "mock-example"
         *       },
         *       "name": "mock-example",
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "resultCount": null,
         *       "version": 0,
         *       "kind": "COMPANION",
         *       "parameters": {
         *         "target": {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         },
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "timeWindowSeconds": 1,
         *         "spatialWindowMeters": 0
         *       },
         *       "analysisTaskId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        AnalysisTaskDetail: components["schemas"]["CompanionAnalysisTaskDetail"] | components["schemas"]["CollisionAnalysisTaskDetail"] | components["schemas"]["RegionAnalysisTaskDetail"];
        /**
         * @description 接口模型 AnalysisTaskInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "kind": "COMPANION",
         *       "name": "mock-example",
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "parameters": {
         *         "target": {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         },
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "timeWindowSeconds": 1,
         *         "spatialWindowMeters": 0
         *       }
         *     }
         */
        AnalysisTaskInput: components["schemas"]["CompanionAnalysisTaskInput"] | components["schemas"]["CollisionAnalysisTaskInput"] | components["schemas"]["RegionAnalysisTaskInput"];
        /**
         * @description 接口模型 AnalysisTaskPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "task": {
         *             "taskId": "00000000-0000-4000-8000-000000000001",
         *             "taskType": "INGESTION",
         *             "status": "PENDING",
         *             "progress": 0,
         *             "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *             "createdAt": "2026-10-07T02:00:00Z",
         *             "retryCount": 0,
         *             "parameterSnapshot": {
         *               "schemaVersion": "mock-example",
         *               "submittedAt": "2026-10-07T02:00:00Z",
         *               "parameterHash": "0000000000000000000000000000000000000000000000000000000000000000"
         *             },
         *             "traceId": "mock-example",
         *             "name": "mock-example"
         *           },
         *           "name": "mock-example",
         *           "contextId": "00000000-0000-4000-8000-000000000001",
         *           "resultCount": null,
         *           "version": 0,
         *           "kind": "COMPANION",
         *           "parameters": {
         *             "target": {
         *               "targetId": "00000000-0000-4000-8000-000000000001",
         *               "targetType": "IMSI"
         *             },
         *             "timeRange": {
         *               "from": "2026-10-07T02:00:00Z",
         *               "to": "2026-10-07T03:00:00Z"
         *             },
         *             "timeWindowSeconds": 1,
         *             "spatialWindowMeters": 0
         *           },
         *           "analysisTaskId": "00000000-0000-4000-8000-000000000001"
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        AnalysisTaskPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["AnalysisTaskDetail"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 AnalysisTaskRevisionInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "kind": "COMPANION",
         *       "parameters": {
         *         "target": {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         },
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "timeWindowSeconds": 1,
         *         "spatialWindowMeters": 0
         *       },
         *       "reason": "mock-example"
         *     }
         */
        AnalysisTaskRevisionInput: components["schemas"]["CompanionAnalysisRevisionInput"] | components["schemas"]["CollisionAnalysisRevisionInput"] | components["schemas"]["RegionAnalysisRevisionInput"];
        /**
         * @example {
         *       "analysisTaskIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "format": "XLSX",
         *       "reason": "mock-example"
         *     }
         */
        AnalysisTasksExportInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            analysisTaskIds: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 接口模型 AnomalyOverview；具体业务范围以使用它的操作为准。
         * @example {
         *       "meta": {
         *         "asOf": null,
         *         "status": "READY",
         *         "unit": "mock-example",
         *         "refreshPolicy": "POLL"
         *       },
         *       "deviceAnomalies": [
         *         {
         *           "code": "UNREGISTERED_DEVICE",
         *           "points": [
         *             {
         *               "at": "2026-10-07T02:00:00Z",
         *               "value": null
         *             }
         *           ]
         *         }
         *       ],
         *       "dataAnomalies": [
         *         {
         *           "code": "UNREGISTERED_DEVICE",
         *           "points": [
         *             {
         *               "at": "2026-10-07T02:00:00Z",
         *               "value": null
         *             }
         *           ]
         *         }
         *       ],
         *       "volumeAnomalies": [
         *         {
         *           "code": "UNREGISTERED_DEVICE",
         *           "points": [
         *             {
         *               "at": "2026-10-07T02:00:00Z",
         *               "value": null
         *             }
         *           ]
         *         }
         *       ]
         *     }
         */
        AnomalyOverview: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dataAnomalies: components["schemas"]["AnomalySeries"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceAnomalies: components["schemas"]["AnomalySeries"][];
            meta: components["schemas"]["MetricMeta"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            volumeAnomalies: components["schemas"]["AnomalySeries"][];
        };
        /**
         * @description 接口模型 AnomalySeries；具体业务范围以使用它的操作为准。
         * @example {
         *       "code": "UNREGISTERED_DEVICE",
         *       "points": [
         *         {
         *           "at": "2026-10-07T02:00:00Z",
         *           "value": null
         *         }
         *       ]
         *     }
         */
        AnomalySeries: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "UNREGISTERED_DEVICE" | "DUPLICATE_DEVICE_DATA" | "DEVICE_TIME_ANOMALY" | "DEVICE_MODE_ANOMALY" | "UNREGISTERED_DATA" | "DUPLICATE_DATA" | "DATA_TIME_ANOMALY" | "DATA_MODE_ANOMALY" | "DATA_FORMAT_ANOMALY" | "ONLINE_NO_DATA" | "DATA_SPIKE" | "DATA_DROP";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            points: components["schemas"]["MetricPoint"][];
        };
        /**
         * @description 接口模型 ApplicationEntry；具体业务范围以使用它的操作为准。
         * @example {
         *       "code": "mock-example",
         *       "label": "mock-example",
         *       "route": "mock-example"
         *     }
         */
        ApplicationEntry: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            code: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            requiredPermission?: components["schemas"]["PermissionCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            route: string;
        };
        /**
         * @description 接口模型 AreaSummary；具体业务范围以使用它的操作为准。
         * @example {
         *       "captureCount": 0,
         *       "deviceCount": 0,
         *       "siteCount": 0,
         *       "asOf": "2026-10-07T02:00:00Z",
         *       "unit": "COUNT",
         *       "onlineDeviceCount": 0,
         *       "onlineRate": 0,
         *       "regions": [
         *         {
         *           "regionCode": "mock-example",
         *           "deviceCount": 0,
         *           "onlineCount": 0,
         *           "onlineRate": 0
         *         }
         *       ]
         *     }
         */
        AreaSummary: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            onlineDeviceCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            onlineRate: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regions: components["schemas"]["AreaSummaryRegionsItem"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteCount: number;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            unit: "COUNT";
        };
        /**
         * @example {
         *       "regionCode": "mock-example",
         *       "deviceCount": 0,
         *       "onlineCount": 0,
         *       "onlineRate": 0
         *     }
         */
        AreaSummaryRegionsItem: {
            deviceCount: number;
            onlineCount: number;
            onlineRate: number;
            regionCode: string;
        };
        /**
         * @description 接口模型 AreaSummaryRequest；具体业务范围以使用它的操作为准。
         * @example {
         *       "spatialRange": {
         *         "selectionType": "CAPTURE",
         *         "geometry": {
         *           "type": "Polygon",
         *           "coordinates": [
         *             [
         *               [
         *                 116.38,
         *                 39.88
         *               ],
         *               [
         *                 116.42,
         *                 39.88
         *               ],
         *               [
         *                 116.42,
         *                 39.92
         *               ],
         *               [
         *                 116.38,
         *                 39.92
         *               ],
         *               [
         *                 116.38,
         *                 39.88
         *               ]
         *             ]
         *           ]
         *         },
         *         "coordinateSystem": "EPSG:4326"
         *       },
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       },
         *       "dataTypes": [
         *         "FENCE"
         *       ]
         *     }
         */
        AreaSummaryRequest: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dataTypes: components["schemas"]["DataType"][];
            spatialRange: components["schemas"]["ContextSpatialRange"];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 不可变审计记录；摘要按当前权限脱敏，不返回凭据或完整请求体。；未认证失败actorUserId=null；系统/认证事件没有UUID对象时resourceId=null或非敏感业务键，不能伪造UUID。
         * @example {
         *       "eventId": "00000000-0000-4000-8000-000000000001",
         *       "actorUserId": null,
         *       "actorLoginName": "mock-example",
         *       "action": "mock-example",
         *       "resourceType": "mock-example",
         *       "resourceId": null,
         *       "occurredAt": "2026-10-07T02:00:00Z",
         *       "result": "SUCCESS",
         *       "traceId": "mock-example",
         *       "actorDisplayName": "mock-example"
         *     }
         */
        AuditEvent: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            action: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            actorDisplayName: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            actorLoginName: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            actorUserId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            afterSummary?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            beforeSummary?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            businessTaskId?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            eventId: string;
            failureCode?: components["schemas"]["ErrorCode"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            occurredAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            requestId?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resourceId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resourceType: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            result: "SUCCESS" | "FAILURE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "eventId": "00000000-0000-4000-8000-000000000001",
         *           "actorUserId": null,
         *           "actorLoginName": "mock-example",
         *           "action": "mock-example",
         *           "resourceType": "mock-example",
         *           "resourceId": null,
         *           "occurredAt": "2026-10-07T02:00:00Z",
         *           "result": "SUCCESS",
         *           "traceId": "mock-example",
         *           "actorDisplayName": "mock-example"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        AuditEventPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["AuditEvent"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 接口模型 AuditExportInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "scope": "OWN_ALL",
         *       "format": "XLSX",
         *       "reason": "mock-example"
         *     }
         */
        AuditExportInput: components["schemas"]["AuditOwnAllExportInput"] | components["schemas"]["AuditFilteredExportInput"];
        /**
         * @example {
         *       "scope": "FILTERED",
         *       "search": {
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         }
         *       },
         *       "format": "XLSX",
         *       "reason": "mock-example"
         *     }
         */
        AuditFilteredExportInput: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /**
             * @description 判别字段固定为 FILTERED。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            scope: "FILTERED";
            search: components["schemas"]["AuditSearchRequest"];
        };
        /**
         * @description 导出当前账户全部保留且授权的日志，忽略页面筛选；按服务器保留期冻结查询快照。
         * @example {
         *       "scope": "OWN_ALL",
         *       "format": "XLSX",
         *       "reason": "mock-example"
         *     }
         */
        AuditOwnAllExportInput: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /**
             * @description 判别字段固定为 OWN_ALL。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            scope: "OWN_ALL";
        };
        /**
         * @description 普通用户只能查询自身；超级管理员范围待安全签认。
         * @example {
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        AuditSearchRequest: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            action?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            actorDisplayName?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            actorLoginName?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            actorUserId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cursor?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resourceType?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            result?: "SUCCESS" | "FAILURE";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            sortBy?: "occurredAt" | "eventId";
            sortOrder?: components["schemas"]["SortOrder"];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 接口模型 BackfillInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "source": "mock-example",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       },
         *       "reason": "mock-example"
         *     }
         */
        BackfillInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 按输入顺序逐项软删，每项独立事务；敏感USER/ROLE项须BatchResourceRef.approvalId绑定单项删除的资源/版本，逐项核验并消费；失败不影响成功项，不能绕过单条权限/引用/审批规则。
         * @example {
         *       "resourceType": "ANALYSIS_TASK",
         *       "items": [
         *         {
         *           "resourceId": "00000000-0000-4000-8000-000000000001",
         *           "version": 0
         *         }
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        BatchDeletionInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["BatchResourceRef"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            resourceType: "ANALYSIS_TASK" | "WATCH_RULE" | "PERSON_LIBRARY" | "SITE" | "DEVICE" | "USER" | "ROLE" | "REPORT" | "NOTIFICATION_TASK" | "DEVICE_COMMAND" | "DATASET";
        };
        /**
         * @example {
         *       "resourceId": "00000000-0000-4000-8000-000000000001",
         *       "status": "DELETED",
         *       "message": "mock-example"
         *     }
         */
        BatchDeletionItem: {
            errorCode?: components["schemas"]["ErrorCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resourceId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            status: "DELETED" | "REJECTED";
        };
        /**
         * @example {
         *       "items": [
         *         {
         *           "resourceId": "00000000-0000-4000-8000-000000000001",
         *           "status": "DELETED",
         *           "message": "mock-example"
         *         }
         *       ],
         *       "successCount": 1,
         *       "failureCount": 0
         *     }
         */
        BatchDeletionResult: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            failureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["BatchDeletionItem"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            successCount: number;
        };
        /**
         * @example {
         *       "resourceId": "00000000-0000-4000-8000-000000000001",
         *       "version": 0
         *     }
         */
        BatchResourceRef: {
            /**
             * Format: uuid
             * @description 敏感单项删除所需审批。USER/ROLE按对应deleteUser/deleteRole提案及该项version换成If-Match进行匹配；在该项事务消费，不以一次外层审批覆盖全批。
             */
            approvalId?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resourceId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /** @description 最多100条，版本来自详情。每项独立事务、按输入顺序；重校资源权限。撤销停止后续调度，不撤回已发送副作用。 */
        BatchRevocationInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["BatchResourceRef"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            resourceType: "WATCH_RULE" | "NOTIFICATION_TASK";
        };
        /** @description REVOKED表示WATCH_RULE已REVOKED/NOTIFICATION_TASK已CANCELLED，在途执行取消单独轮询其taskId。 */
        BatchRevocationItem: {
            errorCode?: components["schemas"]["ErrorCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resourceId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            status: "REVOKED" | "REJECTED";
        };
        /** @description 逐项结果；已撤销重复提交可返回REVOKED，引用/版本冲突返回REJECTED和errorCode。 */
        BatchRevocationResult: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            failureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["BatchRevocationItem"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            successCount: number;
        };
        /**
         * @description 接口模型 BooleanDeviceParameter；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "label": "mock-example",
         *       "valueType": "BOOLEAN",
         *       "booleanValue": true,
         *       "readOnly": true,
         *       "updatedAt": "2026-10-07T02:00:00Z"
         *     }
         */
        BooleanDeviceParameter: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            booleanValue: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            readOnly: boolean;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /**
             * @description 判别字段固定为 BOOLEAN。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "BOOLEAN";
        };
        /**
         * @description 接口模型 BooleanDeviceParameterChange；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "BOOLEAN",
         *       "booleanValue": true
         *     }
         */
        BooleanDeviceParameterChange: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            booleanValue: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /**
             * @description 判别字段固定为 BOOLEAN。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "BOOLEAN";
        };
        /**
         * @description 接口模型 BooleanModelParameterValue；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "BOOLEAN",
         *       "booleanValue": true
         *     }
         */
        BooleanModelParameterValue: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            booleanValue: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /**
             * @description 判别字段固定为 BOOLEAN。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "BOOLEAN";
        };
        /** @description 接口模型 CancelNotificationTaskResponse；具体业务范围以使用它的操作为准。 */
        CancelNotificationTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["NotificationTaskDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CancelTaskResponse；具体业务范围以使用它的操作为准。 */
        CancelTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["Task"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description TARGET按代理目标相等；CAPTURE按captureId相等，未解析目标时省略target，仍允许保留原始记录。
         * @example {
         *       "addedAt": "2026-10-07T02:00:00Z",
         *       "itemId": "00000000-0000-4000-8000-000000000001",
         *       "elementType": "CAPTURE",
         *       "capture": {
         *         "captureId": "00000000-0000-4000-8000-000000000001",
         *         "dataType": "FENCE",
         *         "targetId": null,
         *         "deviceId": null,
         *         "siteId": null,
         *         "captureTime": "2026-10-07T02:00:00Z",
         *         "ingestedAt": "2026-10-07T02:00:00Z",
         *         "longitude": null,
         *         "latitude": null,
         *         "source": "mock-example",
         *         "status": "VALID",
         *         "displayTarget": "mock-example",
         *         "isMasked": true,
         *         "fence": {
         *           "imsiMasked": null,
         *           "imeiMasked": null
         *         },
         *         "targetType": "IMSI"
         *       },
         *       "displayTarget": "mock-example"
         *     }
         */
        CaptureDatasetItem: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            addedAt: string;
            capture: components["schemas"]["CaptureRecord"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /**
             * @description 判别字段固定为 CAPTURE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            elementType: "CAPTURE";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            itemId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            sourceCaptureId?: string;
            target?: components["schemas"]["ContextTarget"];
        };
        /**
         * @description CAPTURE只接受已授权已有采集ID，未解析身份不阻止保存；布控或伴随需另行取得targetId。
         * @example {
         *       "elementType": "CAPTURE",
         *       "captureId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        CaptureDatasetItemInput: {
            /** Format: uuid */
            captureId: string;
            /**
             * @description 判别字段固定为 CAPTURE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            elementType: "CAPTURE";
        };
        /**
         * @description 接口模型 CaptureDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "record": {
         *         "captureId": "00000000-0000-4000-8000-000000000001",
         *         "dataType": "FENCE",
         *         "targetId": null,
         *         "deviceId": null,
         *         "siteId": null,
         *         "captureTime": "2026-10-07T02:00:00Z",
         *         "ingestedAt": "2026-10-07T02:00:00Z",
         *         "longitude": null,
         *         "latitude": null,
         *         "source": "mock-example",
         *         "status": "VALID",
         *         "displayTarget": "mock-example",
         *         "isMasked": true,
         *         "fence": {
         *           "imsiMasked": null,
         *           "imeiMasked": null
         *         },
         *         "targetType": "IMSI"
         *       },
         *       "relatedProfileIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "canDownloadImage": true,
         *       "availableActions": [
         *         "dashboard:read"
         *       ]
         *     }
         */
        CaptureDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            availableActions: components["schemas"]["PermissionCode"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            canDownloadImage: boolean;
            record: components["schemas"]["CaptureRecord"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            relatedProfileIds: string[];
        };
        /**
         * @description 导出列白名单。类型专有列仅对应数据类型；多源不适用单元格留空，未经解密不得导出明文。
         * @example captureId
         * @enum {string}
         */
        CaptureExportColumn: "captureId" | "dataType" | "captureTime" | "ingestedAt" | "deviceId" | "siteId" | "displayTarget" | "source" | "status" | "longitude" | "latitude" | "imsiMasked" | "imeiMasked" | "macMasked" | "plateMasked" | "faceImageAssetId";
        /**
         * @example {
         *       "metricCode": "CAPTURED",
         *       "metric": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "series": [
         *           {
         *             "code": "mock-example",
         *             "label": "mock-example",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ]
         *       }
         *     }
         */
        CaptureMetricSeries: {
            metric: components["schemas"]["SeriesMetric"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            metricCode: "CAPTURED" | "INGESTED" | "CUMULATIVE_INGESTED" | "DAILY_AVERAGE_CAPTURED";
        };
        /**
         * @description 接口模型 CaptureRecord；具体业务范围以使用它的操作为准。
         * @example {
         *       "captureId": "00000000-0000-4000-8000-000000000001",
         *       "dataType": "FENCE",
         *       "targetId": null,
         *       "deviceId": null,
         *       "siteId": null,
         *       "captureTime": "2026-10-07T02:00:00Z",
         *       "ingestedAt": "2026-10-07T02:00:00Z",
         *       "longitude": null,
         *       "latitude": null,
         *       "source": "mock-example",
         *       "status": "VALID",
         *       "displayTarget": "mock-example",
         *       "isMasked": true,
         *       "fence": {
         *         "imsiMasked": null,
         *         "imeiMasked": null
         *       },
         *       "targetType": "IMSI"
         *     }
         */
        CaptureRecord: components["schemas"]["FenceCaptureRecord"] | components["schemas"]["MacCaptureRecord"] | components["schemas"]["PlateCaptureRecord"] | components["schemas"]["FaceCaptureRecord"];
        /**
         * @description 摘要口径整个授权范围；operatorSeries分时输出DAY按小时、MONTH按日、YEAR按月。FENCE_DEVICE_CAPTURE按deviceId分组；设备明细按cursor限量。最终公式待P-006/P-020。
         * @example {
         *       "reportType": "FENCE_OPERATOR_STATISTICS",
         *       "regionCode": "mock-example",
         *       "metrics": [
         *         {
         *           "label": "mock-example",
         *           "value": null,
         *           "unit": "mock-example",
         *           "asOf": "2026-10-07T02:00:00Z",
         *           "status": "READY"
         *         }
         *       ],
         *       "series": [
         *         {
         *           "at": "2026-10-07T02:00:00Z",
         *           "value": null
         *         }
         *       ],
         *       "asOf": "2026-10-07T02:00:00Z",
         *       "definition": {
         *         "reportType": "DEVICE_REALTIME_ONLINE_RATE",
         *         "granularity": "DAY",
         *         "periodStart": "2026-10-07",
         *         "regionCode": "mock-example"
         *       },
         *       "deviceCount": 0,
         *       "captureCount": 0,
         *       "operatorSeries": [
         *         {
         *           "operatorCode": "mock-example",
         *           "operatorLabel": "mock-example",
         *           "points": [
         *             {
         *               "at": "2026-10-07T02:00:00Z",
         *               "value": null
         *             }
         *           ]
         *         }
         *       ],
         *       "devices": [
         *         {
         *           "deviceId": "00000000-0000-4000-8000-000000000001",
         *           "serialNumber": "mock-example",
         *           "name": "mock-example",
         *           "siteId": "00000000-0000-4000-8000-000000000001",
         *           "category": "mock-example",
         *           "status": "ONLINE",
         *           "lastSeenAt": null,
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "version": 1,
         *           "siteName": "mock-example",
         *           "regionCode": "mock-example",
         *           "groupIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "resetCount": 0
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1,
         *       "previewKind": "CAPTURE"
         *     }
         */
        CaptureReportPreview: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureCount: number;
            definition: components["schemas"]["ReportDefinition"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            devices: components["schemas"]["DeviceSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            metrics: components["schemas"]["ReportMetricRow"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            operatorSeries: components["schemas"]["CaptureReportSeries"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /**
             * @description 固定预览类型，与reportType细分报表类型分开。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            previewKind: "CAPTURE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            reportType: "FENCE_OPERATOR_STATISTICS" | "FENCE_AREA_CAPTURE" | "FENCE_DEVICE_CAPTURE" | "MAC_CAPTURE" | "PLATE_CAPTURE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            series: components["schemas"]["MetricPoint"][];
        };
        /**
         * @example {
         *       "operatorCode": "mock-example",
         *       "operatorLabel": "mock-example",
         *       "points": [
         *         {
         *           "at": "2026-10-07T02:00:00Z",
         *           "value": null
         *         }
         *       ]
         *     }
         */
        CaptureReportSeries: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceName?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            operatorCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            operatorLabel: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            points: components["schemas"]["MetricPoint"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode?: string;
        };
        /**
         * @description 接口模型 CaptureSearchFilter；具体业务范围以使用它的操作为准。
         * @example {
         *       "dataType": "FENCE",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        CaptureSearchFilter: components["schemas"]["FenceCaptureSearchFilter"] | components["schemas"]["MacCaptureSearchFilter"] | components["schemas"]["PlateCaptureSearchFilter"] | components["schemas"]["FaceCaptureSearchFilter"];
        /**
         * @description 普通查询最多31天，分页20默认/100最大，不透明游标签名15分钟并绑定条件/水位/权限版本。SQL硬超时5秒、锁等待1秒；取消向DB传播，不能以部分结果冒充完整成功。查询验收目标仍<=2秒。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "dataType": "FENCE",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        CaptureSearchRequest: components["schemas"]["FenceCaptureSearchRequest"] | components["schemas"]["MacCaptureSearchRequest"] | components["schemas"]["PlateCaptureSearchRequest"] | components["schemas"]["FaceCaptureSearchRequest"];
        /** @description nextCursor 是不透明游标；不默认返回全量 total。 */
        CaptureSearchResponse: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["CaptureRecord"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 接口模型 CaptureStatistics；具体业务范围以使用它的操作为准。
         * @example {
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "captureCount": 0,
         *       "siteCount": 0,
         *       "regionCount": 0,
         *       "asOf": "2026-10-07T02:00:00Z"
         *     }
         */
        CaptureStatistics: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureCount: number;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            firstSeenAt?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            mostFrequentSiteId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteCount: number;
            target: components["schemas"]["ContextTarget"];
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "captureCount": 0,
         *           "siteCount": 0,
         *           "regionCount": 0,
         *           "asOf": "2026-10-07T02:00:00Z"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        CaptureStatisticsData: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["CaptureStatistics"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description targets 省略时按游标分批统计当前 Context 的目标；传入时仅统计列出的目标。
         * @example {
         *       "contextId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        CaptureStatisticsRequest: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cursor?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 20
             */
            pageSize?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targets?: components["schemas"]["ContextTarget"][];
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example VALID
         * @enum {string}
         */
        CaptureStatus: "VALID" | "QUARANTINED" | "RETRACTED";
        /**
         * @description captured 是源端采集总量，ingested 是有效入库量，口径不能合并；UNAVAILABLE 时为 null。
         * @example {
         *       "meta": {
         *         "asOf": null,
         *         "status": "READY",
         *         "unit": "mock-example",
         *         "refreshPolicy": "POLL"
         *       },
         *       "todayCaptured": null,
         *       "allCaptured": null,
         *       "todayIngested": null,
         *       "allIngested": null
         *     }
         */
        CaptureTotals: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            allCaptured: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            allIngested: number | null;
            meta: components["schemas"]["MetricMeta"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            todayCaptured: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            todayIngested: number | null;
        };
        /**
         * @description 前端字段与部署字典的绑定。dictionaryCode用于GET /dictionaries/code；字典未启用时没有绑定，不能硬编码Mock分类。
         * @example {
         *       "fieldKey": "DeviceInput.category",
         *       "dictionaryCode": "DEVICE_CATEGORY",
         *       "label": "设备分类"
         *     }
         */
        CatalogBinding: {
            dictionaryCode: string;
            /** @enum {string} */
            fieldKey: "DeviceInput.category" | "PersonLibraryInput.category" | "PersonProfileCorrection.ethnicity" | "PersonProfileCorrection.gender";
            label: string;
        };
        /**
         * @description 接口模型 CategoryCount；具体业务范围以使用它的操作为准。
         * @example {
         *       "code": "mock-example",
         *       "label": "mock-example",
         *       "count": 0
         *     }
         */
        CategoryCount: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            code: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            count: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
        };
        /**
         * @description 接口模型 CategoryMetric；具体业务范围以使用它的操作为准。
         * @example {
         *       "meta": {
         *         "asOf": null,
         *         "status": "READY",
         *         "unit": "mock-example",
         *         "refreshPolicy": "POLL"
         *       },
         *       "items": [
         *         {
         *           "code": "mock-example",
         *           "label": "mock-example",
         *           "count": 0
         *         }
         *       ]
         *     }
         */
        CategoryMetric: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["CategoryCount"][];
            meta: components["schemas"]["MetricMeta"];
        };
        ChangeMyPasswordResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ChangeUserPasswordApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "changeUserPassword";
            pathParameters: {
                /** Format: uuid */
                userId: string;
            };
            requestBody: components["schemas"]["PasswordChangeInput"];
        };
        /** @description 接口模型 ChangeUserPasswordResponse；具体业务范围以使用它的操作为准。 */
        ChangeUserPasswordResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 前端可读取的非机密有效配置；暂定值按PD-2026-10-19.v1返回，后续版本显式变更；features只表示实际实现与资源可用性，不以甲方未定稿统一禁用。
         * @example {
         *       "version": "mock-example",
         *       "mapCoordinateSystem": "EPSG:4326",
         *       "taskPollIntervalSeconds": 2,
         *       "maxUploadBytes": 104857600,
         *       "featureAvailability": [
         *         {
         *           "code": "FACE_SEARCH",
         *           "enabled": false
         *         },
         *         {
         *           "code": "DECRYPTION",
         *           "enabled": false
         *         },
         *         {
         *           "code": "COMPANION_ANALYSIS",
         *           "enabled": false
         *         },
         *         {
         *           "code": "COLLISION_ANALYSIS",
         *           "enabled": false
         *         },
         *         {
         *           "code": "REGION_STATISTICS",
         *           "enabled": false
         *         },
         *         {
         *           "code": "PROFILE_ASSOCIATION",
         *           "enabled": false
         *         },
         *         {
         *           "code": "RESIDENCE_MODEL",
         *           "enabled": false
         *         },
         *         {
         *           "code": "WATCH_ACTIVATION",
         *           "enabled": false
         *         },
         *         {
         *           "code": "NOTIFICATION_SEND",
         *           "enabled": false
         *         },
         *         {
         *           "code": "DEVICE_COMMAND",
         *           "enabled": false
         *         },
         *         {
         *           "code": "REPORT_GENERATION",
         *           "enabled": false
         *         },
         *         {
         *           "code": "RAW_INGESTION",
         *           "enabled": false
         *         }
         *       ],
         *       "uploadPolicies": [
         *         {
         *           "category": "FACE_SEARCH",
         *           "mimeTypes": [
         *             "image/jpeg",
         *             "image/png"
         *           ],
         *           "extensions": [
         *             "jpg",
         *             "jpeg",
         *             "png"
         *           ],
         *           "maxBytes": 5242880,
         *           "enabled": false
         *         }
         *       ],
         *       "institutionTimeZone": "Asia/Shanghai",
         *       "contextTtlSeconds": 1800,
         *       "maxOnlineQuerySeconds": 5,
         *       "maxOnlineWindowDays": 31,
         *       "features": [
         *         {
         *           "featureCode": "FACE_SEARCH",
         *           "enabled": false,
         *           "pendingReason": "P-007：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "DECRYPTION",
         *           "enabled": false,
         *           "pendingReason": "P-008：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "COMPANION_ANALYSIS",
         *           "enabled": false,
         *           "pendingReason": "P-011：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "COLLISION_ANALYSIS",
         *           "enabled": false,
         *           "pendingReason": "P-011：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "REGION_STATISTICS",
         *           "enabled": false,
         *           "pendingReason": "P-011：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "PROFILE_ASSOCIATION",
         *           "enabled": false,
         *           "pendingReason": "P-016：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "RESIDENCE_MODEL",
         *           "enabled": false,
         *           "pendingReason": "P-017：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "WATCH_ACTIVATION",
         *           "enabled": false,
         *           "pendingReason": "P-014：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "NOTIFICATION_SEND",
         *           "enabled": false,
         *           "pendingReason": "P-015：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "DEVICE_COMMAND",
         *           "enabled": false,
         *           "pendingReason": "P-018/P-019：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "REPORT_GENERATION",
         *           "enabled": false,
         *           "pendingReason": "P-006/P-020：合成示例部署尚未完成实现/真实资源绑定"
         *         },
         *         {
         *           "featureCode": "RAW_INGESTION",
         *           "enabled": false,
         *           "pendingReason": "P-004：合成示例部署尚未完成实现/真实资源绑定"
         *         }
         *       ],
         *       "catalogBindings": [
         *         {
         *           "fieldKey": "DeviceInput.category",
         *           "dictionaryCode": "DEVICE_CATEGORY",
         *           "label": "设备分类"
         *         }
         *       ]
         *     }
         */
        ClientConfig: {
            /** @description 字段字典绑定列表，不提供内部配置或供应商私密字段。 */
            catalogBindings: components["schemas"]["CatalogBinding"][];
            /**
             * @description PD-2026-10-19.v1暂定值；部署实际有效值必须与所记录policyVersion一致。
             * @default 1800
             */
            contextTtlSeconds: number;
            /**
             * @deprecated
             * @description 旧兼容别名，仅由features投影为{code:featureCode,enabled}，不得单独计算或覆写。新页面只读features。
             */
            featureAvailability: components["schemas"]["ClientConfigFeatureAvailabilityItem"][];
            features: components["schemas"]["FeatureAvailability"][];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            institutionTimeZone: "Asia/Shanghai";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            mapCoordinateSystem: "EPSG:4326";
            /**
             * @description PD-2026-10-19.v1暂定值；部署实际有效值必须与所记录policyVersion一致。
             * @default 5
             */
            maxOnlineQuerySeconds: number;
            /**
             * @description PD-2026-10-19.v1暂定值；部署实际有效值必须与所记录policyVersion一致。
             * @default 31
             */
            maxOnlineWindowDays: number;
            /**
             * @deprecated
             * @description 旧全局上传安全上限，实际单类上限=min(maxUploadBytes,uploadPolicies.category.maxBytes)。新页面展示分类策略，不能据旧值放宽。
             */
            maxUploadBytes: number;
            /**
             * @description PD-2026-10-19.v1暂定值；部署实际有效值必须与所记录policyVersion一致。
             * @default 2
             */
            taskPollIntervalSeconds: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            uploadPolicies: components["schemas"]["UploadPolicy"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: string;
        };
        /**
         * @example {
         *       "code": "mock-example",
         *       "enabled": true
         *     }
         */
        ClientConfigFeatureAvailabilityItem: {
            code: string;
            enabled: boolean;
        };
        /**
         * @description ruleCode=COLLISION_V1，默认120秒/100米配对，最多7天；保存逐条采集证据，matchRate是匹配主目标桶数/主目标有效桶数，不自动跨标识认人。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "targets": [
         *         {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         },
         *         {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         }
         *       ],
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       },
         *       "ruleCode": "mock-example"
         *     }
         */
        CollisionAnalysisParameters: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            minResultScore?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCodes?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            ruleCode: string;
            spatialRange?: components["schemas"]["ContextSpatialRange"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targets: components["schemas"]["ContextTarget"][];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description kind 必须与现有任务一致，改变分析类型须创建新任务。
         * @example {
         *       "kind": "COLLISION",
         *       "parameters": {
         *         "targets": [
         *           {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           }
         *         ],
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "ruleCode": "mock-example"
         *       },
         *       "reason": "mock-example"
         *     }
         */
        CollisionAnalysisRevisionInput: {
            /**
             * @description 判别字段固定为 COLLISION。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "COLLISION";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name?: string;
            parameters: components["schemas"]["CollisionAnalysisParameters"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 任务未完成时 resultCount=null；修订产生新任务，不覆盖历史结果。
         * @example {
         *       "task": {
         *         "taskId": "00000000-0000-4000-8000-000000000001",
         *         "taskType": "INGESTION",
         *         "status": "PENDING",
         *         "progress": 0,
         *         "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *         "createdAt": "2026-10-07T02:00:00Z",
         *         "retryCount": 0,
         *         "parameterSnapshot": {
         *           "schemaVersion": "mock-example",
         *           "submittedAt": "2026-10-07T02:00:00Z",
         *           "parameterHash": "0000000000000000000000000000000000000000000000000000000000000000"
         *         },
         *         "traceId": "mock-example",
         *         "name": "mock-example"
         *       },
         *       "name": "mock-example",
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "resultCount": null,
         *       "version": 0,
         *       "kind": "COLLISION",
         *       "parameters": {
         *         "targets": [
         *           {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           }
         *         ],
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "ruleCode": "mock-example"
         *       },
         *       "analysisTaskId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        CollisionAnalysisTaskDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            algorithmVersion?: string | null;
            /** Format: uuid */
            analysisTaskId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * @description 判别字段固定为 COLLISION。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "COLLISION";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            parameters: components["schemas"]["CollisionAnalysisParameters"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resultCount: number | null;
            task: components["schemas"]["Task"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 CollisionAnalysisTaskInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "kind": "COLLISION",
         *       "name": "mock-example",
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "parameters": {
         *         "targets": [
         *           {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           }
         *         ],
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "ruleCode": "mock-example"
         *       }
         *     }
         */
        CollisionAnalysisTaskInput: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * @description 判别字段固定为 COLLISION。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "COLLISION";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            parameters: components["schemas"]["CollisionAnalysisParameters"];
        };
        /**
         * @description 时间、地点与证据按单次事件绑定。
         * @example {
         *       "occurredAt": "2026-10-07T02:00:00Z",
         *       "location": {
         *         "type": "Point",
         *         "coordinates": [
         *           116.4,
         *           39.9
         *         ]
         *       },
         *       "evidenceCaptureIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ]
         *     }
         */
        CollisionEvent: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceCaptureIds: string[];
            location: components["schemas"]["GeoPoint"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            occurredAt: string;
        };
        /**
         * @description 返回实际碰撞采集与时间/场所证据，matchRate=匹配主目标桶/主目标有效桶；缺证据不返回推断结论，不把关联分解释为同一人或风险概率。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "resultId": "00000000-0000-4000-8000-000000000001",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "evidenceCaptureIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "algorithmVersion": "mock-example",
         *       "kind": "COLLISION",
         *       "collisionCount": 0,
         *       "events": [
         *         {
         *           "occurredAt": "2026-10-07T02:00:00Z",
         *           "location": {
         *             "type": "Point",
         *             "coordinates": [
         *               116.4,
         *               39.9
         *             ]
         *           },
         *           "evidenceCaptureIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ]
         *         }
         *       ],
         *       "displayTarget": "mock-example",
         *       "dataType": "FENCE",
         *       "captureCount": 0,
         *       "siteCount": 0,
         *       "regionCount": 0,
         *       "visitedRegionCodes": [
         *         "mock-example"
         *       ]
         *     }
         */
        CollisionResultItem: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            algorithmVersion: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            collisionCount: number;
            dataType: components["schemas"]["DataType"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            events: components["schemas"]["CollisionEvent"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceCaptureIds: string[];
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            firstSeenAt?: string;
            /**
             * @description 判别字段固定为 COLLISION。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "COLLISION";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            matchRate?: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCount: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resultId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteCount: number;
            target: components["schemas"]["ContextTarget"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            visitedRegionCodes: string[];
        };
        /**
         * @description targetId/deviceId/siteId 可为 null；targetType 在 targetId 可解析时出现，无法映射的原始标识仅按授权和脱敏规则生成 displayTarget。此时不得发起依赖该 ID 的动作。围栏记录同时有 IMSI/IMEI 时 targetId 只指当前规范目标，另一标识不自动视为同一人。；targetType必返，表示载荷主目标类型；FENCE须IMSI或IMEI，其它分别MAC/PLATE/FACE。targetId=null表示尚未解析代理ID，不能直接研判，主类型仍返回。
         * @example {
         *       "captureId": "00000000-0000-4000-8000-000000000001",
         *       "dataType": "FENCE",
         *       "targetId": null,
         *       "deviceId": null,
         *       "siteId": null,
         *       "captureTime": "2026-10-07T02:00:00Z",
         *       "ingestedAt": "2026-10-07T02:00:00Z",
         *       "longitude": null,
         *       "latitude": null,
         *       "source": "mock-example",
         *       "status": "VALID",
         *       "displayTarget": "mock-example",
         *       "isMasked": true,
         *       "targetType": "IMSI"
         *     }
         */
        CommonCaptureRecord: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            captureId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            captureTime: string;
            dataType: components["schemas"]["DataType"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            ingestedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            isMasked: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            latitude: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            longitude: number | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            status: components["schemas"]["CaptureStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            targetId: string | null;
            targetType: components["schemas"]["TargetType"];
        };
        /**
         * @description COMPANION_V1默认120秒/100米，最多3600秒/5000米、7天；至少3个共现桶。geography米制距离、不重复配对及稳定排序；associationScore为桶Jaccard，不是身份/嫌疑概率。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       },
         *       "timeWindowSeconds": 1,
         *       "spatialWindowMeters": 0
         *     }
         */
        CompanionAnalysisParameters: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 0
             */
            minResultScore?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCodes?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 100
             */
            spatialWindowMeters: number;
            target: components["schemas"]["ContextTarget"];
            timeRange: components["schemas"]["TimeRange"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 120
             */
            timeWindowSeconds: number;
        };
        /**
         * @description kind 必须与现有任务一致，改变分析类型须创建新任务。
         * @example {
         *       "kind": "COMPANION",
         *       "parameters": {
         *         "target": {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         },
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "timeWindowSeconds": 1,
         *         "spatialWindowMeters": 0
         *       },
         *       "reason": "mock-example"
         *     }
         */
        CompanionAnalysisRevisionInput: {
            /**
             * @description 判别字段固定为 COMPANION。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "COMPANION";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name?: string;
            parameters: components["schemas"]["CompanionAnalysisParameters"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 任务未完成时 resultCount=null；修订产生新任务，不覆盖历史结果。
         * @example {
         *       "task": {
         *         "taskId": "00000000-0000-4000-8000-000000000001",
         *         "taskType": "INGESTION",
         *         "status": "PENDING",
         *         "progress": 0,
         *         "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *         "createdAt": "2026-10-07T02:00:00Z",
         *         "retryCount": 0,
         *         "parameterSnapshot": {
         *           "schemaVersion": "mock-example",
         *           "submittedAt": "2026-10-07T02:00:00Z",
         *           "parameterHash": "0000000000000000000000000000000000000000000000000000000000000000"
         *         },
         *         "traceId": "mock-example",
         *         "name": "mock-example"
         *       },
         *       "name": "mock-example",
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "resultCount": null,
         *       "version": 0,
         *       "kind": "COMPANION",
         *       "parameters": {
         *         "target": {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         },
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "timeWindowSeconds": 1,
         *         "spatialWindowMeters": 0
         *       },
         *       "analysisTaskId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        CompanionAnalysisTaskDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            algorithmVersion?: string | null;
            /** Format: uuid */
            analysisTaskId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * @description 判别字段固定为 COMPANION。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "COMPANION";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            parameters: components["schemas"]["CompanionAnalysisParameters"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resultCount: number | null;
            task: components["schemas"]["Task"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 CompanionAnalysisTaskInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "kind": "COMPANION",
         *       "name": "mock-example",
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "parameters": {
         *         "target": {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         },
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "timeWindowSeconds": 1,
         *         "spatialWindowMeters": 0
         *       }
         *     }
         */
        CompanionAnalysisTaskInput: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * @description 判别字段固定为 COMPANION。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "COMPANION";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            parameters: components["schemas"]["CompanionAnalysisParameters"];
        };
        /**
         * @description COMPANION_V1：commonOccurrenceCount=共同桶、sharedSiteCount=配对双方相同siteId的去重数、trajectoryOverlapCount=有效一对一配对数；associationScore=共同桶/(A桶+B桶-共同桶)，不表示身份或风险概率。证据与详细排序/时间桶规则见METRIC_ALGORITHM_RULES.md，待甲方确认。
         * @example {
         *       "resultId": "00000000-0000-4000-8000-000000000001",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "evidenceCaptureIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "algorithmVersion": "mock-example",
         *       "kind": "COMPANION",
         *       "commonOccurrenceCount": 0,
         *       "sharedSiteCount": 0,
         *       "trajectoryOverlapCount": 0,
         *       "displayTarget": "mock-example",
         *       "dataType": "FENCE",
         *       "captureCount": 0,
         *       "siteCount": 0,
         *       "regionCount": 0,
         *       "visitedRegionCodes": [
         *         "mock-example"
         *       ]
         *     }
         */
        CompanionResultItem: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            algorithmVersion: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            associationScore?: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            commonOccurrenceCount: number;
            dataType: components["schemas"]["DataType"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceCaptureIds: string[];
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            firstSeenAt?: string;
            /**
             * @description 判别字段固定为 COMPANION。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "COMPANION";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCount: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resultId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            sharedSiteCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteCount: number;
            target: components["schemas"]["ContextTarget"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            trajectoryOverlapCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            visitedRegionCodes: string[];
        };
        /**
         * @description 保存来源版本和全量结果快照；省略resultIds代表全部结果，不限当前页。权限仍逐次复核。
         * @example {
         *       "filterType": "ANALYSIS_RESULTS",
         *       "filterVersion": 1,
         *       "analysisTaskId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        ContextAnalysisResultFilter: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            analysisTaskId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dataTypes?: components["schemas"]["DataType"][];
            /**
             * @description 判别字段固定为 ANALYSIS_RESULTS。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            filterType: "ANALYSIS_RESULTS";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {integer}
             */
            filterVersion: 1;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resultIds?: string[];
        };
        /**
         * @description 完整保留车牌/MAC/围栏/人像检索条件；不得把仅当前页结果当作完整查询范围。
         * @example {
         *       "filterType": "CAPTURE_SEARCH",
         *       "filterVersion": 1,
         *       "captureFilter": {
         *         "dataType": "FENCE",
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         }
         *       }
         *     }
         */
        ContextCaptureFilter: {
            captureFilter: components["schemas"]["CaptureSearchFilter"];
            /**
             * @description 判别字段固定为 CAPTURE_SEARCH。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            filterType: "CAPTURE_SEARCH";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            filterVersion: number;
        };
        /**
         * @description 保存来源版本和全量结果快照；省略resultIds代表全部结果，不限当前页。权限仍逐次复核。
         * @example {
         *       "filterType": "DATASET",
         *       "filterVersion": 1,
         *       "datasetId": "00000000-0000-4000-8000-000000000001",
         *       "datasetVersion": 0,
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        ContextDatasetFilter: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            datasetId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            datasetVersion: number;
            /**
             * @description 判别字段固定为 DATASET。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            filterType: "DATASET";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {integer}
             */
            filterVersion: 1;
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 接口模型 ContextFilter；具体业务范围以使用它的操作为准。
         * @example {
         *       "filterType": "GENERAL",
         *       "filterVersion": 1,
         *       "dataTypes": [
         *         "FENCE"
         *       ],
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        ContextFilter: components["schemas"]["ContextGeneralFilter"] | components["schemas"]["ContextCaptureFilter"] | components["schemas"]["ContextDatasetFilter"] | components["schemas"]["ContextAnalysisResultFilter"] | components["schemas"]["ContextProfileFilter"];
        /**
         * @description 地图等普通筛选快照；显式选择的少量采集 ID 可附加，权限仍逐次重校。
         * @example {
         *       "filterType": "GENERAL",
         *       "filterVersion": 1,
         *       "dataTypes": [
         *         "FENCE"
         *       ],
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        ContextGeneralFilter: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dataTypes: components["schemas"]["DataType"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds?: string[];
            /**
             * @description 判别字段固定为 GENERAL。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            filterType: "GENERAL";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            filterVersion: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCodes?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            sourceCaptureIds?: string[];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 保存来源版本和全量结果快照；省略resultIds代表全部结果，不限当前页。权限仍逐次复核。
         * @example {
         *       "filterType": "PROFILE",
         *       "filterVersion": 1,
         *       "profileId": "00000000-0000-4000-8000-000000000001",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        ContextProfileFilter: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dataTypes?: components["schemas"]["DataType"][];
            /**
             * @description 判别字段固定为 PROFILE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            filterType: "PROFILE";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {integer}
             */
            filterVersion: 1;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            profileId: string;
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 接口模型 ContextSpatialRange；具体业务范围以使用它的操作为准。
         * @example {
         *       "selectionType": "CAPTURE",
         *       "geometry": {
         *         "type": "Polygon",
         *         "coordinates": [
         *           [
         *             [
         *               116.38,
         *               39.88
         *             ],
         *             [
         *               116.42,
         *               39.88
         *             ],
         *             [
         *               116.42,
         *               39.92
         *             ],
         *             [
         *               116.38,
         *               39.92
         *             ],
         *             [
         *               116.38,
         *               39.88
         *             ]
         *           ]
         *         ]
         *       },
         *       "coordinateSystem": "EPSG:4326"
         *     }
         */
        ContextSpatialRange: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            coordinateSystem: "EPSG:4326";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            geometry: components["schemas"]["GeoPolygon"] | components["schemas"]["GeoMultiPolygon"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            selectionType: "CAPTURE" | "DEVICE" | "AREA";
        };
        /**
         * @description 接口模型 ContextTarget；具体业务范围以使用它的操作为准。
         * @example {
         *       "targetId": "00000000-0000-4000-8000-000000000001",
         *       "targetType": "IMSI"
         *     }
         */
        ContextTarget: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            targetId: string;
            targetType: components["schemas"]["TargetType"];
        };
        /** @description 接口模型 CreateAnalysisTaskResponse；具体业务范围以使用它的操作为准。 */
        CreateAnalysisTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AnalysisTaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateContextResponse；具体业务范围以使用它的操作为准。 */
        CreateContextResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AnalysisContext"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateDatasetResponse；具体业务范围以使用它的操作为准。 */
        CreateDatasetResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DatasetDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        CreateDeviceCommandApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "createDeviceCommand";
            requestBody: components["schemas"]["DeviceCommandInput"];
        };
        /** @description 接口模型 CreateDeviceCommandResponse；具体业务范围以使用它的操作为准。 */
        CreateDeviceCommandResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateDeviceGroupResponse；具体业务范围以使用它的操作为准。 */
        CreateDeviceGroupResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceGroupDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateDeviceResponse；具体业务范围以使用它的操作为准。 */
        CreateDeviceResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        CreateExportApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "createExport";
            requestBody: components["schemas"]["ExportRequest"];
        };
        /** @description 接口模型 CreateExportResponse；具体业务范围以使用它的操作为准。 */
        CreateExportResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateFaceSearchTaskResponse；具体业务范围以使用它的操作为准。 */
        CreateFaceSearchTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        CreateIngestionBackfillApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "createIngestionBackfill";
            requestBody: components["schemas"]["BackfillInput"];
        };
        /** @description 接口模型 CreateIngestionBackfillResponse；具体业务范围以使用它的操作为准。 */
        CreateIngestionBackfillResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateIngestionBatchResponse；具体业务范围以使用它的操作为准。 */
        CreateIngestionBatchResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["IngestionBatchCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        CreateLibraryPersonResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["PersonLibraryPersonCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateModelParameterVersionResponse；具体业务范围以使用它的操作为准。 */
        CreateModelParameterVersionResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ModelParameterVersion"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateNotificationTaskResponse；具体业务范围以使用它的操作为准。 */
        CreateNotificationTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["NotificationTaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreatePersonLibraryResponse；具体业务范围以使用它的操作为准。 */
        CreatePersonLibraryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["PersonLibraryDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateProfileCorrectionResponse；具体业务范围以使用它的操作为准。 */
        CreateProfileCorrectionResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ProfileRevision"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateReportResponse；具体业务范围以使用它的操作为准。 */
        CreateReportResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ReportCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        CreateRoleApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "createRole";
            requestBody: components["schemas"]["RoleInput"];
        };
        /** @description 接口模型 CreateRoleResponse；具体业务范围以使用它的操作为准。 */
        CreateRoleResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["RoleDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateSetTaskResponse；具体业务范围以使用它的操作为准。 */
        CreateSetTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["SetTaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateSiteResponse；具体业务范围以使用它的操作为准。 */
        CreateSiteResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["SiteDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateTrendModelResponse；具体业务范围以使用它的操作为准。 */
        CreateTrendModelResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TrendModel"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        CreateUserApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "createUser";
            requestBody: components["schemas"]["UserCreateInput"];
        };
        /** @description 接口模型 CreateUserResponse；具体业务范围以使用它的操作为准。 */
        CreateUserResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["UserDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 CreateWatchRuleResponse；具体业务范围以使用它的操作为准。 */
        CreateWatchRuleResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["WatchRuleDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 CsrfTokenData；具体业务范围以使用它的操作为准。
         * @example {
         *       "token": "mock-example",
         *       "headerName": "X-CSRF-Token"
         *     }
         */
        CsrfTokenData: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            headerName: "X-CSRF-Token";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            token: string;
        };
        /**
         * @description 接口模型 CurrentUserData；具体业务范围以使用它的操作为准。
         * @example {
         *       "user": {
         *         "userId": "00000000-0000-4000-8000-000000000001",
         *         "loginName": "mock-example",
         *         "displayName": "mock-example",
         *         "status": "ACTIVE"
         *       },
         *       "roleIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "permissionCodes": [
         *         "dashboard:read"
         *       ],
         *       "menus": [
         *         {
         *           "menuId": "00000000-0000-4000-8000-000000000001",
         *           "code": "mock-example",
         *           "label": "mock-example",
         *           "route": "mock-example",
         *           "order": 0
         *         }
         *       ],
         *       "dataScope": {
         *         "siteIds": [],
         *         "allSites": true,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "ownOnly": true,
         *         "scopeVersion": 0
         *       },
         *       "siteScope": {
         *         "siteIds": [],
         *         "allSites": true
         *       },
         *       "session": {
         *         "userId": "00000000-0000-4000-8000-000000000001",
         *         "idleExpiresAt": "2026-10-07T02:00:00Z",
         *         "absoluteExpiresAt": "2026-10-07T02:00:00Z"
         *       }
         *     }
         */
        CurrentUserData: {
            dataScope: components["schemas"]["DataScope"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            menus: components["schemas"]["Menu"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            permissionCodes: components["schemas"]["PermissionCode"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            roleIds: string[];
            session: components["schemas"]["SessionData"];
            siteScope: components["schemas"]["SiteScope"];
            user: components["schemas"]["UserSummary"];
        };
        /**
         * @description 每天机构时区指定时刻，endDate可省略；每个计划+当地日期唯一投递，不因页面是否打开改变。错过执行窗口的补发规则待P-015。
         * @example {
         *       "scheduleType": "DAILY",
         *       "localTime": "10:00",
         *       "timeZone": "Asia/Shanghai",
         *       "startDate": "2026-10-07"
         *     }
         */
        DailyNotificationSchedule: {
            /**
             * Format: date
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            endDate?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            localTime: string;
            /**
             * @description 判别字段固定为 DAILY。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            scheduleType: "DAILY";
            /**
             * Format: date
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            startDate: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            timeZone: "Asia/Shanghai";
        };
        /**
         * @description 地图指标不可用时计数为 null；地图点位独立调用 mapEndpoint。
         * @example {
         *       "meta": {
         *         "asOf": null,
         *         "status": "READY",
         *         "unit": "mock-example",
         *         "refreshPolicy": "POLL"
         *       },
         *       "deviceCount": null,
         *       "siteCount": null,
         *       "mapEndpoint": "/map/devices"
         *     }
         */
        DashboardMapOverview: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceCount: number | null;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            mapEndpoint: "/map/devices";
            meta: components["schemas"]["MetricMeta"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteCount: number | null;
        };
        /**
         * @description 12类首页区域（11类数据+应用入口）按METRIC_V1计算，共用meta/asOf/status/unit。源counter采集量与capture事实入库量分开，去重按typed target；提交水位至发布<=3秒/UI刷新<=2秒，未知/失败用既有状态与null。公式见policy。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "onlineDevices": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "total": null,
         *         "online": null,
         *         "onlineRate": null,
         *         "byDeviceType": [
         *           {
         *             "deviceType": "mock-example",
         *             "total": 0,
         *             "online": 0
         *           }
         *         ],
         *         "byPowerCategory": [
         *           {
         *             "powerCategoryCode": "mock-example",
         *             "label": "mock-example",
         *             "total": 0,
         *             "online": 0,
         *             "onlineRate": 0
         *           }
         *         ]
         *       },
         *       "lastSevenDaysCapture": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "series": [
         *           {
         *             "code": "mock-example",
         *             "label": "mock-example",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ]
         *       },
         *       "topCaptureSites": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "items": [
         *           {
         *             "key": "mock-example",
         *             "label": "mock-example",
         *             "value": 0
         *           }
         *         ]
         *       },
         *       "todayAndTotalCapture": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "todayCaptured": null,
         *         "allCaptured": null,
         *         "todayIngested": null,
         *         "allIngested": null
         *       },
         *       "deviceCaptureTotals": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "items": [
         *           {
         *             "code": "mock-example",
         *             "label": "mock-example",
         *             "count": 0
         *           }
         *         ]
         *       },
         *       "alertCount": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "value": null
         *       },
         *       "deviceMap": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "deviceCount": null,
         *         "siteCount": null,
         *         "mapEndpoint": "/map/devices"
         *       },
         *       "deduplicatedCapture": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "items": [
         *           {
         *             "code": "mock-example",
         *             "label": "mock-example",
         *             "count": 0
         *           }
         *         ]
         *       },
         *       "originTopTen": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "items": [
         *           {
         *             "key": "mock-example",
         *             "label": "mock-example",
         *             "value": 0
         *           }
         *         ]
         *       },
         *       "applicationEntries": [
         *         {
         *           "code": "mock-example",
         *           "label": "mock-example",
         *           "route": "mock-example"
         *         }
         *       ],
         *       "fenceRadioStatistics": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "series": [
         *           {
         *             "code": "mock-example",
         *             "label": "mock-example",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ]
         *       },
         *       "operationStatistics": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "series": [
         *           {
         *             "code": "mock-example",
         *             "label": "mock-example",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ]
         *       },
         *       "topSitesByDataType": [
         *         {
         *           "dataType": "FENCE",
         *           "ranking": {
         *             "meta": {
         *               "asOf": null,
         *               "status": "READY",
         *               "unit": "mock-example",
         *               "refreshPolicy": "POLL"
         *             },
         *             "items": [
         *               {
         *                 "key": "mock-example",
         *                 "label": "mock-example",
         *                 "value": 0
         *               }
         *             ]
         *           }
         *         }
         *       ],
         *       "deduplicatedSeries": [
         *         {
         *           "dataType": "FENCE",
         *           "originClass": "ALL",
         *           "metric": {
         *             "meta": {
         *               "asOf": null,
         *               "status": "READY",
         *               "unit": "mock-example",
         *               "refreshPolicy": "POLL"
         *             },
         *             "series": [
         *               {
         *                 "code": "mock-example",
         *                 "label": "mock-example",
         *                 "points": [
         *                   {
         *                     "at": "2026-10-07T02:00:00Z",
         *                     "value": null
         *                   }
         *                 ]
         *               }
         *             ]
         *           }
         *         }
         *       ],
         *       "deviceAlertCounts": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "items": [
         *           {
         *             "deviceId": "00000000-0000-4000-8000-000000000001",
         *             "deviceName": "mock-example",
         *             "count": 0
         *           }
         *         ]
         *       }
         *     }
         */
        DashboardSummary: {
            alertCount: components["schemas"]["MetricCard"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            applicationEntries: components["schemas"]["ApplicationEntry"][];
            /**
             * @deprecated
             * @description 旧兼容字段；新页面使用按dataType分组的对应指标。
             */
            deduplicatedCapture: components["schemas"]["CategoryMetric"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deduplicatedSeries: components["schemas"]["DeduplicatedSeries"][];
            deviceAlertCounts: components["schemas"]["DeviceAlertMetric"];
            deviceCaptureTotals: components["schemas"]["CategoryMetric"];
            deviceMap: components["schemas"]["DashboardMapOverview"];
            fenceRadioStatistics: components["schemas"]["SeriesMetric"];
            lastSevenDaysCapture: components["schemas"]["SeriesMetric"];
            onlineDevices: components["schemas"]["DeviceOnlineOverview"];
            operationStatistics: components["schemas"]["SeriesMetric"];
            originTopTen: components["schemas"]["RankMetric"];
            todayAndTotalCapture: components["schemas"]["CaptureTotals"];
            /**
             * @deprecated
             * @description 旧兼容字段；新页面使用按dataType分组的对应指标。
             */
            topCaptureSites: components["schemas"]["RankMetric"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            topSitesByDataType: components["schemas"]["TypedRankMetric"][];
        };
        /**
         * @description 按(dataType,siteId,ownershipPredicate)授权元组取角色并集及账号附加授权，不分别合并维度后扩大授权。allSites=true时siteIds必须[]，false且[]无场所；SUPER_ADMIN默认全部采集可见，其他对象和操作权限仍独立检查。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "siteIds": [],
         *       "allSites": true,
         *       "dataTypes": [
         *         "FENCE"
         *       ],
         *       "ownOnly": true,
         *       "scopeVersion": 0
         *     }
         */
        DataScope: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            allSites: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dataTypes: components["schemas"]["DataType"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            ownOnly: boolean;
            /** @description 服务端当前有效授权元组并集，逐项独立匹配后OR；禁止先合并dataTypes和siteIds形成笛卡尔扩权。多元组无法由旧字段精确表达时必返；[]拒绝全部，省略仅限单一旧格式能精确表达的情况。旧汇总字段仅供UI提示，不作为服务端授权依据。 */
            scopeGrants?: {
                dataType: components["schemas"]["DataType"];
                ownOnly: boolean;
                siteScope: components["schemas"]["SiteScope"];
            }[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            scopeVersion: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds: string[];
        };
        /**
         * @description 共享不能放宽源数据；固定datasetId+version。ARCHIVED只读可查、可显式恢复但不能新引用；活跃引用删除409，历史清理按自然月及引用保护。管理导入仍ALL_OR_NOTHING。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "datasetId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "datasetKind": "SINGLE",
         *       "status": "ACTIVE",
         *       "version": 0,
         *       "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *       "itemCount": 0,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "elementType": "CAPTURE"
         *     }
         */
        DatasetDetail: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            datasetId: string;
            datasetKind: components["schemas"]["DatasetKind"];
            elementType: components["schemas"]["DatasetElementType"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            itemCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ownerUserId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            sourceContextId?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            status: "ACTIVE" | "ARCHIVED";
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description CAPTURE以captureId相等；TARGET以targetType+targetId相等。跨类型身份关联另行复核，不在集合运算自动推断。
         * @example CAPTURE
         * @enum {string}
         */
        DatasetElementType: "CAPTURE" | "TARGET";
        /**
         * @description 接口模型 DatasetImportFromContext；具体业务范围以使用它的操作为准。
         * @example {
         *       "source": "CONTEXT",
         *       "contextId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        DatasetImportFromContext: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * @description 判别字段固定为 CONTEXT。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            source: "CONTEXT";
        };
        /**
         * @description ACTIVE DATASET_IMPORT文件；仅导入已有授权captureId或targetId，原始记录先走接入批次，不虚构场所/设备/目标。
         * @example {
         *       "source": "FILE",
         *       "fileAssetId": "00000000-0000-4000-8000-000000000001",
         *       "templateCode": "DATASET_ITEMS_V1"
         *     }
         */
        DatasetImportFromFile: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            fileAssetId: string;
            /**
             * @description 判别字段固定为 FILE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            source: "FILE";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            templateCode: "DATASET_ITEMS_V1";
        };
        /**
         * @description 接口模型 DatasetImportInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "source": "CONTEXT",
         *       "contextId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        DatasetImportInput: components["schemas"]["DatasetImportFromContext"] | components["schemas"]["DatasetImportFromFile"];
        /**
         * @description 只创建空容器；要导入查询结果，再调用 /datasets/{datasetId}/imports，以免同步请求吞入大集合。
         * @example {
         *       "name": "mock-example",
         *       "datasetKind": "SINGLE",
         *       "elementType": "CAPTURE"
         *     }
         */
        DatasetInput: {
            datasetKind: components["schemas"]["DatasetKind"];
            elementType: components["schemas"]["DatasetElementType"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
        };
        /**
         * @description 按elementType判别的强类型联合。
         * @example {
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "addedAt": "2026-10-07T02:00:00Z",
         *       "itemId": "00000000-0000-4000-8000-000000000001",
         *       "elementType": "TARGET",
         *       "displayTarget": "mock-example"
         *     }
         */
        DatasetItem: components["schemas"]["TargetDatasetItem"] | components["schemas"]["CaptureDatasetItem"];
        /**
         * @description 按elementType判别的强类型联合。
         * @example {
         *       "elementType": "TARGET",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       }
         *     }
         */
        DatasetItemInput: components["schemas"]["TargetDatasetItemInput"] | components["schemas"]["CaptureDatasetItemInput"];
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "addedAt": "2026-10-07T02:00:00Z",
         *           "itemId": "00000000-0000-4000-8000-000000000001",
         *           "elementType": "TARGET",
         *           "displayTarget": "mock-example"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        DatasetItemPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DatasetItem"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 接口模型 DatasetItemsInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "elementType": "TARGET",
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           }
         *         }
         *       ]
         *     }
         */
        DatasetItemsInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DatasetItemInput"][];
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example SINGLE
         * @enum {string}
         */
        DatasetKind: "SINGLE" | "MIXED";
        /**
         * @description 接口模型 DatasetPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "datasetId": "00000000-0000-4000-8000-000000000001",
         *           "name": "mock-example",
         *           "datasetKind": "SINGLE",
         *           "status": "ACTIVE",
         *           "version": 0,
         *           "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *           "itemCount": 0,
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "elementType": "CAPTURE"
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        DatasetPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DatasetDetail"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 DatasetUpdate；具体业务范围以使用它的操作为准。
         * @example {
         *       "name": "mock-example"
         *     }
         */
        DatasetUpdate: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
        };
        /**
         * @description 接口模型 DatasetVersion；具体业务范围以使用它的操作为准。
         * @example {
         *       "version": 0,
         *       "itemCount": 0,
         *       "operation": "mock-example",
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "createdBy": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        DatasetVersion: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            createdBy: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            itemCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            operation: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 DatasetVersionPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "version": 0,
         *           "itemCount": 0,
         *           "operation": "mock-example",
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "createdBy": "00000000-0000-4000-8000-000000000001"
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        DatasetVersionPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DatasetVersion"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example FENCE
         * @enum {string}
         */
        DataType: "FENCE" | "MAC" | "PLATE" | "FACE";
        /**
         * @example {
         *       "dataType": "FENCE",
         *       "totals": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "todayCaptured": null,
         *         "allCaptured": null,
         *         "todayIngested": null,
         *         "allIngested": null
         *       },
         *       "captureSeries": [
         *         {
         *           "metricCode": "CAPTURED",
         *           "metric": {
         *             "meta": {
         *               "asOf": null,
         *               "status": "READY",
         *               "unit": "mock-example",
         *               "refreshPolicy": "POLL"
         *             },
         *             "series": [
         *               {
         *                 "code": "mock-example",
         *                 "label": "mock-example",
         *                 "points": [
         *                   {
         *                     "at": "2026-10-07T02:00:00Z",
         *                     "value": null
         *                   }
         *                 ]
         *               }
         *             ]
         *           }
         *         }
         *       ],
         *       "anomalies": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "deviceAnomalies": [
         *           {
         *             "code": "UNREGISTERED_DEVICE",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ],
         *         "dataAnomalies": [
         *           {
         *             "code": "UNREGISTERED_DEVICE",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ],
         *         "volumeAnomalies": [
         *           {
         *             "code": "UNREGISTERED_DEVICE",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ]
         *       }
         *     }
         */
        DataTypeOperations: {
            anomalies: components["schemas"]["AnomalyOverview"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureSeries: components["schemas"]["CaptureMetricSeries"][];
            dataType: components["schemas"]["DataType"];
            totals: components["schemas"]["CaptureTotals"];
        };
        DecryptedCaptureItem: {
            /** Format: uuid */
            captureId: string;
            fields: components["schemas"]["DecryptedField"][];
        };
        DecryptedField: {
            fieldCode: string;
            value: string;
        };
        /**
         * @description capture:read可提交转交，recipient需capture:decrypt及对应范围；普通账号必须指定recipient。purpose10至1000字符，最多200采集；两名不同非申请人审批。只申请，不直接返回明文。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "purpose": "mock-example",
         *       "captureIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ]
         *     }
         */
        DecryptionInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureIds: string[];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            purpose: string;
            /**
             * Format: uuid
             * @description 指定具有capture:decrypt与相应范围的接收人。省略时仅解密授权申请人可默认自己；普通账号必须提供。
             */
            recipientUserId?: string;
        };
        /**
         * @description 不含明文或grantUrl。申请24小时过期，批准后指定recipient使用readDecryptionResult受控POST查看一次，展示许可5分钟；resultAvailable须审批/密钥/身份/当前权限/许可均有效。元数据自然月，明文不形成持久文件。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "requestId": "00000000-0000-4000-8000-000000000001",
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "status": "PENDING",
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "expiresAt": "2026-10-07T02:00:00Z",
         *       "resultAvailable": true
         *     }
         */
        DecryptionRequestDetail: {
            /**
             * Format: uuid
             * @description 创建解密申请时同事务创建readDecryptionResult审批；申请人与指定recipient可查该审批，普通申请人不能消费展示许可。
             */
            approvalId?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            expiresAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            requestId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resultAvailable: boolean;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            status: "PENDING" | "APPROVED" | "REJECTED" | "EXPIRED";
        };
        DecryptionResult: {
            items: components["schemas"]["DecryptedCaptureItem"][];
            /** Format: uuid */
            requestId: string;
            /** @enum {boolean} */
            retainPlaintext: false;
            /** Format: date-time */
            viewedAt: string;
        };
        DecryptionResultResponse: {
            /** @enum {string} */
            code: "0";
            data: components["schemas"]["DecryptionResult"];
            message: string;
            traceId: string;
        };
        /**
         * @example {
         *       "dataType": "FENCE",
         *       "originClass": "ALL",
         *       "metric": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "series": [
         *           {
         *             "code": "mock-example",
         *             "label": "mock-example",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ]
         *       }
         *     }
         */
        DeduplicatedSeries: {
            dataType: components["schemas"]["DataType"];
            metric: components["schemas"]["SeriesMetric"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            originClass: "ALL" | "OUT_OF_PROVINCE" | "OVERSEAS";
        };
        /** @description 接口模型 DeleteAnalysisTaskResponse；具体业务范围以使用它的操作为准。 */
        DeleteAnalysisTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 DeleteDatasetResponse；具体业务范围以使用它的操作为准。 */
        DeleteDatasetResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        DeleteDeviceCommandResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 DeleteDeviceGroupResponse；具体业务范围以使用它的操作为准。 */
        DeleteDeviceGroupResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 DeleteDeviceResponse；具体业务范围以使用它的操作为准。 */
        DeleteDeviceResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 DeleteNotificationTaskResponse；具体业务范围以使用它的操作为准。 */
        DeleteNotificationTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 DeletePersonLibraryResponse；具体业务范围以使用它的操作为准。 */
        DeletePersonLibraryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 DeleteReportResponse；具体业务范围以使用它的操作为准。 */
        DeleteReportResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        DeleteResourcesBatchResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["BatchDeletionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        DeleteRoleApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "deleteRole";
            pathParameters: {
                /** Format: uuid */
                roleId: string;
            };
        };
        /** @description 接口模型 DeleteRoleResponse；具体业务范围以使用它的操作为准。 */
        DeleteRoleResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 DeleteSiteResponse；具体业务范围以使用它的操作为准。 */
        DeleteSiteResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        DeleteUserApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "deleteUser";
            pathParameters: {
                /** Format: uuid */
                userId: string;
            };
        };
        /** @description 接口模型 DeleteUserResponse；具体业务范围以使用它的操作为准。 */
        DeleteUserResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 DeleteWatchRuleResponse；具体业务范围以使用它的操作为准。 */
        DeleteWatchRuleResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 沿用PENDING/SENT/ACKNOWLEDGED/FAILED/UNKNOWN；SMS成功仅送达成功回执。SMTP250仅SENT，DSN成功才ACKNOWLEDGED；平台收件箱持久化可ACKNOWLEDGED。UNKNOWN先对账不盲重发；明确可重试失败3次30/120/600秒+20%抖动，回执窗口24小时。验收连续7天/至少1000逻辑投递，重试合并一次，成功率>=99%。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "deliveryId": "00000000-0000-4000-8000-000000000001",
         *       "channel": "SMS",
         *       "recipientMasked": "mock-example",
         *       "status": "PENDING",
         *       "retryCount": 0,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "traceId": "mock-example",
         *       "taskName": "mock-example",
         *       "contentSummary": "mock-example"
         *     }
         */
        Delivery: {
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            acknowledgedAt?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            alertId?: string;
            channel: components["schemas"]["NotificationChannel"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            contentSummary: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deliveryId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            errorCode?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            errorReason?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            executionTaskId?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            notificationTaskId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            providerReceiptRef?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            recipientMasked: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            retryCount: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ruleId?: string;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            sentAt?: string | null;
            status: components["schemas"]["DeliveryStatus"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            taskName: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description FILTERED导出完整筛选快照；SELECTED_PAGE必须deliveryIds（来自当前页），只导出这些且重校SMS权限。
         * @example {
         *       "search": {
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         }
         *       },
         *       "format": "XLSX",
         *       "reason": "mock-example",
         *       "scope": "FILTERED"
         *     }
         */
        DeliveryExportInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deliveryIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            scope: "FILTERED" | "SELECTED_PAGE";
            search: components["schemas"]["SmsLogSearchRequest"];
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "deliveryId": "00000000-0000-4000-8000-000000000001",
         *           "channel": "SMS",
         *           "recipientMasked": "mock-example",
         *           "status": "PENDING",
         *           "retryCount": 0,
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "traceId": "mock-example",
         *           "taskName": "mock-example",
         *           "contentSummary": "mock-example"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        DeliveryPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["Delivery"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description UNKNOWN 表示供应商回执未确认，不可盲目重发。
         * @example PENDING
         * @enum {string}
         */
        DeliveryStatus: "PENDING" | "SENT" | "ACKNOWLEDGED" | "FAILED" | "UNKNOWN";
        /**
         * @example {
         *       "deviceId": "00000000-0000-4000-8000-000000000001",
         *       "deviceName": "mock-example",
         *       "count": 0
         *     }
         */
        DeviceAlertCount: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            count: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceName: string;
        };
        /**
         * @example {
         *       "meta": {
         *         "asOf": null,
         *         "status": "READY",
         *         "unit": "mock-example",
         *         "refreshPolicy": "POLL"
         *       },
         *       "items": [
         *         {
         *           "deviceId": "00000000-0000-4000-8000-000000000001",
         *           "deviceName": "mock-example",
         *           "count": 0
         *         }
         *       ]
         *     }
         */
        DeviceAlertMetric: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DeviceAlertCount"][];
            meta: components["schemas"]["MetricMeta"];
        };
        /**
         * @description 接口模型 DeviceCapturePoint；具体业务范围以使用它的操作为准。
         * @example {
         *       "at": "2026-10-07T02:00:00Z",
         *       "captureCount": 0,
         *       "ingestedCount": 0
         *     }
         */
        DeviceCapturePoint: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            at: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            ingestedCount: number;
        };
        /**
         * @description 接口模型 DeviceCaptureStatistics；具体业务范围以使用它的操作为准。
         * @example {
         *       "deviceId": "00000000-0000-4000-8000-000000000001",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       },
         *       "granularity": "HOUR",
         *       "points": [
         *         {
         *           "at": "2026-10-07T02:00:00Z",
         *           "captureCount": 0,
         *           "ingestedCount": 0
         *         }
         *       ],
         *       "totalCaptureCount": 0,
         *       "totalIngestedCount": 0,
         *       "asOf": "2026-10-07T02:00:00Z"
         *     }
         */
        DeviceCaptureStatistics: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            granularity: "HOUR" | "DAY" | "MONTH";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            points: components["schemas"]["DeviceCapturePoint"][];
            timeRange: components["schemas"]["TimeRange"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            totalCaptureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            totalIngestedCount: number;
        };
        /**
         * @description 接口模型 DeviceCommandDeviceResult；具体业务范围以使用它的操作为准。
         * @example {
         *       "deviceId": "00000000-0000-4000-8000-000000000001",
         *       "status": "PENDING",
         *       "attemptNo": 0
         *     }
         */
        DeviceCommandDeviceResult: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            acknowledgementId?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            attemptNo: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string;
            errorCode?: components["schemas"]["ErrorCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            errorMessage?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            finishedAt?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            startedAt?: string | null;
            status: components["schemas"]["TaskStatus"];
        };
        /**
         * @description RESET 与 APPLY_PARAMETERS 是已提出的动作；执行设备列表必须冻结，厂商私有命令不暴露。
         * @example {
         *       "name": "核心组参数配置",
         *       "commandType": "APPLY_PARAMETERS",
         *       "deviceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "groupId": "00000000-0000-4000-8000-000000000001",
         *       "regionCode": "MOCK-CENTER",
         *       "parameterChanges": [
         *         {
         *           "parameterCode": "CAPTURE_INTERVAL",
         *           "valueType": "INTEGER",
         *           "integerValue": 60
         *         }
         *       ],
         *       "reason": "例行配置；参数编码仅示意"
         *     }
         */
        DeviceCommandInput: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            commandType: "RESET" | "APPLY_PARAMETERS";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds: string[];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            groupId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterChanges?: components["schemas"]["DeviceParameterChange"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode?: string;
        };
        /**
         * @description 接口模型 DeviceCommandResultPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "deviceId": "00000000-0000-4000-8000-000000000001",
         *           "status": "PENDING",
         *           "attemptNo": 0
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        DeviceCommandResultPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DeviceCommandDeviceResult"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 服务端只允许重试 FAILED 设备，不重复执行 SUCCESS 设备。
         * @example {
         *       "failedDeviceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        DeviceCommandRetryInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            failedDeviceIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 接口模型 DeviceCommandTask；具体业务范围以使用它的操作为准。
         * @example {
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "commandType": "RESET",
         *       "requestSnapshot": {
         *         "commandType": "RESET",
         *         "deviceIds": [
         *           "00000000-0000-4000-8000-000000000001"
         *         ],
         *         "reason": "mock-example",
         *         "name": "mock-example"
         *       },
         *       "status": "PENDING",
         *       "progress": 0,
         *       "requestedDeviceCount": 1,
         *       "successCount": 0,
         *       "failedCount": 0,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "requestedByUserId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "version": 0
         *     }
         */
        DeviceCommandTask: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            commandType: "RESET" | "APPLY_PARAMETERS";
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceNames?: string[];
            errorCode?: components["schemas"]["ErrorCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            failedCount: number;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            finishedAt?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            groupName?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            progress: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            requestedByUserId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            requestedDeviceCount: number;
            requestSnapshot: components["schemas"]["DeviceCommandInput"];
            status: components["schemas"]["TaskStatus"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            successCount: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 DeviceCommandTaskPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "taskId": "00000000-0000-4000-8000-000000000001",
         *           "commandType": "RESET",
         *           "requestSnapshot": {
         *             "commandType": "RESET",
         *             "deviceIds": [
         *               "00000000-0000-4000-8000-000000000001"
         *             ],
         *             "reason": "mock-example",
         *             "name": "mock-example"
         *           },
         *           "status": "PENDING",
         *           "progress": 0,
         *           "requestedDeviceCount": 1,
         *           "successCount": 0,
         *           "failedCount": 0,
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "requestedByUserId": "00000000-0000-4000-8000-000000000001",
         *           "name": "mock-example",
         *           "version": 0
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        DeviceCommandTaskPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DeviceCommandTask"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 DeviceDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "deviceId": "00000000-0000-4000-8000-000000000001",
         *       "serialNumber": "mock-example",
         *       "name": "mock-example",
         *       "siteId": "00000000-0000-4000-8000-000000000001",
         *       "category": "mock-example",
         *       "status": "ONLINE",
         *       "lastSeenAt": null,
         *       "todayCaptureCount": 0,
         *       "cumulativeCaptureCount": 0,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1,
         *       "siteName": "mock-example",
         *       "regionCode": "mock-example",
         *       "groupIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "resetCount": 0
         *     }
         */
        DeviceDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            category: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cumulativeCaptureCount: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            groupIds: string[];
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            installedAt?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            lastSeenAt: string | null;
            location?: components["schemas"]["GeoPoint"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            manufacturer?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            model?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            parameters?: components["schemas"]["DeviceParameters"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resetCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            serialNumber: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteName: string;
            status: components["schemas"]["DeviceStatus"];
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            statusSince?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            todayCaptureCount: number;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 DeviceExportInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "format": "XLSX",
         *       "reason": "mock-example"
         *     }
         */
        DeviceExportInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            category?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            status?: components["schemas"]["DeviceStatus"];
        };
        /**
         * @description 接口模型 DeviceGroupDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "groupId": "00000000-0000-4000-8000-000000000001",
         *       "siteId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "memberCount": 0,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1
         *     }
         */
        DeviceGroupDetail: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            groupId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            memberCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 DeviceGroupInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "siteId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example"
         *     }
         */
        DeviceGroupInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string;
        };
        /**
         * @description 接口模型 DeviceGroupMemberPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "deviceId": "00000000-0000-4000-8000-000000000001",
         *           "serialNumber": "mock-example",
         *           "name": "mock-example",
         *           "siteId": "00000000-0000-4000-8000-000000000001",
         *           "category": "mock-example",
         *           "status": "ONLINE",
         *           "lastSeenAt": null,
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "version": 1,
         *           "siteName": "mock-example",
         *           "regionCode": "mock-example",
         *           "groupIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "resetCount": 0
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        DeviceGroupMemberPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DeviceSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 DeviceGroupMembersInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "deviceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        DeviceGroupMembersInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 接口模型 DeviceGroupPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "groupId": "00000000-0000-4000-8000-000000000001",
         *           "siteId": "00000000-0000-4000-8000-000000000001",
         *           "name": "mock-example",
         *           "memberCount": 0,
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "version": 1
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        DeviceGroupPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DeviceGroupSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 DeviceGroupSummary；具体业务范围以使用它的操作为准。
         * @example {
         *       "groupId": "00000000-0000-4000-8000-000000000001",
         *       "siteId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "memberCount": 0,
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1
         *     }
         */
        DeviceGroupSummary: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            groupId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            memberCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description targetDeviceId供行操作更新单设备；给出时恰好一行且编号匹配，否则422。批量按serialNumber匹配；UPDATE只更新元数据，不伪造心跳状态。
         * @example {
         *       "fileAssetId": "00000000-0000-4000-8000-000000000001",
         *       "duplicatePolicy": "SKIP",
         *       "reason": "mock-example",
         *       "templateCode": "DEVICES_V1"
         *     }
         */
        DeviceImportInput: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            duplicatePolicy: "SKIP" | "FAIL" | "UPDATE";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            fileAssetId: string;
            /**
             * @deprecated
             * @description 兼容旧导入请求的别名；只接受模板version=1，省略等同1。平台标准模板不需要前端另猜厂商mappingVersion。
             * @enum {string}
             */
            mappingVersion?: "1";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            targetDeviceId?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            templateCode: "DEVICES_V1";
        };
        /**
         * @description status由采集心跳/适配器权威更新，管理编辑不能手工ONLINE。原型的状态编辑为Mock模拟能力；真实人工状态覆盖需P-018确认。
         * @example {
         *       "serialNumber": "mock-example",
         *       "name": "mock-example",
         *       "siteId": "00000000-0000-4000-8000-000000000001",
         *       "category": "mock-example"
         *     }
         */
        DeviceInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            category: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            groupIds?: string[];
            location?: components["schemas"]["GeoPoint"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            manufacturer?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            model?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            serialNumber: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string;
        };
        /**
         * @description UNAVAILABLE 时数值为 null，不伪造 0。
         * @example {
         *       "meta": {
         *         "asOf": null,
         *         "status": "READY",
         *         "unit": "mock-example",
         *         "refreshPolicy": "POLL"
         *       },
         *       "total": null,
         *       "online": null,
         *       "onlineRate": null,
         *       "byDeviceType": [
         *         {
         *           "deviceType": "mock-example",
         *           "total": 0,
         *           "online": 0
         *         }
         *       ],
         *       "byPowerCategory": [
         *         {
         *           "powerCategoryCode": "mock-example",
         *           "label": "mock-example",
         *           "total": 0,
         *           "online": 0,
         *           "onlineRate": 0
         *         }
         *       ]
         *     }
         */
        DeviceOnlineOverview: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            byDeviceType: components["schemas"]["DeviceOnlineOverviewByDeviceTypeItem"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            byPowerCategory: components["schemas"]["PowerOnlineBucket"][];
            meta: components["schemas"]["MetricMeta"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            online: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            onlineRate: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number | null;
        };
        /**
         * @example {
         *       "deviceType": "mock-example",
         *       "total": 0,
         *       "online": 0
         *     }
         */
        DeviceOnlineOverviewByDeviceTypeItem: {
            deviceType: string;
            online: number;
            total: number;
        };
        /**
         * @description 接口模型 DeviceOnlineReportPreview；具体业务范围以使用它的操作为准。
         * @example {
         *       "reportType": "DEVICE_REALTIME_ONLINE_RATE",
         *       "regionCode": "mock-example",
         *       "deviceCount": 0,
         *       "onlineCount": 0,
         *       "onlineRate": 0,
         *       "asOf": "2026-10-07T02:00:00Z",
         *       "items": [
         *         {
         *           "regionCode": "mock-example",
         *           "regionName": "mock-example",
         *           "deviceCount": 0,
         *           "onlineCount": 0,
         *           "onlineRate": 0
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1,
         *       "previewKind": "ONLINE_RATE"
         *     }
         */
        DeviceOnlineReportPreview: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["RegionOnlineReportRow"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            onlineCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            onlineRate: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /**
             * @description 固定预览类型，与reportType细分报表类型分开。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            previewKind: "ONLINE_RATE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            reportType: "DEVICE_REALTIME_ONLINE_RATE";
        };
        /**
         * @description 接口模型 DevicePage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "deviceId": "00000000-0000-4000-8000-000000000001",
         *           "serialNumber": "mock-example",
         *           "name": "mock-example",
         *           "siteId": "00000000-0000-4000-8000-000000000001",
         *           "category": "mock-example",
         *           "status": "ONLINE",
         *           "lastSeenAt": null,
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "version": 1,
         *           "siteName": "mock-example",
         *           "regionCode": "mock-example",
         *           "groupIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "resetCount": 0
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        DevicePage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DeviceSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 仅版本化厂商DeviceProfile登记的参数，保留TEXT/INTEGER/NUMBER/BOOLEAN类型；敏感值不回原文。未绑定真实profile不得假造参数，实际读写不可用返回503。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "parameterCode": "mock-example",
         *       "label": "mock-example",
         *       "valueType": "TEXT",
         *       "textValue": "mock-example",
         *       "readOnly": true,
         *       "updatedAt": "2026-10-07T02:00:00Z"
         *     }
         */
        DeviceParameter: components["schemas"]["TextDeviceParameter"] | components["schemas"]["IntegerDeviceParameter"] | components["schemas"]["NumberDeviceParameter"] | components["schemas"]["BooleanDeviceParameter"];
        /**
         * @description 变更写desired，真实回执/回读后才改reported。未知键400、越界422，敏感键/批量>=10台按审批，冻结逐设备集合与版本。RESET不自动映射恢复出厂。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "TEXT",
         *       "textValue": "mock-example"
         *     }
         */
        DeviceParameterChange: components["schemas"]["TextDeviceParameterChange"] | components["schemas"]["IntegerDeviceParameterChange"] | components["schemas"]["NumberDeviceParameterChange"] | components["schemas"]["BooleanDeviceParameterChange"];
        /**
         * @description 接口模型 DeviceParameters；具体业务范围以使用它的操作为准。
         * @example {
         *       "deviceId": "00000000-0000-4000-8000-000000000001",
         *       "parameterVersion": "mock-example",
         *       "items": [
         *         {
         *           "parameterCode": "mock-example",
         *           "label": "mock-example",
         *           "valueType": "TEXT",
         *           "textValue": "mock-example",
         *           "readOnly": true,
         *           "updatedAt": "2026-10-07T02:00:00Z"
         *         }
         *       ],
         *       "asOf": "2026-10-07T02:00:00Z"
         *     }
         */
        DeviceParameters: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DeviceParameter"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterVersion: string;
        };
        /**
         * @description 接口模型 DeviceParameterUpdateInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "changes": [
         *         {
         *           "parameterCode": "mock-example",
         *           "valueType": "TEXT",
         *           "textValue": "mock-example"
         *         }
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        DeviceParameterUpdateInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            changes: components["schemas"]["DeviceParameterChange"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example ONLINE
         * @enum {string}
         */
        DeviceStatus: "ONLINE" | "OFFLINE" | "UNKNOWN" | "FAULT";
        /**
         * @description 接口模型 DeviceStatusData；具体业务范围以使用它的操作为准。
         * @example {
         *       "deviceId": "00000000-0000-4000-8000-000000000001",
         *       "status": "ONLINE",
         *       "asOf": "2026-10-07T02:00:00Z"
         *     }
         */
        DeviceStatusData: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            lastSeenAt?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            since?: string | null;
            status: components["schemas"]["DeviceStatus"];
        };
        /**
         * @description 按状态变更事件重建离线区间；previousStatus→status，OFFLINE后首个ONLINE为恢复，最后未恢复则结束为空。reason非供应商原始报文。
         * @example {
         *       "eventId": "00000000-0000-4000-8000-000000000001",
         *       "deviceId": "00000000-0000-4000-8000-000000000001",
         *       "previousStatus": "ONLINE",
         *       "status": "ONLINE",
         *       "occurredAt": "2026-10-07T02:00:00Z",
         *       "source": "mock-example"
         *     }
         */
        DeviceStatusEvent: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            eventId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            occurredAt: string;
            previousStatus: components["schemas"]["DeviceStatus"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            status: components["schemas"]["DeviceStatus"];
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "eventId": "00000000-0000-4000-8000-000000000001",
         *           "deviceId": "00000000-0000-4000-8000-000000000001",
         *           "previousStatus": "ONLINE",
         *           "status": "ONLINE",
         *           "occurredAt": "2026-10-07T02:00:00Z",
         *           "source": "mock-example"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        DeviceStatusEventPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DeviceStatusEvent"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 接口模型 DeviceStatusReportPreview；具体业务范围以使用它的操作为准。
         * @example {
         *       "reportType": "DEVICE_REALTIME_STATUS",
         *       "regionCode": "mock-example",
         *       "items": [
         *         {
         *           "deviceId": "00000000-0000-4000-8000-000000000001",
         *           "serialNumber": "mock-example",
         *           "name": "mock-example",
         *           "siteId": "00000000-0000-4000-8000-000000000001",
         *           "category": "mock-example",
         *           "status": "ONLINE",
         *           "lastSeenAt": null,
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "version": 1,
         *           "siteName": "mock-example",
         *           "regionCode": "mock-example",
         *           "groupIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "resetCount": 0
         *         }
         *       ],
         *       "asOf": "2026-10-07T02:00:00Z",
         *       "nextCursor": null,
         *       "pageSize": 1,
         *       "previewKind": "DEVICE_STATUS"
         *     }
         */
        DeviceStatusReportPreview: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DeviceSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /**
             * @description 固定预览类型，与reportType细分报表类型分开。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            previewKind: "DEVICE_STATUS";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            reportType: "DEVICE_REALTIME_STATUS";
        };
        /**
         * @description 接口模型 DeviceSummary；具体业务范围以使用它的操作为准。
         * @example {
         *       "deviceId": "00000000-0000-4000-8000-000000000001",
         *       "serialNumber": "mock-example",
         *       "name": "mock-example",
         *       "siteId": "00000000-0000-4000-8000-000000000001",
         *       "category": "mock-example",
         *       "status": "ONLINE",
         *       "lastSeenAt": null,
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1,
         *       "siteName": "mock-example",
         *       "regionCode": "mock-example",
         *       "groupIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "resetCount": 0
         *     }
         */
        DeviceSummary: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            category: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            groupIds: string[];
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            lastSeenAt: string | null;
            location?: components["schemas"]["GeoPoint"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resetCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            serialNumber: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteName: string;
            status: components["schemas"]["DeviceStatus"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 DictionaryData；具体业务范围以使用它的操作为准。
         * @example {
         *       "dictionaryCode": "mock-example",
         *       "version": "mock-example",
         *       "items": [
         *         {
         *           "code": "mock-example",
         *           "label": "mock-example",
         *           "order": 0,
         *           "enabled": true
         *         }
         *       ]
         *     }
         */
        DictionaryData: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dictionaryCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["DictionaryItem"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: string;
        };
        /**
         * @description 接口模型 DictionaryItem；具体业务范围以使用它的操作为准。
         * @example {
         *       "code": "mock-example",
         *       "label": "mock-example",
         *       "order": 0,
         *       "enabled": true
         *     }
         */
        DictionaryItem: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            code: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            enabled: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            order: number;
        };
        DisableUserApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "disableUser";
            pathParameters: {
                /** Format: uuid */
                userId: string;
            };
            requestBody: components["schemas"]["UserStateInput"];
        };
        /** @description 接口模型 DisableUserResponse；具体业务范围以使用它的操作为准。 */
        DisableUserResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["UserDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 DistanceMetersModelParameterValue；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "DISTANCE_METERS",
         *       "numberValue": 0
         *     }
         */
        DistanceMetersModelParameterValue: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            numberValue: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /**
             * @description 判别字段固定为 DISTANCE_METERS。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "DISTANCE_METERS";
        };
        /**
         * @description 接口模型 DurationSecondsModelParameterValue；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "DURATION_SECONDS",
         *       "integerValue": 0
         *     }
         */
        DurationSecondsModelParameterValue: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            integerValue: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /**
             * @description 判别字段固定为 DURATION_SECONDS。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "DURATION_SECONDS";
        };
        /**
         * @description 敏感收件地址仅请求体，响应使用Delivery.recipientMasked，不回传明文。
         * @example {
         *       "channel": "EMAIL",
         *       "address": "mock@example.invalid"
         *     }
         */
        EmailNotificationTarget: {
            /**
             * Format: email
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            address: string;
            /**
             * @description 判别字段固定为 EMAIL。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            channel: "EMAIL";
        };
        EnableUserApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "enableUser";
            pathParameters: {
                /** Format: uuid */
                userId: string;
            };
            requestBody: components["schemas"]["UserStateInput"];
        };
        /** @description 接口模型 EnableUserResponse；具体业务范围以使用它的操作为准。 */
        EnableUserResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["UserDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example VALIDATION_ERROR
         * @enum {string}
         */
        ErrorCode: "VALIDATION_ERROR" | "AUTH_REQUIRED" | "AUTH_INVALID_CREDENTIALS" | "AUTH_CSRF_INVALID" | "AUTH_SESSION_EXPIRED" | "PERMISSION_DENIED" | "PERMISSION_DATA_SCOPE" | "DATA_INVALID_FILTER" | "DATA_QUERY_TOO_BROAD" | "DATA_NOT_FOUND" | "RESOURCE_CONFLICT" | "RESOURCE_VERSION_CONFLICT" | "TASK_CONFLICT" | "TASK_NOT_CANCELLABLE" | "TASK_FAILED" | "ANALYSIS_INVALID_RULE" | "DEVICE_OFFLINE" | "ALERT_RULE_INACTIVE" | "FILE_INVALID" | "FILE_EXPIRED" | "RATE_LIMITED" | "SYSTEM_INTERNAL" | "SYSTEM_UNAVAILABLE" | "CONTEXT_EXPIRED" | "TASK_RESULT_NOT_READY" | "REQUIREMENT_PENDING" | "NOTIFICATION_INVALID_RECIPIENT" | "MODEL_PARAMETER_INVALID" | "DATASET_ELEMENT_TYPE_MISMATCH" | "FILE_TOO_LARGE" | "FILE_MEDIA_UNSUPPORTED" | "DATA_CURSOR_INVALID" | "IDEMPOTENCY_CONFLICT" | "MAP_GEOMETRY_INVALID";
        /** @description 业务失败返回非 2xx；details 可为空数组，不包含敏感入参或堆栈。 */
        ErrorResponse: {
            code: components["schemas"]["ErrorCode"];
            /**
             * @description 失败固定null。OAS3.0以nullable string且not string表达仅null；代码生成器可能显示string|null，但运行时禁止非null。
             * @example null
             */
            data: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            details: components["schemas"]["FieldError"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ExportAnalysisResultsApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "exportAnalysisResults";
            pathParameters: {
                /** Format: uuid */
                analysisTaskId: string;
            };
            requestBody: components["schemas"]["ResultExportInput"];
        };
        /** @description 接口模型 ExportAnalysisResultsResponse；具体业务范围以使用它的操作为准。 */
        ExportAnalysisResultsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ExportAnalysisTasksApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "exportAnalysisTasks";
            requestBody: components["schemas"]["AnalysisTasksExportInput"];
        };
        ExportAnalysisTasksResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ExportAuditEventsApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "exportAuditEvents";
            requestBody: components["schemas"]["AuditExportInput"];
        };
        /** @description 接口模型 ExportAuditEventsResponse；具体业务范围以使用它的操作为准。 */
        ExportAuditEventsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ExportDatasetApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "exportDataset";
            pathParameters: {
                /** Format: uuid */
                datasetId: string;
            };
            requestBody: components["schemas"]["ResultExportInput"];
        };
        /** @description 接口模型 ExportDatasetResponse；具体业务范围以使用它的操作为准。 */
        ExportDatasetResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ExportDevicesResponse；具体业务范围以使用它的操作为准。 */
        ExportDevicesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ExportMetricsApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "exportMetrics";
            requestBody: components["schemas"]["MetricExportInput"];
        };
        ExportMetricsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ExportPersonLibrariesApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "exportPersonLibraries";
            requestBody: components["schemas"]["PersonLibrariesExportInput"];
        };
        ExportPersonLibrariesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ExportPersonLibraryApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "exportPersonLibrary";
            pathParameters: {
                /** Format: uuid */
                libraryId: string;
            };
            requestBody: components["schemas"]["PersonLibraryExportInput"];
        };
        /** @description 接口模型 ExportPersonLibraryResponse；具体业务范围以使用它的操作为准。 */
        ExportPersonLibraryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ExportProfileApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "exportProfile";
            pathParameters: {
                /** Format: uuid */
                profileId: string;
            };
            requestBody: components["schemas"]["ProfileExportInput"];
        };
        /** @description 接口模型 ExportProfileResponse；具体业务范围以使用它的操作为准。 */
        ExportProfileResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 保持异步XLSX/CSV/ZIP；最多100万行/1GiB、每5万行分片，超限422 DATA_QUERY_TOO_BROAD。文件保存一年（12个自然月）、下载授权5分钟且每次校验当前权限，不新增同步导出。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "format": "XLSX"
         *     }
         */
        ExportRequest: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            columns?: components["schemas"]["CaptureExportColumn"][];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX" | "CSV" | "ZIP";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            purpose?: string;
        };
        ExportSmsLogsApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "exportSmsLogs";
            requestBody: components["schemas"]["DeliveryExportInput"];
        };
        /** @description 接口模型 ExportSmsLogsResponse；具体业务范围以使用它的操作为准。 */
        ExportSmsLogsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 FaceCaptureRecord；具体业务范围以使用它的操作为准。；targetType必返，表示载荷主目标类型；FENCE须IMSI或IMEI，其它分别MAC/PLATE/FACE。targetId=null表示尚未解析代理ID，不能直接研判，主类型仍返回。
         * @example {
         *       "captureId": "00000000-0000-4000-8000-000000000001",
         *       "dataType": "FACE",
         *       "targetId": null,
         *       "deviceId": null,
         *       "siteId": null,
         *       "captureTime": "2026-10-07T02:00:00Z",
         *       "ingestedAt": "2026-10-07T02:00:00Z",
         *       "longitude": null,
         *       "latitude": null,
         *       "source": "mock-example",
         *       "status": "VALID",
         *       "displayTarget": "mock-example",
         *       "isMasked": true,
         *       "face": {
         *         "imageAssetId": "00000000-0000-4000-8000-000000000001",
         *         "modelVersion": null,
         *         "qualityScore": null
         *       },
         *       "targetType": "FACE"
         *     }
         */
        FaceCaptureRecord: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            captureId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            captureTime: string;
            /**
             * @description 判别字段固定为 FACE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "FACE";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            face: components["schemas"]["FaceRecord"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            ingestedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            isMasked: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            latitude: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            longitude: number | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            status: components["schemas"]["CaptureStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            targetId: string | null;
            targetType: components["schemas"]["TargetType"];
        };
        /**
         * @description 供 Context 保存的不可变业务筛选条件；不包含本次分页游标。；targetIds同组为OR，与其它条件为AND；禁止把脱敏displayTarget作精确条件。
         * @example {
         *       "dataType": "FACE",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        FaceCaptureSearchFilter: {
            /**
             * @description 判别字段固定为 FACE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "FACE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetIds?: string[];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 按场所和时间检索已入库人像采集；上传搜图使用独立异步任务。；targetIds同组为OR，与其它条件为AND；禁止把脱敏displayTarget作精确条件。
         * @example {
         *       "dataType": "FACE",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        FaceCaptureSearchRequest: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cursor?: string;
            /**
             * @description 判别字段固定为 FACE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "FACE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 20
             */
            pageSize?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default captureTime
             * @enum {string}
             */
            sortBy?: "captureTime" | "captureId";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default DESC
             * @enum {string}
             */
            sortOrder?: "ASC" | "DESC";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetIds?: string[];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description imageAssetId必须可用且受控；modelVersion记录实际合法模型，未知质量标尺qualityScore=null，不用统一阈值0.8或伪质量分。原图/缩略图访问5分钟、物理保存一年（12个自然月）。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "imageAssetId": "00000000-0000-4000-8000-000000000001",
         *       "modelVersion": null,
         *       "qualityScore": null
         *     }
         */
        FaceRecord: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            imageAssetId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            modelVersion: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            qualityScore: number | null;
        };
        /**
         * @description 接口模型 FaceSearchCandidate；具体业务范围以使用它的操作为准。
         * @example {
         *       "capture": {
         *         "captureId": "00000000-0000-4000-8000-000000000001",
         *         "dataType": "FACE",
         *         "targetId": null,
         *         "deviceId": null,
         *         "siteId": null,
         *         "captureTime": "2026-10-07T02:00:00Z",
         *         "ingestedAt": "2026-10-07T02:00:00Z",
         *         "longitude": null,
         *         "latitude": null,
         *         "source": "mock-example",
         *         "status": "VALID",
         *         "displayTarget": "mock-example",
         *         "isMasked": true,
         *         "face": {
         *           "imageAssetId": "00000000-0000-4000-8000-000000000001",
         *           "modelVersion": null,
         *           "qualityScore": null
         *         },
         *         "targetType": "FACE"
         *       },
         *       "similarity": 0,
         *       "modelVersion": "mock-example",
         *       "reviewRequired": true
         *     }
         */
        FaceSearchCandidate: {
            capture: components["schemas"]["FaceCaptureRecord"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            modelVersion: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reviewRequired: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            similarity: number;
        };
        /**
         * @description PD-2026-10-19.v1暂定：minSimilarity默认0，score>minSimilarity筛选仅作候选排序；默认20最多100、人工复核。授权内网模型未就绪503，无通用可信同人阈值；10万底库端到端2秒目标含队列/实际检索/结果可用，需实测，不以普通30分钟任务时限替代。
         * @example {
         *       "imageAssetId": "00000000-0000-4000-8000-000000000001",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       },
         *       "candidateLimit": 1
         *     }
         */
        FaceSearchInput: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 20
             */
            candidateLimit: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            imageAssetId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 0
             */
            minSimilarity?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "capture": {
         *             "captureId": "00000000-0000-4000-8000-000000000001",
         *             "dataType": "FACE",
         *             "targetId": null,
         *             "deviceId": null,
         *             "siteId": null,
         *             "captureTime": "2026-10-07T02:00:00Z",
         *             "ingestedAt": "2026-10-07T02:00:00Z",
         *             "longitude": null,
         *             "latitude": null,
         *             "source": "mock-example",
         *             "status": "VALID",
         *             "displayTarget": "mock-example",
         *             "isMasked": true,
         *             "face": {
         *               "imageAssetId": "00000000-0000-4000-8000-000000000001",
         *               "modelVersion": null,
         *               "qualityScore": null
         *             },
         *             "targetType": "FACE"
         *           },
         *           "similarity": 0,
         *           "modelVersion": "mock-example",
         *           "reviewRequired": true
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        FaceSearchResultPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["FaceSearchCandidate"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 实现或真实资源未就绪时enabled=false；pendingReason面向页面解释不可用，不暴露密钥/厂商内部字段。
         * @example {
         *       "featureCode": "FACE_SEARCH",
         *       "enabled": false,
         *       "pendingReason": "P-007：合成示例部署尚未完成实现/真实资源绑定"
         *     }
         */
        FeatureAvailability: {
            enabled: boolean;
            /** @enum {string} */
            featureCode: "FACE_SEARCH" | "DECRYPTION" | "COMPANION_ANALYSIS" | "COLLISION_ANALYSIS" | "REGION_STATISTICS" | "PROFILE_ASSOCIATION" | "RESIDENCE_MODEL" | "WATCH_ACTIVATION" | "NOTIFICATION_SEND" | "DEVICE_COMMAND" | "REPORT_GENERATION" | "RAW_INGESTION";
            pendingReason: string | null;
        };
        /**
         * @description 接口模型 FenceCaptureRecord；具体业务范围以使用它的操作为准。；targetType必返，表示载荷主目标类型；FENCE须IMSI或IMEI，其它分别MAC/PLATE/FACE。targetId=null表示尚未解析代理ID，不能直接研判，主类型仍返回。
         * @example {
         *       "captureId": "00000000-0000-4000-8000-000000000001",
         *       "dataType": "FENCE",
         *       "targetId": null,
         *       "deviceId": null,
         *       "siteId": null,
         *       "captureTime": "2026-10-07T02:00:00Z",
         *       "ingestedAt": "2026-10-07T02:00:00Z",
         *       "longitude": null,
         *       "latitude": null,
         *       "source": "mock-example",
         *       "status": "VALID",
         *       "displayTarget": "mock-example",
         *       "isMasked": true,
         *       "fence": {
         *         "imsiMasked": null,
         *         "imeiMasked": null
         *       },
         *       "targetType": "IMSI"
         *     }
         */
        FenceCaptureRecord: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            captureId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            captureTime: string;
            /**
             * @description 判别字段固定为 FENCE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "FENCE";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            fence: components["schemas"]["FenceRecord"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            ingestedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            isMasked: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            latitude: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            longitude: number | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            status: components["schemas"]["CaptureStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            targetId: string | null;
            targetType: components["schemas"]["TargetType"];
        };
        /**
         * @description 供 Context 保存的不可变业务筛选条件；不包含本次分页游标。；targetIds同组为OR，与其它条件为AND；禁止把脱敏displayTarget作精确条件。
         * @example {
         *       "dataType": "FENCE",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        FenceCaptureSearchFilter: {
            /**
             * @description 判别字段固定为 FENCE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "FENCE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds?: string[];
            /** @description 围栏精确条件，可与 imsi 组合。 */
            imei?: string;
            /** @description 围栏精确条件，仅请求体。 */
            imsi?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetIds?: string[];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description IMSI 和 IMEI 可组合检索；空条件的最大时间窗由服务端策略校验。；targetIds同组为OR，与其它条件为AND；禁止把脱敏displayTarget作精确条件。
         * @example {
         *       "dataType": "FENCE",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        FenceCaptureSearchRequest: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cursor?: string;
            /**
             * @description 判别字段固定为 FENCE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "FENCE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds?: string[];
            /** @description 围栏精确条件，可与 imsi 组合。 */
            imei?: string;
            /** @description 围栏精确条件，仅请求体。 */
            imsi?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 20
             */
            pageSize?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default captureTime
             * @enum {string}
             */
            sortBy?: "captureTime" | "captureId";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default DESC
             * @enum {string}
             */
            sortOrder?: "ASC" | "DESC";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetIds?: string[];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 接口模型 FenceRecord；具体业务范围以使用它的操作为准。
         * @example {
         *       "imsiMasked": null,
         *       "imeiMasked": null
         *     }
         */
        FenceRecord: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            carrier?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            imeiMasked: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            imsiMasked: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            radioType?: string | null;
        };
        /**
         * @description 接口模型 FieldError；具体业务范围以使用它的操作为准。
         * @example {
         *       "field": "mock-example",
         *       "reason": "mock-example"
         *     }
         */
        FieldError: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            field: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 接口模型 FileAsset；具体业务范围以使用它的操作为准。
         * @example {
         *       "fileAssetId": "00000000-0000-4000-8000-000000000001",
         *       "category": "FACE_SEARCH",
         *       "fileName": "mock-example",
         *       "mimeType": "mock-example",
         *       "byteSize": 0,
         *       "status": "PROCESSING",
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "expiresAt": null
         *     }
         */
        FileAsset: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            byteSize: number;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            category: "FACE_SEARCH" | "DATASET_IMPORT" | "DEVICE_IMPORT" | "PERSON_LIBRARY_IMPORT" | "PROFILE_PORTRAIT" | "REPORT" | "EXPORT" | "CAPTURE_IMAGE" | "RAW_INGESTION";
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            errorCode?: components["schemas"]["ErrorCode"];
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            expiresAt: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            fileAssetId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            fileName: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            mimeType: string;
            status: components["schemas"]["FileStatus"];
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example PROCESSING
         * @enum {string}
         */
        FileStatus: "PROCESSING" | "ACTIVE" | "QUARANTINED" | "DELETED";
        /**
         * @description 接口模型 FileUploadData；具体业务范围以使用它的操作为准。
         * @example {
         *       "fileAssetId": "00000000-0000-4000-8000-000000000001",
         *       "status": "PROCESSING"
         *     }
         */
        FileUploadData: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            fileAssetId: string;
            status: components["schemas"]["FileStatus"];
        };
        /**
         * @description 持久业务点 id 为 UUID 字符串；聚合 cluster 的 id 为本次查询内稳定的不透明字符串。
         * @example {
         *       "type": "Feature",
         *       "id": "mock-example",
         *       "geometry": {
         *         "type": "Point",
         *         "coordinates": [
         *           116.4,
         *           39.9
         *         ]
         *       },
         *       "properties": {
         *         "featureType": "CAPTURE"
         *       }
         *     }
         */
        GeoFeature: {
            geometry: components["schemas"]["GeoGeometry"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            id: string;
            properties: components["schemas"]["MapFeatureProperties"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            type: "Feature";
        };
        /**
         * @description WGS84 GeoJSON [lon,lat]；缺坐标用null，不造(0,0)。单次2000点、面2000顶点/1万平方千米、zoom3至20，超量聚合/瓦片，非法几何422。高德离线PC Web产品/授权未绑定不声称底图可用。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "type": "FeatureCollection",
         *       "features": [
         *         {
         *           "type": "Feature",
         *           "id": "mock-example",
         *           "geometry": {
         *             "type": "Point",
         *             "coordinates": [
         *               116.4,
         *               39.9
         *             ]
         *           },
         *           "properties": {
         *             "featureType": "CAPTURE"
         *           }
         *         }
         *       ],
         *       "aggregated": true,
         *       "truncated": true,
         *       "maxFeatures": 1,
         *       "asOf": "2026-10-07T02:00:00Z"
         *     }
         */
        GeoFeatureCollection: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            aggregated: boolean;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            features: components["schemas"]["GeoFeature"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            maxFeatures: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            truncated: boolean;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            type: "FeatureCollection";
        };
        /**
         * @description GeoJSON geometry；WGS84/EPSG:4326。
         * @example {
         *       "type": "Point",
         *       "coordinates": [
         *         116.4,
         *         39.9
         *       ]
         *     }
         */
        GeoGeometry: components["schemas"]["GeoPoint"] | components["schemas"]["GeoLineString"] | components["schemas"]["GeoPolygon"] | components["schemas"]["GeoMultiPolygon"];
        /**
         * @description 接口模型 GeoLineString；具体业务范围以使用它的操作为准。
         * @example {
         *       "type": "LineString",
         *       "coordinates": [
         *         [
         *           116.4,
         *           39.9
         *         ],
         *         [
         *           116.41,
         *           39.91
         *         ]
         *       ]
         *     }
         */
        GeoLineString: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            coordinates: number[][];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            type: "LineString";
        };
        /**
         * @description 接口模型 GeoMultiPolygon；具体业务范围以使用它的操作为准。
         * @example {
         *       "type": "MultiPolygon",
         *       "coordinates": [
         *         [
         *           [
         *             [
         *               116.38,
         *               39.88
         *             ],
         *             [
         *               116.42,
         *               39.88
         *             ],
         *             [
         *               116.42,
         *               39.92
         *             ],
         *             [
         *               116.38,
         *               39.92
         *             ],
         *             [
         *               116.38,
         *               39.88
         *             ]
         *           ]
         *         ]
         *       ]
         *     }
         */
        GeoMultiPolygon: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            coordinates: number[][][][];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            type: "MultiPolygon";
        };
        /**
         * @description WGS84/EPSG:4326 [lon,lat].
         * @example {
         *       "type": "Point",
         *       "coordinates": [
         *         116.4,
         *         39.9
         *       ]
         *     }
         */
        GeoPoint: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            coordinates: number[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            type: "Point";
        };
        /**
         * @description 每个 ring 必须闭合；顶点数和面积由服务端校验。
         * @example {
         *       "type": "Polygon",
         *       "coordinates": [
         *         [
         *           [
         *             116.38,
         *             39.88
         *           ],
         *           [
         *             116.42,
         *             39.88
         *           ],
         *           [
         *             116.42,
         *             39.92
         *           ],
         *           [
         *             116.38,
         *             39.92
         *           ],
         *           [
         *             116.38,
         *             39.88
         *           ]
         *         ]
         *       ]
         *     }
         */
        GeoPolygon: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            coordinates: number[][][];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            type: "Polygon";
        };
        /** @description 接口模型 GetAlertResponse；具体业务范围以使用它的操作为准。 */
        GetAlertResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AlertDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetAnalysisResultResponse；具体业务范围以使用它的操作为准。 */
        GetAnalysisResultResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AnalysisResultItem"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetAnalysisTaskResponse；具体业务范围以使用它的操作为准。 */
        GetAnalysisTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AnalysisTaskDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetAuditEventResponse；具体业务范围以使用它的操作为准。 */
        GetAuditEventResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AuditEvent"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetCaptureResponse；具体业务范围以使用它的操作为准。 */
        GetCaptureResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["CaptureDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetCaptureStatisticsResponse；具体业务范围以使用它的操作为准。 */
        GetCaptureStatisticsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["CaptureStatisticsData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetClientConfigResponse；具体业务范围以使用它的操作为准。 */
        GetClientConfigResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ClientConfig"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetContextResponse；具体业务范围以使用它的操作为准。 */
        GetContextResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AnalysisContext"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetCsrfTokenResponse；具体业务范围以使用它的操作为准。 */
        GetCsrfTokenResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["CsrfTokenData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDashboardSummaryResponse；具体业务范围以使用它的操作为准。 */
        GetDashboardSummaryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DashboardSummary"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDatasetResponse；具体业务范围以使用它的操作为准。 */
        GetDatasetResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DatasetDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDecryptionRequestResponse；具体业务范围以使用它的操作为准。 */
        GetDecryptionRequestResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DecryptionRequestDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDeliveryResponse；具体业务范围以使用它的操作为准。 */
        GetDeliveryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["Delivery"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDeviceCaptureStatisticsResponse；具体业务范围以使用它的操作为准。 */
        GetDeviceCaptureStatisticsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceCaptureStatistics"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDeviceCommandResponse；具体业务范围以使用它的操作为准。 */
        GetDeviceCommandResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceCommandTask"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDeviceGroupResponse；具体业务范围以使用它的操作为准。 */
        GetDeviceGroupResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceGroupDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDeviceMapFeaturesResponse；具体业务范围以使用它的操作为准。 */
        GetDeviceMapFeaturesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["GeoFeatureCollection"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDeviceParametersResponse；具体业务范围以使用它的操作为准。 */
        GetDeviceParametersResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceParameters"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDeviceResponse；具体业务范围以使用它的操作为准。 */
        GetDeviceResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDeviceStatusResponse；具体业务范围以使用它的操作为准。 */
        GetDeviceStatusResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceStatusData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetDictionaryResponse；具体业务范围以使用它的操作为准。 */
        GetDictionaryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DictionaryData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetFileAssetResponse；具体业务范围以使用它的操作为准。 */
        GetFileAssetResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["FileAsset"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetHealthResponse；具体业务范围以使用它的操作为准。 */
        GetHealthResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["HealthData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 导入结果响应。 */
        GetImportTaskResultResponse: {
            /** @enum {string} */
            code: "0";
            data: components["schemas"]["ImportTaskResult"];
            message: string;
            traceId: string;
        };
        GetImportTemplateResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ImportTemplate"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetIngestionBatchResponse；具体业务范围以使用它的操作为准。 */
        GetIngestionBatchResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["IngestionBatch"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetIngestionQualitySummaryResponse；具体业务范围以使用它的操作为准。 */
        GetIngestionQualitySummaryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["IngestionQualitySummary"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetMapAreaSummaryResponse；具体业务范围以使用它的操作为准。 */
        GetMapAreaSummaryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AreaSummary"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetMapFeaturesResponse；具体业务范围以使用它的操作为准。 */
        GetMapFeaturesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["GeoFeatureCollection"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetMapHeatmapResponse；具体业务范围以使用它的操作为准。 */
        GetMapHeatmapResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["HeatmapData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetMeResponse；具体业务范围以使用它的操作为准。 */
        GetMeResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["CurrentUserData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetModelParameterVersionResponse；具体业务范围以使用它的操作为准。 */
        GetModelParameterVersionResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ModelParameterVersion"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetNotificationTaskResponse；具体业务范围以使用它的操作为准。 */
        GetNotificationTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["NotificationTaskDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetOperationSummaryResponse；具体业务范围以使用它的操作为准。 */
        GetOperationSummaryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["OperationsSummary"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetPersonLibraryResponse；具体业务范围以使用它的操作为准。 */
        GetPersonLibraryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["PersonLibraryDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetProfileResponse；具体业务范围以使用它的操作为准。 */
        GetProfileResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ProfileDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetReadinessResponse；具体业务范围以使用它的操作为准。 */
        GetReadinessResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ReadinessData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetReportPreviewResponse；具体业务范围以使用它的操作为准。 */
        GetReportPreviewResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ReportPreviewData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetReportResponse；具体业务范围以使用它的操作为准。 */
        GetReportResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ReportDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetRoleResponse；具体业务范围以使用它的操作为准。 */
        GetRoleResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["RoleDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetSetTaskResponse；具体业务范围以使用它的操作为准。 */
        GetSetTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["SetTaskDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetSiteResponse；具体业务范围以使用它的操作为准。 */
        GetSiteResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["SiteDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetTaskResponse；具体业务范围以使用它的操作为准。 */
        GetTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["Task"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetTrendModelResponse；具体业务范围以使用它的操作为准。 */
        GetTrendModelResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TrendModel"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetTrendModelResultResponse；具体业务范围以使用它的操作为准。 */
        GetTrendModelResultResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ModelResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetUserResponse；具体业务范围以使用它的操作为准。 */
        GetUserResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["UserDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetWatchRuleResponse；具体业务范围以使用它的操作为准。 */
        GetWatchRuleResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["WatchRuleDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 GetWatchStatisticsResponse；具体业务范围以使用它的操作为准。 */
        GetWatchStatisticsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["WatchStatistics"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 HealthData；具体业务范围以使用它的操作为准。
         * @example {
         *       "status": "UP",
         *       "checkedAt": "2026-10-07T02:00:00Z"
         *     }
         */
        HealthData: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            checkedAt: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            status: "UP" | "DOWN";
        };
        /**
         * @description 接口模型 HeatCell；具体业务范围以使用它的操作为准。
         * @example {
         *       "cellId": "mock-example",
         *       "center": {
         *         "type": "Point",
         *         "coordinates": [
         *           116.4,
         *           39.9
         *         ]
         *       },
         *       "value": 0,
         *       "unit": "mock-example",
         *       "window": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        HeatCell: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cellId: string;
            center: components["schemas"]["GeoPoint"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            unit: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            value: number;
            window: components["schemas"]["TimeRange"];
        };
        /**
         * @description 接口模型 HeatmapData；具体业务范围以使用它的操作为准。
         * @example {
         *       "cells": [
         *         {
         *           "cellId": "mock-example",
         *           "center": {
         *             "type": "Point",
         *             "coordinates": [
         *               116.4,
         *               39.9
         *             ]
         *           },
         *           "value": 0,
         *           "unit": "mock-example",
         *           "window": {
         *             "from": "2026-10-07T02:00:00Z",
         *             "to": "2026-10-07T03:00:00Z"
         *           }
         *         }
         *       ],
         *       "asOf": "2026-10-07T02:00:00Z",
         *       "aggregated": true
         *     }
         */
        HeatmapData: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            aggregated: boolean;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cells: components["schemas"]["HeatCell"][];
        };
        /**
         * @example {
         *       "name": "mock-example",
         *       "label": "mock-example",
         *       "required": true,
         *       "type": "STRING",
         *       "description": "mock-example"
         *     }
         */
        ImportColumn: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            required: boolean;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            type: "STRING" | "UUID" | "INTEGER" | "BOOLEAN" | "DATE_TIME";
        };
        /** @description 接口模型 ImportDatasetItemsResponse；具体业务范围以使用它的操作为准。 */
        ImportDatasetItemsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ImportDevicesResponse；具体业务范围以使用它的操作为准。 */
        ImportDevicesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ImportPersonLibrariesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ImportPersonLibraryMembersResponse；具体业务范围以使用它的操作为准。 */
        ImportPersonLibraryMembersResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 成功行的领域ID。人员必须libraryId/memberId/profileId；设备deviceId；数据集datasetId/itemId；SKIPPED返回对应既有资源ID。
         * @example {
         *       "rowNumber": 1,
         *       "status": "CREATED"
         *     }
         */
        ImportResultItem: {
            /** Format: uuid */
            datasetId?: string;
            /** Format: uuid */
            deviceId?: string;
            /** Format: uuid */
            itemId?: string;
            /** Format: uuid */
            libraryId?: string;
            /** Format: uuid */
            memberId?: string;
            /** Format: uuid */
            profileId?: string;
            rowNumber: number;
            /** @enum {string} */
            status: "CREATED" | "UPDATED" | "SKIPPED";
        };
        /**
         * @description 整个任务成功快照；行结果cursor分页，汇总不限当前页；失败查看task/errors。
         * @example {
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "taskType": "DATASET_IMPORT",
         *       "importedCount": 0,
         *       "skippedCount": 0,
         *       "items": [
         *         {
         *           "rowNumber": 1,
         *           "status": "CREATED"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        ImportTaskResult: {
            importedCount: number;
            items: components["schemas"]["ImportResultItem"][];
            nextCursor: string | null;
            pageSize: number;
            skippedCount: number;
            /** Format: uuid */
            taskId: string;
            /** @enum {string} */
            taskType: "DATASET_IMPORT" | "PERSON_LIBRARY_IMPORT" | "DEVICE_IMPORT";
        };
        /**
         * @description XLSX第一工作表首行英文列名；CSV带/不带BOM的UTF8与RFC4180；JSON对象数组。非法行使整个业务导入回滚，任务错误分页返回行号。所有模板实际列定义见api-conventions.md。
         * @example {
         *       "templateCode": "DATASET_ITEMS_V1",
         *       "version": "1",
         *       "columns": [
         *         {
         *           "name": "mock-example",
         *           "label": "mock-example",
         *           "required": true,
         *           "type": "STRING",
         *           "description": "mock-example"
         *         }
         *       ],
         *       "formats": [
         *         "XLSX"
         *       ],
         *       "transactionPolicy": "ALL_OR_NOTHING",
         *       "encoding": "UTF-8",
         *       "description": "mock-example"
         *     }
         */
        ImportTemplate: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            columns: components["schemas"]["ImportColumn"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            encoding: "UTF-8";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            formats: ("XLSX" | "CSV" | "JSON")[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            templateCode: "DATASET_ITEMS_V1" | "DEVICES_V1" | "PERSON_LIBRARIES_V1";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            transactionPolicy: "ALL_OR_NOTHING";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            version: "1";
        };
        /**
         * @description 接口模型 IngestionBatch；具体业务范围以使用它的操作为准。
         * @example {
         *       "batchId": "00000000-0000-4000-8000-000000000001",
         *       "source": "mock-example",
         *       "mappingVersion": "mock-example",
         *       "dedupVersion": "mock-example",
         *       "status": "PENDING",
         *       "acceptedCount": 0,
         *       "rejectedCount": 0,
         *       "duplicateCount": 0,
         *       "createdAt": "2026-10-07T02:00:00Z"
         *     }
         */
        IngestionBatch: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            acceptedCount: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            batchId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dedupVersion: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            duplicateCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            errorSummary?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            finishedAt?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            mappingVersion: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            rejectedCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            sourceFileId?: string;
            status: components["schemas"]["IngestionStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId?: string;
        };
        /**
         * @description 接口模型 IngestionBatchCreated；具体业务范围以使用它的操作为准。
         * @example {
         *       "batchId": "00000000-0000-4000-8000-000000000001",
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "status": "PENDING",
         *       "createdAt": "2026-10-07T02:00:00Z"
         *     }
         */
        IngestionBatchCreated: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            batchId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            status: components["schemas"]["TaskStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId: string;
        };
        /**
         * @description 设备协议/源字段映射由适配器管理；仅 ACTIVE 文件可提交。
         * @example {
         *       "source": "mock-example",
         *       "sourceFileId": "00000000-0000-4000-8000-000000000001",
         *       "mappingVersion": "mock-example",
         *       "dedupVersion": "mock-example"
         *     }
         */
        IngestionBatchInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dedupVersion: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            mappingVersion: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            sourceFileId: string;
        };
        /**
         * @description 接口模型 IngestionBatchPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "batchId": "00000000-0000-4000-8000-000000000001",
         *           "source": "mock-example",
         *           "mappingVersion": "mock-example",
         *           "dedupVersion": "mock-example",
         *           "status": "PENDING",
         *           "acceptedCount": 0,
         *           "rejectedCount": 0,
         *           "duplicateCount": 0,
         *           "createdAt": "2026-10-07T02:00:00Z"
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        IngestionBatchPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["IngestionBatch"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 IngestionError；具体业务范围以使用它的操作为准。
         * @example {
         *       "rowNumber": 1,
         *       "code": "VALIDATION_ERROR",
         *       "reason": "mock-example"
         *     }
         */
        IngestionError: {
            code: components["schemas"]["ErrorCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            field?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            rowNumber: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            sourceRecordKey?: string;
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "rowNumber": 1,
         *           "code": "VALIDATION_ERROR",
         *           "reason": "mock-example"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        IngestionErrorPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["IngestionError"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 接口模型 IngestionQualitySummary；具体业务范围以使用它的操作为准。
         * @example {
         *       "asOf": "2026-10-07T02:00:00Z",
         *       "acceptedCount": 0,
         *       "rejectedCount": 0,
         *       "duplicateCount": 0,
         *       "unmappedDeviceCount": 0,
         *       "invalidCoordinateCount": 0
         *     }
         */
        IngestionQualitySummary: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            acceptedCount: number;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            duplicateCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            invalidCoordinateCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            rejectedCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            unmappedDeviceCount: number;
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example PENDING
         * @enum {string}
         */
        IngestionStatus: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "CANCELLED";
        /**
         * @description 接口模型 IntegerDeviceParameter；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "label": "mock-example",
         *       "valueType": "INTEGER",
         *       "integerValue": 0,
         *       "readOnly": true,
         *       "updatedAt": "2026-10-07T02:00:00Z"
         *     }
         */
        IntegerDeviceParameter: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            integerValue: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            readOnly: boolean;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /**
             * @description 判别字段固定为 INTEGER。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "INTEGER";
        };
        /**
         * @description 接口模型 IntegerDeviceParameterChange；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "INTEGER",
         *       "integerValue": 0
         *     }
         */
        IntegerDeviceParameterChange: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            integerValue: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /**
             * @description 判别字段固定为 INTEGER。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "INTEGER";
        };
        /**
         * @description 接口模型 IntegerModelParameterValue；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "INTEGER",
         *       "integerValue": 0
         *     }
         */
        IntegerModelParameterValue: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            integerValue: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /**
             * @description 判别字段固定为 INTEGER。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "INTEGER";
        };
        /** @description 接口模型 ListAlertsResponse；具体业务范围以使用它的操作为准。 */
        ListAlertsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AlertPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListAnalysisResultsResponse；具体业务范围以使用它的操作为准。 */
        ListAnalysisResultsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AnalysisResultPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListAnalysisTasksResponse；具体业务范围以使用它的操作为准。 */
        ListAnalysisTasksResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AnalysisTaskPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListDatasetItemsResponse；具体业务范围以使用它的操作为准。 */
        ListDatasetItemsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DatasetItemPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListDatasetsResponse；具体业务范围以使用它的操作为准。 */
        ListDatasetsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DatasetPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListDatasetVersionsResponse；具体业务范围以使用它的操作为准。 */
        ListDatasetVersionsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DatasetVersionPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListDeliveriesResponse；具体业务范围以使用它的操作为准。 */
        ListDeliveriesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeliveryPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListDeviceCommandResultsResponse；具体业务范围以使用它的操作为准。 */
        ListDeviceCommandResultsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceCommandResultPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListDeviceCommandsResponse；具体业务范围以使用它的操作为准。 */
        ListDeviceCommandsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceCommandTaskPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListDeviceGroupMembersResponse；具体业务范围以使用它的操作为准。 */
        ListDeviceGroupMembersResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceGroupMemberPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListDeviceGroupsResponse；具体业务范围以使用它的操作为准。 */
        ListDeviceGroupsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceGroupPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListDevicesResponse；具体业务范围以使用它的操作为准。 */
        ListDevicesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DevicePage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListDeviceStatusHistoryResponse；具体业务范围以使用它的操作为准。 */
        ListDeviceStatusHistoryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceStatusEventPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListFaceSearchResultsResponse；具体业务范围以使用它的操作为准。 */
        ListFaceSearchResultsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["FaceSearchResultPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListIngestionBatchesResponse；具体业务范围以使用它的操作为准。 */
        ListIngestionBatchesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["IngestionBatchPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListIngestionErrorsResponse；具体业务范围以使用它的操作为准。 */
        ListIngestionErrorsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["IngestionErrorPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ListMenuCatalogResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["MenuCatalog"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListModelParameterVersionsResponse；具体业务范围以使用它的操作为准。 */
        ListModelParameterVersionsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ModelParameterVersionPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListModelTagsResponse；具体业务范围以使用它的操作为准。 */
        ListModelTagsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ModelTagList"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListNotificationTasksResponse；具体业务范围以使用它的操作为准。 */
        ListNotificationTasksResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["NotificationTaskPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ListNotificationTemplatesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["NotificationTemplateList"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ListPermissionCatalogResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["PermissionCatalog"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListPersonLibrariesResponse；具体业务范围以使用它的操作为准。 */
        ListPersonLibrariesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["PersonLibraryPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListPersonLibraryMembersResponse；具体业务范围以使用它的操作为准。 */
        ListPersonLibraryMembersResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["PersonLibraryMemberPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ListPlatformMessagesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["PlatformMessagePage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ListProfileConflictsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ProfileConflictPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListProfileEvidenceResponse；具体业务范围以使用它的操作为准。 */
        ListProfileEvidenceResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ProfileEvidencePage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListProfileHistoryResponse；具体业务范围以使用它的操作为准。 */
        ListProfileHistoryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ProfileRevisionPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListProfileTargetsResponse；具体业务范围以使用它的操作为准。 */
        ListProfileTargetsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ProfileTargetPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListProfileTrajectoriesResponse；具体业务范围以使用它的操作为准。 */
        ListProfileTrajectoriesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TrajectoryPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ListRegionsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["RegionCatalog"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListReportsResponse；具体业务范围以使用它的操作为准。 */
        ListReportsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ReportPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListRolesResponse；具体业务范围以使用它的操作为准。 */
        ListRolesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["RolePage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListSetTaskResultsResponse；具体业务范围以使用它的操作为准。 */
        ListSetTaskResultsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DatasetItemPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListSetTasksResponse；具体业务范围以使用它的操作为准。 */
        ListSetTasksResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["SetTaskPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListSitesResponse；具体业务范围以使用它的操作为准。 */
        ListSitesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["SitePage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListTaskAttemptsResponse；具体业务范围以使用它的操作为准。 */
        ListTaskAttemptsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskAttemptsData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListTaskRowErrorsResponse；具体业务范围以使用它的操作为准。 */
        ListTaskRowErrorsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskRowErrorPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListTasksResponse；具体业务范围以使用它的操作为准。 */
        ListTasksResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListTrendModelResultsResponse；具体业务范围以使用它的操作为准。 */
        ListTrendModelResultsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ModelResultPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListTrendModelsResponse；具体业务范围以使用它的操作为准。 */
        ListTrendModelsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TrendModelPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListUsersResponse；具体业务范围以使用它的操作为准。 */
        ListUsersResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["UserPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ListWatchRulesResponse；具体业务范围以使用它的操作为准。 */
        ListWatchRulesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["WatchRulePage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 LoginRequest；具体业务范围以使用它的操作为准。
         * @example {
         *       "loginName": "mock-example",
         *       "password": "Example-password-2026!"
         *     }
         */
        LoginRequest: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            loginName: string;
            /**
             * Format: password
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            password: string;
        };
        /** @description 接口模型 LoginResponse；具体业务范围以使用它的操作为准。 */
        LoginResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["SessionData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 LogoutResponse；具体业务范围以使用它的操作为准。 */
        LogoutResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 MacCaptureRecord；具体业务范围以使用它的操作为准。；targetType必返，表示载荷主目标类型；FENCE须IMSI或IMEI，其它分别MAC/PLATE/FACE。targetId=null表示尚未解析代理ID，不能直接研判，主类型仍返回。
         * @example {
         *       "captureId": "00000000-0000-4000-8000-000000000001",
         *       "dataType": "MAC",
         *       "targetId": null,
         *       "deviceId": null,
         *       "siteId": null,
         *       "captureTime": "2026-10-07T02:00:00Z",
         *       "ingestedAt": "2026-10-07T02:00:00Z",
         *       "longitude": null,
         *       "latitude": null,
         *       "source": "mock-example",
         *       "status": "VALID",
         *       "displayTarget": "mock-example",
         *       "isMasked": true,
         *       "mac": {
         *         "macMasked": "mock-example"
         *       },
         *       "targetType": "MAC"
         *     }
         */
        MacCaptureRecord: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            captureId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            captureTime: string;
            /**
             * @description 判别字段固定为 MAC。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "MAC";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            ingestedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            isMasked: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            latitude: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            longitude: number | null;
            mac: components["schemas"]["MacRecord"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            status: components["schemas"]["CaptureStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            targetId: string | null;
            targetType: components["schemas"]["TargetType"];
        };
        /**
         * @description 供 Context 保存的不可变业务筛选条件；不包含本次分页游标。；targetIds同组为OR，与其它条件为AND；禁止把脱敏displayTarget作精确条件。
         * @example {
         *       "dataType": "MAC",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        MacCaptureSearchFilter: {
            /**
             * @description 判别字段固定为 MAC。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "MAC";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            macAddress?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetIds?: string[];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description ；targetIds同组为OR，与其它条件为AND；禁止把脱敏displayTarget作精确条件。
         * @example {
         *       "dataType": "MAC",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        MacCaptureSearchRequest: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cursor?: string;
            /**
             * @description 判别字段固定为 MAC。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "MAC";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            macAddress?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 20
             */
            pageSize?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default captureTime
             * @enum {string}
             */
            sortBy?: "captureTime" | "captureId";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default DESC
             * @enum {string}
             */
            sortOrder?: "ASC" | "DESC";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetIds?: string[];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 接口模型 MacRecord；具体业务范围以使用它的操作为准。
         * @example {
         *       "macMasked": "mock-example"
         *     }
         */
        MacRecord: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            macMasked: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            wirelessType?: string | null;
        };
        /**
         * @description 接口模型 MapFeatureProperties；具体业务范围以使用它的操作为准。
         * @example {
         *       "featureType": "CAPTURE"
         *     }
         */
        MapFeatureProperties: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            byDataType?: components["schemas"]["MapFeaturePropertiesByDataTypeItem"][];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            capturedAt?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            captureId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            clusterId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            count?: number;
            dataType?: components["schemas"]["DataType"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceName?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayLabel?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            expansionZoom?: number;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            featureType: "CAPTURE" | "DEVICE" | "SITE" | "CLUSTER";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteName?: string;
            status?: components["schemas"]["DeviceStatus"];
            target?: components["schemas"]["ContextTarget"];
        };
        /**
         * @example {
         *       "dataType": "FENCE",
         *       "count": 0
         *     }
         */
        MapFeaturePropertiesByDataTypeItem: {
            count: number;
            dataType: components["schemas"]["DataType"];
        };
        /**
         * @description 接口模型 Menu；具体业务范围以使用它的操作为准。
         * @example {
         *       "menuId": "00000000-0000-4000-8000-000000000001",
         *       "code": "mock-example",
         *       "label": "mock-example",
         *       "route": "mock-example",
         *       "order": 0
         *     }
         */
        Menu: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            code: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            menuId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            order: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            parentMenuId?: string | null;
            requiredPermission?: components["schemas"]["PermissionCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            route: string;
        };
        /**
         * @example {
         *       "items": [
         *         {
         *           "menuId": "00000000-0000-4000-8000-000000000001",
         *           "code": "mock-example",
         *           "label": "mock-example",
         *           "route": "mock-example",
         *           "order": 0
         *         }
         *       ],
         *       "version": "mock-example"
         *     }
         */
        MenuCatalog: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["Menu"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: string;
        };
        MergeProfilesApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "mergeProfiles";
            requestBody: components["schemas"]["ProfileMergeInput"];
        };
        /** @description 接口模型 MergeProfilesResponse；具体业务范围以使用它的操作为准。 */
        MergeProfilesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 MetricCard；具体业务范围以使用它的操作为准。
         * @example {
         *       "meta": {
         *         "asOf": null,
         *         "status": "READY",
         *         "unit": "mock-example",
         *         "refreshPolicy": "POLL"
         *       },
         *       "value": null
         *     }
         */
        MetricCard: {
            meta: components["schemas"]["MetricMeta"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            value: number | null;
        };
        /**
         * @description DASHBOARD仅FENCE_RADIO_STATISTICS；OPERATIONS仅DEVICE_ANOMALIES/VOLUME_ANOMALIES且必须dataType/range。任务冻结指标口径和asOf。
         * @example {
         *       "source": "DASHBOARD",
         *       "metricCodes": [
         *         "FENCE_RADIO_STATISTICS"
         *       ],
         *       "format": "XLSX"
         *     }
         */
        MetricExportInput: {
            dataType?: components["schemas"]["DataType"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            metricCodes: ("FENCE_RADIO_STATISTICS" | "DEVICE_ANOMALIES" | "VOLUME_ANOMALIES")[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            range?: "WEEK" | "MONTH" | "QUARTER" | "YEAR";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            source: "DASHBOARD" | "OPERATIONS";
        };
        /**
         * @description 尚未计算或不可用时 asOf=null；单项失败不阻断其他指标。
         * @example {
         *       "asOf": null,
         *       "status": "READY",
         *       "unit": "mock-example",
         *       "refreshPolicy": "POLL"
         *     }
         */
        MetricMeta: {
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            asOf: string | null;
            errorCode?: components["schemas"]["ErrorCode"];
            refreshPolicy: components["schemas"]["RefreshPolicy"];
            status: components["schemas"]["MetricStatus"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            unit: string;
        };
        /**
         * @description 接口模型 MetricPoint；具体业务范围以使用它的操作为准。
         * @example {
         *       "at": "2026-10-07T02:00:00Z",
         *       "value": null
         *     }
         */
        MetricPoint: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            at: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            value: number | null;
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example READY
         * @enum {string}
         */
        MetricStatus: "READY" | "STALE" | "UNAVAILABLE";
        /**
         * @description 接口模型 ModelEffectiveDataFilter；具体业务范围以使用它的操作为准。
         * @example {
         *       "dataTypes": [
         *         "FENCE"
         *       ],
         *       "captureStatuses": [
         *         "VALID"
         *       ]
         *     }
         */
        ModelEffectiveDataFilter: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureStatuses: components["schemas"]["CaptureStatus"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dataTypes: components["schemas"]["DataType"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            timeRange?: components["schemas"]["TimeRange"];
        };
        /**
         * @example {
         *       "parameterCode": "mock-example",
         *       "label": "mock-example",
         *       "valueType": "TEXT",
         *       "required": true,
         *       "description": "mock-example"
         *     }
         */
        ModelParameterDescriptor: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            maximum?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            minimum?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            required: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            unit?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            valueType: "TEXT" | "INTEGER" | "NUMBER" | "BOOLEAN" | "DURATION_SECONDS" | "DISTANCE_METERS";
        };
        /**
         * @description 强类型/单位与参数目录校验，引用后不可变。暂定RESIDENCE参数见policy；变更产生新版本，不复用任务已引用版本。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "TEXT",
         *       "textValue": "mock-example"
         *     }
         */
        ModelParameterValue: components["schemas"]["TextModelParameterValue"] | components["schemas"]["IntegerModelParameterValue"] | components["schemas"]["NumberModelParameterValue"] | components["schemas"]["BooleanModelParameterValue"] | components["schemas"]["DurationSecondsModelParameterValue"] | components["schemas"]["DistanceMetersModelParameterValue"];
        /**
         * @description 接口模型 ModelParameterVersion；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterVersionId": "00000000-0000-4000-8000-000000000001",
         *       "modelId": "00000000-0000-4000-8000-000000000001",
         *       "version": 1,
         *       "parameters": [
         *         {
         *           "parameterCode": "mock-example",
         *           "valueType": "TEXT",
         *           "textValue": "mock-example"
         *         }
         *       ],
         *       "matchingRules": [
         *         {
         *           "ruleCode": "mock-example",
         *           "label": "mock-example",
         *           "algorithmVersion": "mock-example",
         *           "outputTagCodes": [
         *             "mock-example"
         *           ],
         *           "description": "mock-example"
         *         }
         *       ],
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "createdByUserId": "00000000-0000-4000-8000-000000000001",
         *       "effectiveAt": "2026-10-07T02:00:00Z"
         *     }
         */
        ModelParameterVersion: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            createdByUserId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            effectiveAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            matchingRules: components["schemas"]["ModelRuleDescriptor"][];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            modelId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameters: components["schemas"]["ModelParameterValue"][];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            parameterVersionId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 ModelParameterVersionInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameters": [
         *         {
         *           "parameterCode": "mock-example",
         *           "valueType": "TEXT",
         *           "textValue": "mock-example"
         *         }
         *       ],
         *       "matchingRules": [
         *         {
         *           "ruleCode": "mock-example",
         *           "label": "mock-example",
         *           "algorithmVersion": "mock-example",
         *           "outputTagCodes": [
         *             "mock-example"
         *           ],
         *           "description": "mock-example"
         *         }
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        ModelParameterVersionInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            matchingRules: components["schemas"]["ModelRuleDescriptor"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameters: components["schemas"]["ModelParameterValue"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 接口模型 ModelParameterVersionPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "parameterVersionId": "00000000-0000-4000-8000-000000000001",
         *           "modelId": "00000000-0000-4000-8000-000000000001",
         *           "version": 1,
         *           "parameters": [
         *             {
         *               "parameterCode": "mock-example",
         *               "valueType": "TEXT",
         *               "textValue": "mock-example"
         *             }
         *           ],
         *           "matchingRules": [
         *             {
         *               "ruleCode": "mock-example",
         *               "label": "mock-example",
         *               "algorithmVersion": "mock-example",
         *               "outputTagCodes": [
         *                 "mock-example"
         *               ],
         *               "description": "mock-example"
         *             }
         *           ],
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "createdByUserId": "00000000-0000-4000-8000-000000000001",
         *           "effectiveAt": "2026-10-07T02:00:00Z"
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        ModelParameterVersionPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["ModelParameterVersion"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 ModelResult；具体业务范围以使用它的操作为准。
         * @example {
         *       "resultId": "00000000-0000-4000-8000-000000000001",
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "modelId": "00000000-0000-4000-8000-000000000001",
         *       "parameterVersionId": "00000000-0000-4000-8000-000000000001",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "tagCodes": [
         *         "mock-example"
         *       ],
         *       "evidenceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "algorithmVersion": "mock-example",
         *       "generatedAt": "2026-10-07T02:00:00Z",
         *       "reviewStatus": "UNREVIEWED",
         *       "displayTarget": "mock-example",
         *       "captureIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "dataType": "FENCE"
         *     }
         */
        ModelResult: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            algorithmVersion: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            confidence?: number | null;
            dataType: components["schemas"]["DataType"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceIds: string[];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            generatedAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            modelId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            parameterVersionId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resultId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            reviewStatus: "UNREVIEWED" | "CONFIRMED" | "REJECTED";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            tagCodes: string[];
            target: components["schemas"]["ContextTarget"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId: string;
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "resultId": "00000000-0000-4000-8000-000000000001",
         *           "taskId": "00000000-0000-4000-8000-000000000001",
         *           "modelId": "00000000-0000-4000-8000-000000000001",
         *           "parameterVersionId": "00000000-0000-4000-8000-000000000001",
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "tagCodes": [
         *             "mock-example"
         *           ],
         *           "evidenceIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "algorithmVersion": "mock-example",
         *           "generatedAt": "2026-10-07T02:00:00Z",
         *           "reviewStatus": "UNREVIEWED",
         *           "displayTarget": "mock-example",
         *           "captureIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "dataType": "FENCE"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        ModelResultPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["ModelResult"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 仅RESIDENCE_SITE_NIGHTS_V1元数据，指向受控实现而非自由DSL。一个自然月输入、22至06时、>=10记录/>=5夜/夜晚比例>=0.6，标签仅疑似场所候选；同分按夜数/最近时刻/siteId稳定排序。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "ruleCode": "mock-example",
         *       "label": "mock-example",
         *       "algorithmVersion": "mock-example",
         *       "outputTagCodes": [
         *         "mock-example"
         *       ],
         *       "description": "mock-example"
         *     }
         */
        ModelRuleDescriptor: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            algorithmVersion: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            outputTagCodes: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            ruleCode: string;
        };
        /**
         * @description 接口模型 ModelRunInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "parameterVersionId": "00000000-0000-4000-8000-000000000001",
         *       "reason": "mock-example"
         *     }
         */
        ModelRunInput: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            parameterVersionId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 接口模型 ModelTag；具体业务范围以使用它的操作为准。
         * @example {
         *       "tagCode": "mock-example",
         *       "label": "mock-example",
         *       "enabled": true
         *     }
         */
        ModelTag: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            enabled: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            tagCode: string;
        };
        /**
         * @description 接口模型 ModelTagList；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "tagCode": "mock-example",
         *           "label": "mock-example",
         *           "enabled": true
         *         }
         *       ]
         *     }
         */
        ModelTagList: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["ModelTag"][];
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example SMS
         * @enum {string}
         */
        NotificationChannel: "SMS" | "EMAIL" | "PLATFORM";
        /**
         * @description 周期计划与单次TaskStatus分开；ONCE执行完成为COMPLETED；DAILY保持ACTIVE，取消后不产生下一次执行。
         * @example ACTIVE
         * @enum {string}
         */
        NotificationPlanStatus: "ACTIVE" | "CANCELLED" | "COMPLETED";
        /**
         * @example {
         *       "channel": "SMS",
         *       "recipientMasked": "mock-example",
         *       "recipientId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        NotificationRecipientSummary: {
            channel: components["schemas"]["NotificationChannel"];
            /** Format: uuid */
            recipientId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            recipientMasked: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            userId?: string;
        };
        /**
         * @description 接口模型 NotificationSchedule；具体业务范围以使用它的操作为准。
         * @example {
         *       "scheduleType": "ONCE",
         *       "scheduleAt": "2026-10-07T02:00:00Z"
         *     }
         */
        NotificationSchedule: components["schemas"]["OneTimeNotificationSchedule"] | components["schemas"]["DailyNotificationSchedule"];
        /**
         * @description 按channel判别的强类型联合。
         * @example {
         *       "channel": "SMS",
         *       "address": "+8613800000000"
         *     }
         */
        NotificationTarget: components["schemas"]["SmsNotificationTarget"] | components["schemas"]["EmailNotificationTarget"] | components["schemas"]["PlatformNotificationTarget"] | components["schemas"]["RetainedNotificationTarget"];
        /**
         * @description 接口模型 NotificationTaskCreated；具体业务范围以使用它的操作为准。
         * @example {
         *       "notificationTaskId": "00000000-0000-4000-8000-000000000001",
         *       "status": "ACTIVE",
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "version": 0
         *     }
         */
        NotificationTaskCreated: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            nextRunAt?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            notificationTaskId: string;
            status: components["schemas"]["NotificationPlanStatus"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 NotificationTaskDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "notificationTaskId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "status": "ACTIVE",
         *       "templateCode": "mock-example",
         *       "targets": [
         *         {
         *           "channel": "SMS",
         *           "recipientMasked": "mock-example",
         *           "recipientId": "00000000-0000-4000-8000-000000000001"
         *         }
         *       ],
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "deliveryCount": 0,
         *       "schedule": {
         *         "scheduleType": "ONCE",
         *         "scheduleAt": "2026-10-07T02:00:00Z"
         *       },
         *       "platformUserIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "version": 0,
         *       "lastSentAt": null,
         *       "nextRunAt": null
         *     }
         */
        NotificationTaskDetail: {
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            cancelledAt?: string | null;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deliveryCount: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            lastExecutionTaskId?: string;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            lastSentAt: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            nextRunAt: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            notificationTaskId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            platformUserIds: string[];
            schedule: components["schemas"]["NotificationSchedule"];
            status: components["schemas"]["NotificationPlanStatus"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targets: components["schemas"]["NotificationRecipientSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            templateCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 模板code+version与变量白名单冻结；单任务最多100收件人。DAILY错过<=1小时补一次，超过记MISSED并告警；编辑/撤销只影响未启动外部动作。三类通知均属本期，外部资源未绑定不返回假成功。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "name": "设备状态日通知",
         *       "targets": [
         *         {
         *           "channel": "SMS",
         *           "address": "+8613800000000"
         *         }
         *       ],
         *       "schedule": {
         *         "scheduleType": "DAILY",
         *         "localTime": "10:00",
         *         "timeZone": "Asia/Shanghai",
         *         "startDate": "2026-10-07"
         *       },
         *       "templateCode": "DEVICE_STATUS_V1",
         *       "platformUserIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ]
         *     }
         */
        NotificationTaskInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            platformUserIds: string[];
            schedule: components["schemas"]["NotificationSchedule"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targets: components["schemas"]["NotificationTarget"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            templateCode: string;
        };
        /**
         * @description 接口模型 NotificationTaskPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "notificationTaskId": "00000000-0000-4000-8000-000000000001",
         *           "name": "mock-example",
         *           "status": "ACTIVE",
         *           "templateCode": "mock-example",
         *           "targets": [
         *             {
         *               "channel": "SMS",
         *               "recipientMasked": "mock-example",
         *               "recipientId": "00000000-0000-4000-8000-000000000001"
         *             }
         *           ],
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "deliveryCount": 0,
         *           "schedule": {
         *             "scheduleType": "ONCE",
         *             "scheduleAt": "2026-10-07T02:00:00Z"
         *           },
         *           "platformUserIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "version": 0,
         *           "lastSentAt": null,
         *           "nextRunAt": null
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        NotificationTaskPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["NotificationTaskDetail"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @example {
         *       "templateCode": "mock-example",
         *       "name": "mock-example",
         *       "purpose": "WATCH_ALERT",
         *       "channels": [
         *         "SMS"
         *       ],
         *       "variables": [
         *         {
         *           "code": "mock-example",
         *           "description": "mock-example",
         *           "required": true
         *         }
         *       ],
         *       "version": "mock-example",
         *       "enabled": true
         *     }
         */
        NotificationTemplate: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            channels: components["schemas"]["NotificationChannel"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            enabled: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            purpose: "WATCH_ALERT" | "DEVICE_STATUS";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            templateCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            variables: components["schemas"]["NotificationTemplateVariablesItem"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: string;
        };
        /**
         * @example {
         *       "items": [
         *         {
         *           "templateCode": "mock-example",
         *           "name": "mock-example",
         *           "purpose": "WATCH_ALERT",
         *           "channels": [
         *             "SMS"
         *           ],
         *           "variables": [
         *             {
         *               "code": "mock-example",
         *               "description": "mock-example",
         *               "required": true
         *             }
         *           ],
         *           "version": "mock-example",
         *           "enabled": true
         *         }
         *       ]
         *     }
         */
        NotificationTemplateList: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["NotificationTemplate"][];
        };
        /**
         * @example {
         *       "code": "mock-example",
         *       "description": "mock-example",
         *       "required": true
         *     }
         */
        NotificationTemplateVariablesItem: {
            code: string;
            description: string;
            required: boolean;
        };
        /**
         * @description 接口模型 NumberDeviceParameter；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "label": "mock-example",
         *       "valueType": "NUMBER",
         *       "numberValue": 0,
         *       "readOnly": true,
         *       "updatedAt": "2026-10-07T02:00:00Z"
         *     }
         */
        NumberDeviceParameter: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            numberValue: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            readOnly: boolean;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /**
             * @description 判别字段固定为 NUMBER。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "NUMBER";
        };
        /**
         * @description 接口模型 NumberDeviceParameterChange；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "NUMBER",
         *       "numberValue": 0
         *     }
         */
        NumberDeviceParameterChange: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            numberValue: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /**
             * @description 判别字段固定为 NUMBER。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "NUMBER";
        };
        /**
         * @description 接口模型 NumberModelParameterValue；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "NUMBER",
         *       "numberValue": 0
         *     }
         */
        NumberModelParameterValue: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            numberValue: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /**
             * @description 判别字段固定为 NUMBER。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "NUMBER";
        };
        /**
         * @example {
         *       "scheduleType": "ONCE",
         *       "scheduleAt": "2026-10-07T02:00:00Z"
         *     }
         */
        OneTimeNotificationSchedule: {
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            scheduleAt: string;
            /**
             * @description 判别字段固定为 ONCE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            scheduleType: "ONCE";
        };
        /**
         * @description 四类区域共用METRIC_V1和5秒新数据可见预算；WEEK/MONTH/QUARTER/YEAR按Asia/Shanghai自然周期，内部[start,end)。短信猫字段来自真实适配器，未知为null，不编造信号/余额。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "deviceOnline": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "total": null,
         *         "online": null,
         *         "onlineRate": null,
         *         "byDeviceType": [
         *           {
         *             "deviceType": "mock-example",
         *             "total": 0,
         *             "online": 0
         *           }
         *         ],
         *         "byPowerCategory": [
         *           {
         *             "powerCategoryCode": "mock-example",
         *             "label": "mock-example",
         *             "total": 0,
         *             "online": 0,
         *             "onlineRate": 0
         *           }
         *         ]
         *       },
         *       "captureOverview": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "todayCaptured": null,
         *         "allCaptured": null,
         *         "todayIngested": null,
         *         "allIngested": null
         *       },
         *       "smsModem": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "online": null,
         *         "uptimeSeconds": null,
         *         "networkRegistrationCount": null,
         *         "sentSuccessCount": null,
         *         "sentTotalCount": null
         *       },
         *       "anomalies": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "deviceAnomalies": [
         *           {
         *             "code": "UNREGISTERED_DEVICE",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ],
         *         "dataAnomalies": [
         *           {
         *             "code": "UNREGISTERED_DEVICE",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ],
         *         "volumeAnomalies": [
         *           {
         *             "code": "UNREGISTERED_DEVICE",
         *             "points": [
         *               {
         *                 "at": "2026-10-07T02:00:00Z",
         *                 "value": null
         *               }
         *             ]
         *           }
         *         ]
         *       },
         *       "timeGranularity": "WEEK",
         *       "byDataType": [
         *         {
         *           "dataType": "FENCE",
         *           "totals": {
         *             "meta": {
         *               "asOf": null,
         *               "status": "READY",
         *               "unit": "mock-example",
         *               "refreshPolicy": "POLL"
         *             },
         *             "todayCaptured": null,
         *             "allCaptured": null,
         *             "todayIngested": null,
         *             "allIngested": null
         *           },
         *           "captureSeries": [
         *             {
         *               "metricCode": "CAPTURED",
         *               "metric": {
         *                 "meta": {
         *                   "asOf": null,
         *                   "status": "READY",
         *                   "unit": "mock-example",
         *                   "refreshPolicy": "POLL"
         *                 },
         *                 "series": [
         *                   {
         *                     "code": "mock-example",
         *                     "label": "mock-example",
         *                     "points": [
         *                       {
         *                         "at": "2026-10-07T02:00:00Z",
         *                         "value": null
         *                       }
         *                     ]
         *                   }
         *                 ]
         *               }
         *             }
         *           ],
         *           "anomalies": {
         *             "meta": {
         *               "asOf": null,
         *               "status": "READY",
         *               "unit": "mock-example",
         *               "refreshPolicy": "POLL"
         *             },
         *             "deviceAnomalies": [
         *               {
         *                 "code": "UNREGISTERED_DEVICE",
         *                 "points": [
         *                   {
         *                     "at": "2026-10-07T02:00:00Z",
         *                     "value": null
         *                   }
         *                 ]
         *               }
         *             ],
         *             "dataAnomalies": [
         *               {
         *                 "code": "UNREGISTERED_DEVICE",
         *                 "points": [
         *                   {
         *                     "at": "2026-10-07T02:00:00Z",
         *                     "value": null
         *                   }
         *                 ]
         *               }
         *             ],
         *             "volumeAnomalies": [
         *               {
         *                 "code": "UNREGISTERED_DEVICE",
         *                 "points": [
         *                   {
         *                     "at": "2026-10-07T02:00:00Z",
         *                     "value": null
         *                   }
         *                 ]
         *               }
         *             ]
         *           }
         *         }
         *       ],
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       },
         *       "bucketGranularity": "DAY"
         *     }
         */
        OperationsSummary: {
            anomalies: components["schemas"]["AnomalyOverview"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            bucketGranularity: "DAY" | "MONTH";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            byDataType: components["schemas"]["DataTypeOperations"][];
            captureOverview: components["schemas"]["CaptureTotals"];
            deviceOnline: components["schemas"]["DeviceOnlineOverview"];
            smsModem: components["schemas"]["SmsModemStatus"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            timeGranularity: "WEEK" | "MONTH" | "QUARTER" | "YEAR";
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 本人需currentPassword；管理员重置reason必填、撤销用户全部会话。新密码15至128字符/NFC/离线弱密码表，不强制字符组合或90天轮换；首次登录强改。响应/日志不含密码。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "newPassword": "Example-password-2026!"
         *     }
         */
        PasswordChangeInput: {
            /**
             * Format: password
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            currentPassword?: string;
            /**
             * Format: password
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            newPassword: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason?: string;
        };
        /**
         * @example {
         *       "items": [
         *         {
         *           "code": "dashboard:read",
         *           "label": "mock-example",
         *           "module": "mock-example",
         *           "description": "mock-example",
         *           "assignable": true
         *         }
         *       ],
         *       "version": "mock-example"
         *     }
         */
        PermissionCatalog: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["PermissionDescriptor"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: string;
        };
        /**
         * @description 58项既有权限码不重复扩充。角色矩阵和28菜单见policy；操作权限暂按角色并集，数据范围采用已确认多角色并集/账号附加授权/超级管理员全采集可见；额外授权不超授予者范围。 [PD-2026-10-19.v1，待甲方确认]
         * @example dashboard:read
         * @enum {string}
         */
        PermissionCode: "dashboard:read" | "capture:read" | "capture:export" | "capture:decrypt" | "face:search" | "face:download" | "context:use" | "map:read" | "analysis:read" | "analysis:create" | "analysis:update" | "analysis:delete" | "dataset:read" | "dataset:write" | "dataset:export" | "watch:read" | "watch:write" | "watch:revoke" | "watch:delete" | "notification:read" | "notification:send" | "notification:cancel" | "device:read" | "device:write" | "device:command" | "device:reset" | "report:read" | "report:create" | "user:read" | "user:write" | "user:disable" | "role:read" | "role:grant" | "audit:read" | "audit:export" | "delivery:read" | "delivery:export" | "ingestion:read" | "ingestion:write" | "ingestion:replay" | "file:download" | "task:read" | "task:manage" | "person-library:read" | "person-library:write" | "person-library:export" | "profile:read" | "profile:correct" | "profile:merge" | "profile:export" | "model:read" | "model:write" | "model:execute" | "site:read" | "site:write" | "device-group:read" | "device-group:write" | "report:delete";
        /**
         * @example {
         *       "code": "dashboard:read",
         *       "label": "mock-example",
         *       "module": "mock-example",
         *       "description": "mock-example",
         *       "assignable": true
         *     }
         */
        PermissionDescriptor: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            assignable: boolean;
            code: components["schemas"]["PermissionCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            module: string;
        };
        /**
         * @example {
         *       "libraryIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "format": "XLSX",
         *       "reason": "mock-example"
         *     }
         */
        PersonLibrariesExportInput: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            libraryIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @example {
         *       "fileAssetId": "00000000-0000-4000-8000-000000000001",
         *       "templateCode": "PERSON_LIBRARIES_V1",
         *       "duplicatePolicy": "SKIP",
         *       "reason": "mock-example"
         *     }
         */
        PersonLibrariesImportInput: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            duplicatePolicy: "SKIP" | "FAIL";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            fileAssetId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            templateCode: "PERSON_LIBRARIES_V1";
        };
        /**
         * @description 删除或缩小范围时须检查布控/任务引用；成员访问不得超过原始数据权限。
         * @example {
         *       "libraryId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "category": "mock-example",
         *       "memberCount": 0,
         *       "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *       "siteScope": {
         *         "siteIds": [],
         *         "allSites": true
         *       },
         *       "dataScope": {
         *         "siteIds": [],
         *         "allSites": true,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "ownOnly": true,
         *         "scopeVersion": 0
         *       },
         *       "linkedWatchRuleIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1
         *     }
         */
        PersonLibraryDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            category: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            dataScope: components["schemas"]["DataScope"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            libraryId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            linkedWatchRuleIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            memberCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ownerUserId: string;
            siteScope: components["schemas"]["SiteScope"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 PersonLibraryExportInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "format": "XLSX",
         *       "reason": "mock-example"
         *     }
         */
        PersonLibraryExportInput: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 沿用PERSON_LIBRARIES_V1、CSV/XLSX/JSON和ALL_OR_NOTHING；仅显式允许重复行可SKIP，错误标识不得upsert吞错。保留逐行错误与资源ID，不从同型标识自动认定跨源同人。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "fileAssetId": "00000000-0000-4000-8000-000000000001",
         *       "duplicatePolicy": "SKIP",
         *       "reason": "mock-example",
         *       "templateCode": "PERSON_LIBRARIES_V1"
         *     }
         */
        PersonLibraryImportInput: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            duplicatePolicy: "SKIP" | "FAIL";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            fileAssetId: string;
            /**
             * @deprecated
             * @description 兼容旧导入请求的别名；只接受模板version=1，省略等同1。平台标准模板不需要前端另猜厂商mappingVersion。
             * @enum {string}
             */
            mappingVersion?: "1";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            templateCode: "PERSON_LIBRARIES_V1";
        };
        /**
         * @description 接口模型 PersonLibraryInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "name": "mock-example",
         *       "category": "mock-example",
         *       "siteScope": {
         *         "siteIds": [],
         *         "allSites": true
         *       },
         *       "dataScope": {
         *         "siteIds": [],
         *         "allSites": true,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "ownOnly": true,
         *         "scopeVersion": 0
         *       }
         *     }
         */
        PersonLibraryInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            category: string;
            dataScope: components["schemas"]["DataScope"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            siteScope: components["schemas"]["SiteScope"];
        };
        /**
         * @description 接口模型 PersonLibraryMember；具体业务范围以使用它的操作为准。
         * @example {
         *       "memberId": "00000000-0000-4000-8000-000000000001",
         *       "libraryId": "00000000-0000-4000-8000-000000000001",
         *       "profileId": "00000000-0000-4000-8000-000000000001",
         *       "displayName": "mock-example",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "source": "mock-example",
         *       "addedAt": "2026-10-07T02:00:00Z",
         *       "addedByUserId": "00000000-0000-4000-8000-000000000001",
         *       "person": {
         *         "nameMasked": null,
         *         "phoneMasked": null,
         *         "identityNumberMasked": null
         *       },
         *       "version": 0
         *     }
         */
        PersonLibraryMember: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            addedAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            addedByUserId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayName: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            libraryId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            memberId: string;
            person: components["schemas"]["PersonProfileInfo"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            profileId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            target: components["schemas"]["ContextTarget"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 PersonLibraryMemberPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "memberId": "00000000-0000-4000-8000-000000000001",
         *           "libraryId": "00000000-0000-4000-8000-000000000001",
         *           "profileId": "00000000-0000-4000-8000-000000000001",
         *           "displayName": "mock-example",
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "source": "mock-example",
         *           "addedAt": "2026-10-07T02:00:00Z",
         *           "addedByUserId": "00000000-0000-4000-8000-000000000001",
         *           "person": {
         *             "nameMasked": null,
         *             "phoneMasked": null,
         *             "identityNumberMasked": null
         *           },
         *           "version": 0
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        PersonLibraryMemberPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["PersonLibraryMember"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 PersonLibraryMembersInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "profileIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        PersonLibraryMembersInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            profileIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 接口模型 PersonLibraryPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "libraryId": "00000000-0000-4000-8000-000000000001",
         *           "name": "mock-example",
         *           "category": "mock-example",
         *           "memberCount": 0,
         *           "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "version": 1
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        PersonLibraryPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["PersonLibrarySummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @example {
         *       "member": {
         *         "memberId": "00000000-0000-4000-8000-000000000001",
         *         "libraryId": "00000000-0000-4000-8000-000000000001",
         *         "profileId": "00000000-0000-4000-8000-000000000001",
         *         "displayName": "mock-example",
         *         "target": {
         *           "targetId": "00000000-0000-4000-8000-000000000001",
         *           "targetType": "IMSI"
         *         },
         *         "source": "mock-example",
         *         "addedAt": "2026-10-07T02:00:00Z",
         *         "addedByUserId": "00000000-0000-4000-8000-000000000001",
         *         "person": {
         *           "nameMasked": null,
         *           "phoneMasked": null,
         *           "identityNumberMasked": null
         *         },
         *         "version": 0
         *       },
         *       "profile": {
         *         "profileId": "00000000-0000-4000-8000-000000000001",
         *         "profileType": "PERSON",
         *         "displayLabel": "mock-example",
         *         "linkedTargets": [
         *           {
         *             "linkId": "00000000-0000-4000-8000-000000000001",
         *             "target": {
         *               "targetId": "00000000-0000-4000-8000-000000000001",
         *               "targetType": "IMSI"
         *             },
         *             "displayTarget": "mock-example",
         *             "confidence": null,
         *             "evidenceIds": [
         *               "00000000-0000-4000-8000-000000000001"
         *             ],
         *             "source": "mock-example",
         *             "reviewStatus": "UNREVIEWED"
         *           }
         *         ],
         *         "evidenceCount": 0,
         *         "captureCount": 0,
         *         "siteCount": 0,
         *         "confidence": null,
         *         "createdAt": "2026-10-07T02:00:00Z",
         *         "updatedAt": "2026-10-07T02:00:00Z",
         *         "version": 1,
         *         "person": {
         *           "nameMasked": null,
         *           "phoneMasked": null,
         *           "identityNumberMasked": null
         *         },
         *         "regionCount": 0,
         *         "visitedRegionCodes": [
         *           "mock-example"
         *         ]
         *       }
         *     }
         */
        PersonLibraryPersonCreated: {
            member: components["schemas"]["PersonLibraryMember"];
            profile: components["schemas"]["PersonProfileDetail"];
        };
        /**
         * @description 手工新增人员的资料，姓名必填；敏感标识只在请求体。
         * @example {
         *       "name": "mock-example"
         *     }
         */
        PersonLibraryPersonInfoInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            appearanceDescription?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            ethnicity?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            gender?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            identityNumber?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            phone?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            portraitAssetId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            residenceRegionCode?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            suspectedLandingText?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            suspectedSiteId?: string;
        };
        /**
         * @description person.name必填；手工资料创建PERSON档案并加入库，关联来源MANUAL且不产生算法置信度。无采集标识也能登记布控目标。
         * @example {
         *       "person": {
         *         "name": "示例人员",
         *         "gender": "MALE",
         *         "ethnicity": "示例民族",
         *         "appearanceDescription": "短发、深色衣服"
         *       },
         *       "identifiers": [
         *         {
         *           "targetType": "IMSI",
         *           "value": "460000000000001"
         *         }
         *       ],
         *       "reason": "人工录入，未进行算法归档"
         *     }
         */
        PersonLibraryPersonInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            identifiers: components["schemas"]["TargetIdentifier"][];
            person: components["schemas"]["PersonLibraryPersonInfoInput"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 接口模型 PersonLibrarySummary；具体业务范围以使用它的操作为准。
         * @example {
         *       "libraryId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "category": "mock-example",
         *       "memberCount": 0,
         *       "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1
         *     }
         */
        PersonLibrarySummary: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            category: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            libraryId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            memberCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ownerUserId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 只通过受权请求体提交；服务端存证并在普通响应中脱敏。
         * @example {}
         */
        PersonProfileCorrection: {
            /** @description 只更新本次提供的appearanceDescription；省略保留原值，敏感输入需授权并留用途/证据；不从脱敏值反写。 */
            appearanceDescription?: string;
            /** @description 只更新本次提供的ethnicity；省略保留原值，敏感输入需授权并留用途/证据；不从脱敏值反写。 */
            ethnicity?: string;
            /** @description 只更新本次提供的gender；省略保留原值，敏感输入需授权并留用途/证据；不从脱敏值反写。 */
            gender?: string;
            /** @description 只更新本次提供的identityNumber；省略保留原值，敏感输入需授权并留用途/证据；不从脱敏值反写。 */
            identityNumber?: string;
            /** @description 只更新本次提供的name；省略保留原值，敏感输入需授权并留用途/证据；不从脱敏值反写。 */
            name?: string;
            /** @description 只更新本次提供的phone；省略保留原值，敏感输入需授权并留用途/证据；不从脱敏值反写。 */
            phone?: string;
            /**
             * Format: uuid
             * @description 只更新本次提供的portraitAssetId；省略保留原值，敏感输入需授权并留用途/证据；不从脱敏值反写。
             */
            portraitAssetId?: string;
            /** @description 只更新本次提供的residenceRegionCode；省略保留原值，敏感输入需授权并留用途/证据；不从脱敏值反写。 */
            residenceRegionCode?: string;
            /** @description 只更新本次提供的suspectedLandingText；省略保留原值，敏感输入需授权并留用途/证据；不从脱敏值反写。 */
            suspectedLandingText?: string;
            /**
             * Format: uuid
             * @description 只更新本次提供的suspectedSiteId；省略保留原值，敏感输入需授权并留用途/证据；不从脱敏值反写。
             */
            suspectedSiteId?: string;
        };
        /**
         * @description 仅现有白名单资料字段，reason/evidence必填；profile:correct和当前范围校验，不修改设备原始事实。合并拆分另需profile:merge及独立复核，形成新revision。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "profileType": "PERSON",
         *       "person": {},
         *       "supportingEvidenceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        PersonProfileCorrectionInput: {
            person: components["schemas"]["PersonProfileCorrection"];
            /**
             * @description 判别字段固定为 PERSON。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            profileType: "PERSON";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            supportingEvidenceIds: string[];
        };
        /**
         * @description 档案类型与详情字段一一对应；敏感身份值默认脱敏。
         * @example {
         *       "profileId": "00000000-0000-4000-8000-000000000001",
         *       "profileType": "PERSON",
         *       "displayLabel": "mock-example",
         *       "linkedTargets": [
         *         {
         *           "linkId": "00000000-0000-4000-8000-000000000001",
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "displayTarget": "mock-example",
         *           "confidence": null,
         *           "evidenceIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "source": "mock-example",
         *           "reviewStatus": "UNREVIEWED"
         *         }
         *       ],
         *       "evidenceCount": 0,
         *       "captureCount": 0,
         *       "siteCount": 0,
         *       "confidence": null,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1,
         *       "person": {
         *         "nameMasked": null,
         *         "phoneMasked": null,
         *         "identityNumberMasked": null
         *       },
         *       "regionCount": 0,
         *       "visitedRegionCodes": [
         *         "mock-example"
         *       ]
         *     }
         */
        PersonProfileDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            confidence: number | null;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayLabel: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceCount: number;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            firstSeenAt?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            lastSeenAt?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            linkedTargets: components["schemas"]["ProfileTargetLink"][];
            person: components["schemas"]["PersonProfileInfo"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            profileId: string;
            /**
             * @description 判别字段固定为 PERSON。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            profileType: "PERSON";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteCount: number;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            visitedRegionCodes: string[];
        };
        /**
         * @description 接口模型 PersonProfileInfo；具体业务范围以使用它的操作为准。
         * @example {
         *       "nameMasked": null,
         *       "phoneMasked": null,
         *       "identityNumberMasked": null
         *     }
         */
        PersonProfileInfo: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            appearanceDescription?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            ethnicity?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            gender?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            identityNumberMasked: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nameMasked: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            phoneMasked: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            portraitAssetId?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            residenceRegionCode?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            suspectedLandingText?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            suspectedSiteId?: string | null;
        };
        /**
         * @description 接口模型 PlateCaptureRecord；具体业务范围以使用它的操作为准。；targetType必返，表示载荷主目标类型；FENCE须IMSI或IMEI，其它分别MAC/PLATE/FACE。targetId=null表示尚未解析代理ID，不能直接研判，主类型仍返回。
         * @example {
         *       "captureId": "00000000-0000-4000-8000-000000000001",
         *       "dataType": "PLATE",
         *       "targetId": "00000000-0000-4000-8000-000000000002",
         *       "deviceId": "00000000-0000-4000-8000-000000000003",
         *       "siteId": "00000000-0000-4000-8000-000000000004",
         *       "captureTime": "2026-10-07T02:00:00Z",
         *       "ingestedAt": "2026-10-07T02:00:00Z",
         *       "longitude": 116.4,
         *       "latitude": 39.9,
         *       "source": "MOCK_PLATE",
         *       "status": "VALID",
         *       "displayTarget": "示A1***1",
         *       "isMasked": true,
         *       "plate": {
         *         "plateMasked": "示A1***1"
         *       },
         *       "targetType": "PLATE"
         *     }
         */
        PlateCaptureRecord: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            captureId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            captureTime: string;
            /**
             * @description 判别字段固定为 PLATE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "PLATE";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            ingestedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            isMasked: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            latitude: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            longitude: number | null;
            plate: components["schemas"]["PlateRecord"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            status: components["schemas"]["CaptureStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            targetId: string | null;
            targetType: components["schemas"]["TargetType"];
        };
        /**
         * @description 供 Context 保存的不可变业务筛选条件；不包含本次分页游标。；targetIds同组为OR，与其它条件为AND；禁止把脱敏displayTarget作精确条件。
         * @example {
         *       "dataType": "PLATE",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        PlateCaptureSearchFilter: {
            /**
             * @description 判别字段固定为 PLATE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "PLATE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            plateColor?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            plateNumber?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetIds?: string[];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description ；targetIds同组为OR，与其它条件为AND；禁止把脱敏displayTarget作精确条件。
         * @example {
         *       "dataType": "PLATE",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        PlateCaptureSearchRequest: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cursor?: string;
            /**
             * @description 判别字段固定为 PLATE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            dataType: "PLATE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 20
             */
            pageSize?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            plateColor?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            plateNumber?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default captureTime
             * @enum {string}
             */
            sortBy?: "captureTime" | "captureId";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default DESC
             * @enum {string}
             */
            sortOrder?: "ASC" | "DESC";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetIds?: string[];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 接口模型 PlateRecord；具体业务范围以使用它的操作为准。
         * @example {
         *       "plateMasked": "mock-example"
         *     }
         */
        PlateRecord: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            plateColor?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            plateMasked: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            vehicleImageAssetId?: string;
        };
        /**
         * @example {
         *       "messageId": "00000000-0000-4000-8000-000000000001",
         *       "recipientUserId": "00000000-0000-4000-8000-000000000001",
         *       "title": "mock-example",
         *       "content": "mock-example",
         *       "createdAt": "2026-10-07T02:00:00Z"
         *     }
         */
        PlatformMessage: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            alertId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            content: string;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            messageId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            notificationTaskId?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            recipientUserId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            title: string;
        };
        /**
         * @description 按统一分页规范返回；空结果 items=[]。
         * @example {
         *       "items": [
         *         {
         *           "messageId": "00000000-0000-4000-8000-000000000001",
         *           "recipientUserId": "00000000-0000-4000-8000-000000000001",
         *           "title": "mock-example",
         *           "content": "mock-example",
         *           "createdAt": "2026-10-07T02:00:00Z"
         *         }
         *       ],
         *       "pageSize": 1,
         *       "nextCursor": null,
         *       "totalCount": 1,
         *       "asOf": "2026-10-07T02:00:00Z"
         *     }
         */
        PlatformMessagePage: {
            /** Format: date-time */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["PlatformMessage"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 当前用户全部保留消息数量，包含未加载的页。 */
            totalCount: number;
        };
        /**
         * @description 用户ID来自/users或/me。
         * @example {
         *       "channel": "PLATFORM",
         *       "userId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        PlatformNotificationTarget: {
            /**
             * @description 判别字段固定为 PLATFORM。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            channel: "PLATFORM";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            userId: string;
        };
        /**
         * @description 功率分类边界取设备目录/配置，不把Mock 10/20/30当生产阈值。
         * @example {
         *       "powerCategoryCode": "mock-example",
         *       "label": "mock-example",
         *       "total": 0,
         *       "online": 0,
         *       "onlineRate": 0
         *     }
         */
        PowerOnlineBucket: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            online: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            onlineRate: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            powerCategoryCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /** @description 接口模型 PreviewSetTaskResponse；具体业务范围以使用它的操作为准。 */
        PreviewSetTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["SetPreview"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @example {
         *       "conflictId": "00000000-0000-4000-8000-000000000001",
         *       "profileId": "00000000-0000-4000-8000-000000000001",
         *       "field": "mock-example",
         *       "evidenceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "status": "OPEN",
         *       "description": "mock-example"
         *     }
         */
        ProfileConflict: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            conflictId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            field: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            profileId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            status: "OPEN" | "RESOLVED";
        };
        /**
         * @description 按统一分页规范返回；空结果 items=[]。
         * @example {
         *       "items": [
         *         {
         *           "conflictId": "00000000-0000-4000-8000-000000000001",
         *           "profileId": "00000000-0000-4000-8000-000000000001",
         *           "field": "mock-example",
         *           "evidenceIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "status": "OPEN",
         *           "description": "mock-example"
         *         }
         *       ],
         *       "pageSize": 1,
         *       "nextCursor": null
         *     }
         */
        ProfileConflictPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["ProfileConflict"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 接口模型 ProfileConflictResolutionInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "conflictId": "00000000-0000-4000-8000-000000000001",
         *       "selectedEvidenceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        ProfileConflictResolutionInput: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            conflictId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            selectedEvidenceIds: string[];
        };
        /**
         * @description 接口模型 ProfileCorrectionInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "profileType": "PERSON",
         *       "person": {},
         *       "supportingEvidenceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        ProfileCorrectionInput: components["schemas"]["PersonProfileCorrectionInput"] | components["schemas"]["VehicleProfileCorrectionInput"];
        /**
         * @description PERSON/VEHICLE分型；跨源只产候选+证据，未经校准/人工确认confidence=null；保留旧ID/修订谱系，合并遵守retainedProfileId，不自动404或重写历史任务。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "profileId": "00000000-0000-4000-8000-000000000001",
         *       "profileType": "PERSON",
         *       "displayLabel": "mock-example",
         *       "linkedTargets": [
         *         {
         *           "linkId": "00000000-0000-4000-8000-000000000001",
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "displayTarget": "mock-example",
         *           "confidence": null,
         *           "evidenceIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "source": "mock-example",
         *           "reviewStatus": "UNREVIEWED"
         *         }
         *       ],
         *       "evidenceCount": 0,
         *       "captureCount": 0,
         *       "siteCount": 0,
         *       "confidence": null,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1,
         *       "person": {
         *         "nameMasked": null,
         *         "phoneMasked": null,
         *         "identityNumberMasked": null
         *       },
         *       "regionCount": 0,
         *       "visitedRegionCodes": [
         *         "mock-example"
         *       ]
         *     }
         */
        ProfileDetail: components["schemas"]["PersonProfileDetail"] | components["schemas"]["VehicleProfileDetail"];
        /**
         * @description 接口模型 ProfileEvidence；具体业务范围以使用它的操作为准。
         * @example {
         *       "evidenceId": "00000000-0000-4000-8000-000000000001",
         *       "profileId": "00000000-0000-4000-8000-000000000001",
         *       "captureId": "00000000-0000-4000-8000-000000000001",
         *       "source": "mock-example",
         *       "sourceVersion": "mock-example",
         *       "relation": "PRIMARY",
         *       "collectedAt": "2026-10-07T02:00:00Z",
         *       "reviewStatus": "UNREVIEWED"
         *     }
         */
        ProfileEvidence: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            captureId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            collectedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            confidence?: number | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            evidenceId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            profileId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            relation: "PRIMARY" | "ASSOCIATED";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            reviewStatus: "UNREVIEWED" | "CONFIRMED" | "REJECTED";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            sourceVersion: string;
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "evidenceId": "00000000-0000-4000-8000-000000000001",
         *           "profileId": "00000000-0000-4000-8000-000000000001",
         *           "captureId": "00000000-0000-4000-8000-000000000001",
         *           "source": "mock-example",
         *           "sourceVersion": "mock-example",
         *           "relation": "PRIMARY",
         *           "collectedAt": "2026-10-07T02:00:00Z",
         *           "reviewStatus": "UNREVIEWED"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        ProfileEvidencePage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["ProfileEvidence"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 接口模型 ProfileExportInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "format": "XLSX",
         *       "reason": "mock-example"
         *     }
         */
        ProfileExportInput: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 接口模型 ProfileMergeInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "sourceProfileIds": [
         *         "00000000-0000-4000-8000-000000000001",
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "retainedProfileId": "00000000-0000-4000-8000-000000000001",
         *       "evidenceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        ProfileMergeInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            retainedProfileId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            sourceProfileIds: string[];
        };
        /**
         * @description 接口模型 ProfileRevision；具体业务范围以使用它的操作为准。
         * @example {
         *       "revisionId": "00000000-0000-4000-8000-000000000001",
         *       "profileId": "00000000-0000-4000-8000-000000000001",
         *       "action": "CORRECT",
         *       "actorUserId": "00000000-0000-4000-8000-000000000001",
         *       "reason": "mock-example",
         *       "occurredAt": "2026-10-07T02:00:00Z",
         *       "sourceProfileIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "resultProfileIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "evidenceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ]
         *     }
         */
        ProfileRevision: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            action: "CORRECT" | "MERGE" | "SPLIT" | "RESOLVE_CONFLICT";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            actorUserId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceIds: string[];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            occurredAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            profileId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resultProfileIds: string[];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            revisionId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            sourceProfileIds: string[];
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "revisionId": "00000000-0000-4000-8000-000000000001",
         *           "profileId": "00000000-0000-4000-8000-000000000001",
         *           "action": "CORRECT",
         *           "actorUserId": "00000000-0000-4000-8000-000000000001",
         *           "reason": "mock-example",
         *           "occurredAt": "2026-10-07T02:00:00Z",
         *           "sourceProfileIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "resultProfileIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "evidenceIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ]
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        ProfileRevisionPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["ProfileRevision"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "profileId": "00000000-0000-4000-8000-000000000001",
         *           "profileType": "PERSON",
         *           "displayLabel": "mock-example",
         *           "confidence": null,
         *           "evidenceCount": 0,
         *           "lastSeenAt": null,
         *           "updatedAt": "2026-10-07T02:00:00Z"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        ProfileSearchPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["ProfileSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 敏感目标条件只放 JSON 请求体。
         * @example {
         *       "profileType": "PERSON"
         *     }
         */
        ProfileSearchRequest: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            appearanceQuery?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cursor?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayQuery?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize?: number;
            profileType: components["schemas"]["ProfileType"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            sortBy?: "updatedAt" | "lastSeenAt" | "profileId";
            sortOrder?: components["schemas"]["SortOrder"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targetQuery?: string;
            targetType?: components["schemas"]["TargetType"];
        };
        /**
         * @description 接口模型 ProfileSplitInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "sourceProfileId": "00000000-0000-4000-8000-000000000001",
         *       "movingTargetLinkIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "movingEvidenceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        ProfileSplitInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            movingEvidenceIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            movingTargetLinkIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            sourceProfileId: string;
        };
        /**
         * @description 接口模型 ProfileSummary；具体业务范围以使用它的操作为准。
         * @example {
         *       "profileId": "00000000-0000-4000-8000-000000000001",
         *       "profileType": "PERSON",
         *       "displayLabel": "mock-example",
         *       "confidence": null,
         *       "evidenceCount": 0,
         *       "lastSeenAt": null,
         *       "updatedAt": "2026-10-07T02:00:00Z"
         *     }
         */
        ProfileSummary: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            confidence: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayLabel: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceCount: number;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            lastSeenAt: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            profileId: string;
            profileType: components["schemas"]["ProfileType"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
        };
        /**
         * @description 接口模型 ProfileTargetLink；具体业务范围以使用它的操作为准。
         * @example {
         *       "linkId": "00000000-0000-4000-8000-000000000001",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "displayTarget": "mock-example",
         *       "confidence": null,
         *       "evidenceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "source": "mock-example",
         *       "reviewStatus": "UNREVIEWED"
         *     }
         */
        ProfileTargetLink: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            algorithmVersion?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            confidence: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceIds: string[];
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            firstSeenAt?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            lastSeenAt?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            linkId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            reviewStatus: "UNREVIEWED" | "CONFIRMED" | "REJECTED";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            source: string;
            target: components["schemas"]["ContextTarget"];
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "linkId": "00000000-0000-4000-8000-000000000001",
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "displayTarget": "mock-example",
         *           "confidence": null,
         *           "evidenceIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "source": "mock-example",
         *           "reviewStatus": "UNREVIEWED"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        ProfileTargetPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["ProfileTargetLink"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example PERSON
         * @enum {string}
         */
        ProfileType: "PERSON" | "VEHICLE";
        /** @description 接口模型 QueryTrajectoryResponse；具体业务范围以使用它的操作为准。 */
        QueryTrajectoryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TrajectoryPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 RankItem；具体业务范围以使用它的操作为准。
         * @example {
         *       "key": "mock-example",
         *       "label": "mock-example",
         *       "value": 0
         *     }
         */
        RankItem: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            key: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            value: number;
        };
        /**
         * @description 接口模型 RankMetric；具体业务范围以使用它的操作为准。
         * @example {
         *       "meta": {
         *         "asOf": null,
         *         "status": "READY",
         *         "unit": "mock-example",
         *         "refreshPolicy": "POLL"
         *       },
         *       "items": [
         *         {
         *           "key": "mock-example",
         *           "label": "mock-example",
         *           "value": 0
         *         }
         *       ]
         *     }
         */
        RankMetric: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["RankItem"][];
            meta: components["schemas"]["MetricMeta"];
        };
        ReadDecryptionResultApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "readDecryptionResult";
            pathParameters: {
                /** Format: uuid */
                requestId: string;
            };
        };
        /**
         * @description 接口模型 ReadinessData；具体业务范围以使用它的操作为准。
         * @example {
         *       "status": "READY",
         *       "checkedAt": "2026-10-07T02:00:00Z",
         *       "dependencies": [
         *         {
         *           "name": "mock-example",
         *           "status": "READY"
         *         }
         *       ]
         *     }
         */
        ReadinessData: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            checkedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dependencies: components["schemas"]["ReadinessDataDependenciesItem"][];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            status: "READY" | "NOT_READY";
        };
        /**
         * @example {
         *       "name": "mock-example",
         *       "status": "READY"
         *     }
         */
        ReadinessDataDependenciesItem: {
            name: string;
            /** @enum {string} */
            status: "READY" | "NOT_READY";
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example POLL
         * @enum {string}
         */
        RefreshPolicy: "POLL" | "ON_DEMAND" | "SNAPSHOT";
        /**
         * @description PDF p13区域统计结果只明确这三类，不加入FACE。
         * @example FENCE
         * @enum {string}
         */
        RegionAnalysisDataType: "FENCE" | "PLATE" | "MAC";
        /**
         * @description kind 必须与现有任务一致，改变分析类型须创建新任务。
         * @example {
         *       "kind": "REGION_STATISTICS",
         *       "parameters": {
         *         "regions": [
         *           {
         *             "type": "Polygon",
         *             "coordinates": [
         *               [
         *                 [
         *                   116.38,
         *                   39.88
         *                 ],
         *                 [
         *                   116.42,
         *                   39.88
         *                 ],
         *                 [
         *                   116.42,
         *                   39.92
         *                 ],
         *                 [
         *                   116.38,
         *                   39.92
         *                 ],
         *                 [
         *                   116.38,
         *                   39.88
         *                 ]
         *               ]
         *             ]
         *           }
         *         ],
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "dataTypes": [
         *           "FENCE"
         *         ]
         *       },
         *       "reason": "mock-example"
         *     }
         */
        RegionAnalysisRevisionInput: {
            /**
             * @description 判别字段固定为 REGION_STATISTICS。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "REGION_STATISTICS";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name?: string;
            parameters: components["schemas"]["RegionStatisticsParameters"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 任务未完成时 resultCount=null；修订产生新任务，不覆盖历史结果。
         * @example {
         *       "task": {
         *         "taskId": "00000000-0000-4000-8000-000000000001",
         *         "taskType": "INGESTION",
         *         "status": "PENDING",
         *         "progress": 0,
         *         "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *         "createdAt": "2026-10-07T02:00:00Z",
         *         "retryCount": 0,
         *         "parameterSnapshot": {
         *           "schemaVersion": "mock-example",
         *           "submittedAt": "2026-10-07T02:00:00Z",
         *           "parameterHash": "0000000000000000000000000000000000000000000000000000000000000000"
         *         },
         *         "traceId": "mock-example",
         *         "name": "mock-example"
         *       },
         *       "name": "mock-example",
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "resultCount": null,
         *       "version": 0,
         *       "kind": "REGION_STATISTICS",
         *       "parameters": {
         *         "regions": [
         *           {
         *             "type": "Polygon",
         *             "coordinates": [
         *               [
         *                 [
         *                   116.38,
         *                   39.88
         *                 ],
         *                 [
         *                   116.42,
         *                   39.88
         *                 ],
         *                 [
         *                   116.42,
         *                   39.92
         *                 ],
         *                 [
         *                   116.38,
         *                   39.92
         *                 ],
         *                 [
         *                   116.38,
         *                   39.88
         *                 ]
         *               ]
         *             ]
         *           }
         *         ],
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "dataTypes": [
         *           "FENCE"
         *         ]
         *       },
         *       "analysisTaskId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        RegionAnalysisTaskDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            algorithmVersion?: string | null;
            /** Format: uuid */
            analysisTaskId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * @description 判别字段固定为 REGION_STATISTICS。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "REGION_STATISTICS";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            parameters: components["schemas"]["RegionStatisticsParameters"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resultCount: number | null;
            task: components["schemas"]["Task"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 RegionAnalysisTaskInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "kind": "REGION_STATISTICS",
         *       "name": "mock-example",
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "parameters": {
         *         "regions": [
         *           {
         *             "type": "Polygon",
         *             "coordinates": [
         *               [
         *                 [
         *                   116.38,
         *                   39.88
         *                 ],
         *                 [
         *                   116.42,
         *                   39.88
         *                 ],
         *                 [
         *                   116.42,
         *                   39.92
         *                 ],
         *                 [
         *                   116.38,
         *                   39.92
         *                 ],
         *                 [
         *                   116.38,
         *                   39.88
         *                 ]
         *               ]
         *             ]
         *           }
         *         ],
         *         "timeRange": {
         *           "from": "2026-10-07T02:00:00Z",
         *           "to": "2026-10-07T03:00:00Z"
         *         },
         *         "dataTypes": [
         *           "FENCE"
         *         ]
         *       }
         *     }
         */
        RegionAnalysisTaskInput: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * @description 判别字段固定为 REGION_STATISTICS。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "REGION_STATISTICS";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            parameters: components["schemas"]["RegionStatisticsParameters"];
        };
        /**
         * @example {
         *       "items": [
         *         {
         *           "regionCode": "mock-example",
         *           "name": "mock-example",
         *           "parentRegionCode": null
         *         }
         *       ],
         *       "version": "mock-example"
         *     }
         */
        RegionCatalog: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["RegionDescriptor"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: string;
        };
        /**
         * @description 行政/业务区域目录，geometry仅在有授权几何时返回。
         * @example {
         *       "regionCode": "mock-example",
         *       "name": "mock-example",
         *       "parentRegionCode": null
         *     }
         */
        RegionDescriptor: {
            geometry?: components["schemas"]["GeoPolygon"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parentRegionCode: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
        };
        /**
         * @example {
         *       "regionCode": "mock-example",
         *       "regionName": "mock-example",
         *       "deviceCount": 0,
         *       "onlineCount": 0,
         *       "onlineRate": 0
         *     }
         */
        RegionOnlineReportRow: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            onlineCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            onlineRate: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionName: string;
        };
        /**
         * @description coverageRate=visitedRegionCount/totalRegionCount，不是嫌疑概率。
         * @example {
         *       "resultId": "00000000-0000-4000-8000-000000000001",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "evidenceCaptureIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "algorithmVersion": "mock-example",
         *       "kind": "REGION_STATISTICS",
         *       "captureCount": 0,
         *       "siteCount": 0,
         *       "visitedRegionCount": 0,
         *       "totalRegionCount": 1,
         *       "coverageRate": 0,
         *       "displayTarget": "mock-example",
         *       "dataType": "FENCE",
         *       "regionCount": 0,
         *       "visitedRegionCodes": [
         *         "mock-example"
         *       ]
         *     }
         */
        RegionResultItem: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            algorithmVersion: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            coverageRate: number;
            dataType: components["schemas"]["RegionAnalysisDataType"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceCaptureIds: string[];
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            firstSeenAt?: string;
            /**
             * @description 判别字段固定为 REGION_STATISTICS。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            kind: "REGION_STATISTICS";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCount: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resultId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteCount: number;
            target: components["schemas"]["ContextTarget"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            totalRegionCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            visitedRegionCodes: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            visitedRegionCount: number;
        };
        /**
         * @description coverageRate = 经过选区数 / 总选区数；不得作为嫌疑概率。
         * @example {
         *       "regions": [
         *         {
         *           "type": "Polygon",
         *           "coordinates": [
         *             [
         *               [
         *                 116.38,
         *                 39.88
         *               ],
         *               [
         *                 116.42,
         *                 39.88
         *               ],
         *               [
         *                 116.42,
         *                 39.92
         *               ],
         *               [
         *                 116.38,
         *                 39.92
         *               ],
         *               [
         *                 116.38,
         *                 39.88
         *               ]
         *             ]
         *           ]
         *         }
         *       ],
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       },
         *       "dataTypes": [
         *         "FENCE"
         *       ]
         *     }
         */
        RegionStatisticsParameters: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dataTypes: components["schemas"]["RegionAnalysisDataType"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            minResultScore?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCodes?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regions: components["schemas"]["GeoPolygon"][];
            timeRange: components["schemas"]["TimeRange"];
        };
        /** @description 接口模型 RemoveDatasetItemResponse；具体业务范围以使用它的操作为准。 */
        RemoveDatasetItemResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DatasetDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 RemoveDeviceGroupMemberResponse；具体业务范围以使用它的操作为准。 */
        RemoveDeviceGroupMemberResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 RemovePersonLibraryMemberResponse；具体业务范围以使用它的操作为准。 */
        RemovePersonLibraryMemberResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ActionResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 RenewSessionResponse；具体业务范围以使用它的操作为准。 */
        RenewSessionResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["SessionData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ReplayIngestionBatchApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "replayIngestionBatch";
            pathParameters: {
                /** Format: uuid */
                batchId: string;
            };
            requestBody: components["schemas"]["ReplayInput"];
        };
        /** @description 接口模型 ReplayIngestionBatchResponse；具体业务范围以使用它的操作为准。 */
        ReplayIngestionBatchResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 回放创建新批次，不能覆盖原批次和已入库记录。
         * @example {
         *       "reason": "mock-example"
         *     }
         */
        ReplayInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dedupVersion?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            mappingVersion?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description reportId 用于报表详情与下载，taskId 用于统一轮询。
         * @example {
         *       "reportId": "00000000-0000-4000-8000-000000000001",
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "status": "PENDING",
         *       "createdAt": "2026-10-07T02:00:00Z"
         *     }
         */
        ReportCreated: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            reportId: string;
            status: components["schemas"]["TaskStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId: string;
        };
        /**
         * @description SINGLE 恰好一个定义，产出 XLSX；BUNDLE 可含多个定义，产出 ZIP。
         * @example {
         *       "name": "mock-example",
         *       "mode": "SINGLE",
         *       "definitions": [
         *         {
         *           "reportType": "DEVICE_REALTIME_ONLINE_RATE",
         *           "granularity": "DAY",
         *           "periodStart": "2026-10-07",
         *           "regionCode": "mock-example"
         *         }
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        ReportCreateInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            definitions: components["schemas"]["ReportDefinition"][];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            mode: "SINGLE" | "BUNDLE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 保留8报表类型/自然DAY-MONTH-YEAR/SINGLE XLSX-BUNDLE ZIP；METRIC_V1同源公式及冻结快照。原始明细最多31天，年度只能显示当前留存覆盖区间，缺历史不补0；regionCode=ALL仍受当前授权范围限制。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "reportType": "DEVICE_REALTIME_ONLINE_RATE",
         *       "granularity": "DAY",
         *       "periodStart": "2026-10-07",
         *       "regionCode": "mock-example"
         *     }
         */
        ReportDefinition: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            granularity: "DAY" | "MONTH" | "YEAR";
            /**
             * Format: date
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            periodEnd?: string;
            /**
             * Format: date
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            periodStart: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            reportType: "DEVICE_REALTIME_ONLINE_RATE" | "DEVICE_REALTIME_STATUS" | "FENCE_OPERATOR_STATISTICS" | "FENCE_AREA_CAPTURE" | "FENCE_DEVICE_CAPTURE" | "MAC_CAPTURE" | "PLATE_CAPTURE" | "SYSTEM_COMPONENT_STATUS";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds?: string[];
        };
        /**
         * @description 接口模型 ReportDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "reportId": "00000000-0000-4000-8000-000000000001",
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "mode": "SINGLE",
         *       "definitions": [
         *         {
         *           "reportType": "DEVICE_REALTIME_ONLINE_RATE",
         *           "granularity": "DAY",
         *           "periodStart": "2026-10-07",
         *           "regionCode": "mock-example"
         *         }
         *       ],
         *       "status": "PENDING",
         *       "progress": 0,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "createdByUserId": "00000000-0000-4000-8000-000000000001",
         *       "version": 1
         *     }
         */
        ReportDetail: {
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            completedAt?: string | null;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            createdByUserId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            definitions: components["schemas"]["ReportDefinition"][];
            errorCode?: components["schemas"]["ErrorCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            errorMessage?: string | null;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            mode: "SINGLE" | "BUNDLE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            outputFileAssetId?: string | null;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string|null}
             */
            outputFormat?: "XLSX" | "ZIP" | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            progress: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            reportId: string;
            status: components["schemas"]["TaskStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 ReportMetricRow；具体业务范围以使用它的操作为准。
         * @example {
         *       "label": "mock-example",
         *       "value": null,
         *       "unit": "mock-example",
         *       "asOf": "2026-10-07T02:00:00Z",
         *       "status": "READY"
         *     }
         */
        ReportMetricRow: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            status: components["schemas"]["MetricStatus"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            unit: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            value: number | null;
        };
        /**
         * @description 接口模型 ReportPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "reportId": "00000000-0000-4000-8000-000000000001",
         *           "taskId": "00000000-0000-4000-8000-000000000001",
         *           "name": "mock-example",
         *           "mode": "SINGLE",
         *           "status": "PENDING",
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "createdByUserId": "00000000-0000-4000-8000-000000000001"
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        ReportPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["ReportSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description SINGLE在线冻结预览、BUNDLE下载ZIP，预览与正式文件使用同一已发布结果/游标；分页20默认100最大，不重新扫实时源。文件/预览保留自然月，下载许可5分钟。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "reportType": "DEVICE_REALTIME_ONLINE_RATE",
         *       "regionCode": "mock-example",
         *       "deviceCount": 0,
         *       "onlineCount": 0,
         *       "onlineRate": 0,
         *       "asOf": "2026-10-07T02:00:00Z",
         *       "items": [
         *         {
         *           "regionCode": "mock-example",
         *           "regionName": "mock-example",
         *           "deviceCount": 0,
         *           "onlineCount": 0,
         *           "onlineRate": 0
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1,
         *       "previewKind": "ONLINE_RATE"
         *     }
         */
        ReportPreview: components["schemas"]["DeviceOnlineReportPreview"] | components["schemas"]["DeviceStatusReportPreview"] | components["schemas"]["CaptureReportPreview"] | components["schemas"]["SystemReportPreview"];
        /**
         * @description 接口模型 ReportPreviewData；具体业务范围以使用它的操作为准。
         * @example {
         *       "reportId": "00000000-0000-4000-8000-000000000001",
         *       "preview": {
         *         "reportType": "DEVICE_REALTIME_ONLINE_RATE",
         *         "regionCode": "mock-example",
         *         "deviceCount": 0,
         *         "onlineCount": 0,
         *         "onlineRate": 0,
         *         "asOf": "2026-10-07T02:00:00Z",
         *         "items": [
         *           {
         *             "regionCode": "mock-example",
         *             "regionName": "mock-example",
         *             "deviceCount": 0,
         *             "onlineCount": 0,
         *             "onlineRate": 0
         *           }
         *         ],
         *         "nextCursor": null,
         *         "pageSize": 1,
         *         "previewKind": "ONLINE_RATE"
         *       }
         *     }
         */
        ReportPreviewData: {
            preview: components["schemas"]["ReportPreview"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            reportId: string;
        };
        /**
         * @description 接口模型 ReportSummary；具体业务范围以使用它的操作为准。
         * @example {
         *       "reportId": "00000000-0000-4000-8000-000000000001",
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "mode": "SINGLE",
         *       "status": "PENDING",
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "createdByUserId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        ReportSummary: {
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            completedAt?: string | null;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            createdByUserId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            mode: "SINGLE" | "BUNDLE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            reportId: string;
            status: components["schemas"]["TaskStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId: string;
        };
        /** @description 接口模型 RequestDecryptionResponse；具体业务范围以使用它的操作为准。 */
        RequestDecryptionResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DecryptionRequestDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ResolveProfileConflictResponse；具体业务范围以使用它的操作为准。 */
        ResolveProfileConflictResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ProfileRevision"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ResolveTargetsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TargetResolutionData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 ResultExportInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "format": "XLSX"
         *     }
         */
        ResultExportInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            columns?: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dataTypes?: components["schemas"]["DataType"][];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            format: "XLSX" | "CSV";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resultIds?: string[];
        };
        /**
         * @description 编辑时保留既有敏感地址；recipientId仅来自同通知计划/布控的snapshot.targets。创建禁止RETAINED；不能引用别人的收件人。
         * @example {
         *       "channel": "RETAINED",
         *       "recipientId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        RetainedNotificationTarget: {
            /**
             * @description 判别字段固定为 RETAINED。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            channel: "RETAINED";
            /** Format: uuid */
            recipientId: string;
        };
        RetryDeviceCommandApprovalProposal: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "retryDeviceCommand";
            pathParameters: {
                /** Format: uuid */
                taskId: string;
            };
            requestBody: components["schemas"]["DeviceCommandRetryInput"];
        };
        /** @description 接口模型 RetryDeviceCommandResponse；具体业务范围以使用它的操作为准。 */
        RetryDeviceCommandResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 RetryNotificationTaskResponse；具体业务范围以使用它的操作为准。 */
        RetryNotificationTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 RetryReportResponse；具体业务范围以使用它的操作为准。 */
        RetryReportResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 RetryTaskResponse；具体业务范围以使用它的操作为准。 */
        RetryTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["Task"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 ReviseAnalysisTaskResponse；具体业务范围以使用它的操作为准。 */
        ReviseAnalysisTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AnalysisTaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        ReviseDeviceCommandApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "reviseDeviceCommand";
            pathParameters: {
                /** Format: uuid */
                taskId: string;
            };
            requestBody: components["schemas"]["DeviceCommandInput"];
        };
        ReviseDeviceCommandResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 批量撤销响应。 */
        RevokeResourcesBatchResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["BatchRevocationResult"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 RevokeWatchRuleResponse；具体业务范围以使用它的操作为准。 */
        RevokeWatchRuleResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["WatchRuleDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 RoleDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "roleId": "00000000-0000-4000-8000-000000000001",
         *       "roleCode": "mock-example",
         *       "name": "mock-example",
         *       "permissionCodes": [
         *         "dashboard:read"
         *       ],
         *       "menuIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "siteScope": {
         *         "siteIds": [],
         *         "allSites": true
         *       },
         *       "dataScope": {
         *         "siteIds": [],
         *         "allSites": true,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "ownOnly": true,
         *         "scopeVersion": 0
         *       },
         *       "assignedUserCount": 0,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1
         *     }
         */
        RoleDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            assignedUserCount: number;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            dataScope: components["schemas"]["DataScope"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            menuIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            permissionCodes: components["schemas"]["PermissionCode"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            roleCode: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            roleId: string;
            siteScope: components["schemas"]["SiteScope"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 RoleInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "roleCode": "mock-example",
         *       "name": "mock-example",
         *       "permissionCodes": [
         *         "dashboard:read"
         *       ],
         *       "menuIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "siteScope": {
         *         "siteIds": [],
         *         "allSites": true
         *       },
         *       "dataScope": {
         *         "siteIds": [],
         *         "allSites": true,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "ownOnly": true,
         *         "scopeVersion": 0
         *       }
         *     }
         */
        RoleInput: {
            dataScope: components["schemas"]["DataScope"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            menuIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            permissionCodes: components["schemas"]["PermissionCode"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            roleCode: string;
            siteScope: components["schemas"]["SiteScope"];
        };
        /**
         * @description 接口模型 RolePage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "roleId": "00000000-0000-4000-8000-000000000001",
         *           "roleCode": "mock-example",
         *           "name": "mock-example",
         *           "assignedUserCount": 0,
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "version": 1
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        RolePage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["RoleSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 RolePermissionsInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "permissionCodes": [
         *         "dashboard:read"
         *       ],
         *       "menuIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        RolePermissionsInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            menuIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            permissionCodes: components["schemas"]["PermissionCode"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 接口模型 RoleScopesInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "siteScope": {
         *         "siteIds": [],
         *         "allSites": true
         *       },
         *       "dataScope": {
         *         "siteIds": [],
         *         "allSites": true,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "ownOnly": true,
         *         "scopeVersion": 0
         *       },
         *       "reason": "mock-example"
         *     }
         */
        RoleScopesInput: {
            dataScope: components["schemas"]["DataScope"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            siteScope: components["schemas"]["SiteScope"];
        };
        /**
         * @description 接口模型 RoleSummary；具体业务范围以使用它的操作为准。
         * @example {
         *       "roleId": "00000000-0000-4000-8000-000000000001",
         *       "roleCode": "mock-example",
         *       "name": "mock-example",
         *       "assignedUserCount": 0,
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1
         *     }
         */
        RoleSummary: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            assignedUserCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            roleCode: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            roleId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 RuleTarget；具体业务范围以使用它的操作为准。
         * @example {
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       }
         *     }
         */
        RuleTarget: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            sourceProfileId?: string;
            target: components["schemas"]["ContextTarget"];
        };
        /** @description 接口模型 RunTrendModelResponse；具体业务范围以使用它的操作为准。 */
        RunTrendModelResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 SearchAuditEventsResponse；具体业务范围以使用它的操作为准。 */
        SearchAuditEventsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["AuditEventPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 SearchCapturesResponse；具体业务范围以使用它的操作为准。 */
        SearchCapturesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["CaptureSearchResponse"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 SearchProfilesResponse；具体业务范围以使用它的操作为准。 */
        SearchProfilesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["ProfileSearchPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 SearchSmsLogsResponse；具体业务范围以使用它的操作为准。 */
        SearchSmsLogsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeliveryPage"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 SeriesMetric；具体业务范围以使用它的操作为准。
         * @example {
         *       "meta": {
         *         "asOf": null,
         *         "status": "READY",
         *         "unit": "mock-example",
         *         "refreshPolicy": "POLL"
         *       },
         *       "series": [
         *         {
         *           "code": "mock-example",
         *           "label": "mock-example",
         *           "points": [
         *             {
         *               "at": "2026-10-07T02:00:00Z",
         *               "value": null
         *             }
         *           ]
         *         }
         *       ]
         *     }
         */
        SeriesMetric: {
            meta: components["schemas"]["MetricMeta"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            series: components["schemas"]["SeriesMetricSeriesItem"][];
        };
        /**
         * @example {
         *       "code": "mock-example",
         *       "label": "mock-example",
         *       "points": [
         *         {
         *           "at": "2026-10-07T02:00:00Z",
         *           "value": null
         *         }
         *       ]
         *     }
         */
        SeriesMetricSeriesItem: {
            code: string;
            label: string;
            points: components["schemas"]["MetricPoint"][];
        };
        /**
         * @description SID只经Set-Cookie；空闲15分钟/绝对8小时，主动操作或显式renew可更新空闲，后台轮询不续命；常规续期不换SID/CSRF，登录/改密/权限提升轮换。单账号1会话，新登录撤销旧会话。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "userId": "00000000-0000-4000-8000-000000000001",
         *       "idleExpiresAt": "2026-10-07T02:00:00Z",
         *       "absoluteExpiresAt": "2026-10-07T02:00:00Z"
         *     }
         */
        SessionData: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            absoluteExpiresAt: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            idleExpiresAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            userId: string;
        };
        /**
         * @example {
         *       "symbol": "A",
         *       "datasetId": "00000000-0000-4000-8000-000000000001",
         *       "datasetVersion": 0
         *     }
         */
        SetOperand: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            datasetId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            datasetVersion: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            symbol: string;
        };
        /**
         * @description INTERSECTION交集、UNION并集、DIFFERENCE有序差集；MIXED使用显式表达式。元素等价键由DatasetElementType固定。
         * @example INTERSECTION
         * @enum {string}
         */
        SetOperator: "INTERSECTION" | "UNION" | "DIFFERENCE" | "MIXED";
        /**
         * @description compatible=false 时 estimatedCount=null，避免伪造 0。
         * @example {
         *       "compatible": true,
         *       "estimatedCount": null,
         *       "estimateAsOf": null,
         *       "warnings": [
         *         "mock-example"
         *       ],
         *       "operandVersions": [
         *         {
         *           "symbol": "A",
         *           "datasetId": "00000000-0000-4000-8000-000000000001",
         *           "datasetVersion": 0
         *         }
         *       ]
         *     }
         */
        SetPreview: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            compatible: boolean;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            estimateAsOf: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            estimatedCount: number | null;
            /**
             * @deprecated
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            leftDatasetVersion?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            operandVersions: components["schemas"]["SetOperand"][];
            /**
             * @deprecated
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            rightDatasetVersion?: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            warnings: string[];
        };
        SetRolePermissionsApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "setRolePermissions";
            pathParameters: {
                /** Format: uuid */
                roleId: string;
            };
            requestBody: components["schemas"]["RolePermissionsInput"];
        };
        /** @description 接口模型 SetRolePermissionsResponse；具体业务范围以使用它的操作为准。 */
        SetRolePermissionsResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["RoleDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        SetRoleScopesApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "setRoleScopes";
            pathParameters: {
                /** Format: uuid */
                roleId: string;
            };
            requestBody: components["schemas"]["RoleScopesInput"];
        };
        /** @description 接口模型 SetRoleScopesResponse；具体业务范围以使用它的操作为准。 */
        SetRoleScopesResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["RoleDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 SetTaskCreated；具体业务范围以使用它的操作为准。
         * @example {
         *       "setTaskId": "00000000-0000-4000-8000-000000000001",
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "status": "PENDING",
         *       "createdAt": "2026-10-07T02:00:00Z"
         *     }
         */
        SetTaskCreated: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            setTaskId: string;
            status: components["schemas"]["TaskStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId: string;
        };
        /**
         * @description 结果未生成时 resultCount=null；成功后为实际计数。
         * @example {
         *       "task": {
         *         "taskId": "00000000-0000-4000-8000-000000000001",
         *         "taskType": "INGESTION",
         *         "status": "PENDING",
         *         "progress": 0,
         *         "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *         "createdAt": "2026-10-07T02:00:00Z",
         *         "retryCount": 0,
         *         "parameterSnapshot": {
         *           "schemaVersion": "mock-example",
         *           "submittedAt": "2026-10-07T02:00:00Z",
         *           "parameterHash": "0000000000000000000000000000000000000000000000000000000000000000"
         *         },
         *         "traceId": "mock-example",
         *         "name": "mock-example"
         *       },
         *       "input": {
         *         "operands": [
         *           {
         *             "symbol": "A",
         *             "datasetId": "00000000-0000-4000-8000-000000000001",
         *             "datasetVersion": 0
         *           },
         *           {
         *             "symbol": "B",
         *             "datasetId": "00000000-0000-4000-8000-000000000001",
         *             "datasetVersion": 0
         *           }
         *         ],
         *         "operator": "INTERSECTION",
         *         "name": "mock-example"
         *       },
         *       "resultCount": null,
         *       "setTaskId": "00000000-0000-4000-8000-000000000001"
         *     }
         */
        SetTaskDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dedupRuleVersion?: string;
            input: components["schemas"]["SetTaskInput"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            resultCount: number | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resultDatasetId?: string;
            /**
             * Format: uuid
             * @description 集合领域ID，不等于task.taskId执行ID。
             */
            setTaskId: string;
            task: components["schemas"]["Task"];
        };
        /**
         * @description symbol唯一且引用现存版本；MIXED必须expression，其他操作禁止expression并按operands顺序左结合（差集保留顺序）。混合语法仅字母集合、括号、∩∪−/-；无括号所有操作同优先级从左至右，禁止eval。结果保持相同elementType，异型422 DATASET_ELEMENT_TYPE_MISMATCH。不得自动跨源关联身份。
         * @example {
         *       "operands": [
         *         {
         *           "symbol": "A",
         *           "datasetId": "00000000-0000-4000-8000-000000000001",
         *           "datasetVersion": 1
         *         },
         *         {
         *           "symbol": "B",
         *           "datasetId": "00000000-0000-4000-8000-000000000002",
         *           "datasetVersion": 1
         *         }
         *       ],
         *       "operator": "MIXED",
         *       "expression": "(A ∪ B) ∩ A",
         *       "name": "交并混合结果"
         *     }
         */
        SetTaskInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            expression?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            operands: components["schemas"]["SetOperand"][];
            operator: components["schemas"]["SetOperator"];
        };
        /**
         * @description 接口模型 SetTaskPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "task": {
         *             "taskId": "00000000-0000-4000-8000-000000000001",
         *             "taskType": "INGESTION",
         *             "status": "PENDING",
         *             "progress": 0,
         *             "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *             "createdAt": "2026-10-07T02:00:00Z",
         *             "retryCount": 0,
         *             "parameterSnapshot": {
         *               "schemaVersion": "mock-example",
         *               "submittedAt": "2026-10-07T02:00:00Z",
         *               "parameterHash": "0000000000000000000000000000000000000000000000000000000000000000"
         *             },
         *             "traceId": "mock-example",
         *             "name": "mock-example"
         *           },
         *           "input": {
         *             "operands": [
         *               {
         *                 "symbol": "A",
         *                 "datasetId": "00000000-0000-4000-8000-000000000001",
         *                 "datasetVersion": 0
         *               },
         *               {
         *                 "symbol": "B",
         *                 "datasetId": "00000000-0000-4000-8000-000000000001",
         *                 "datasetVersion": 0
         *               }
         *             ],
         *             "operator": "INTERSECTION",
         *             "name": "mock-example"
         *           },
         *           "resultCount": null,
         *           "setTaskId": "00000000-0000-4000-8000-000000000001"
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        SetTaskPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["SetTaskDetail"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 SiteDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "siteId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "regionCode": "mock-example",
         *       "deviceCount": 0,
         *       "enabled": true,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1
         *     }
         */
        SiteDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            address?: string | null;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            enabled: boolean;
            location?: components["schemas"]["GeoPoint"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 SiteInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "name": "mock-example",
         *       "regionCode": "mock-example",
         *       "enabled": true
         *     }
         */
        SiteInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            address?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            enabled: boolean;
            location?: components["schemas"]["GeoPoint"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
        };
        /**
         * @description 接口模型 SitePage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "siteId": "00000000-0000-4000-8000-000000000001",
         *           "name": "mock-example",
         *           "regionCode": "mock-example",
         *           "deviceCount": 0,
         *           "enabled": true,
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "version": 1
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        SitePage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["SiteSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description allSites=true时siteIds必须[]，false时[]无范围；管理场所操作使用当前授权范围，新增场所不自动加入显式场所grant。区域到siteId为显式版本映射。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "siteIds": [],
         *       "allSites": true
         *     }
         */
        SiteScope: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            allSites: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds: string[];
        };
        /**
         * @description 接口模型 SiteSummary；具体业务范围以使用它的操作为准。
         * @example {
         *       "siteId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "regionCode": "mock-example",
         *       "deviceCount": 0,
         *       "enabled": true,
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1
         *     }
         */
        SiteSummary: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            deviceCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            enabled: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCode: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 只查询 SMS；通道由路径固定，不接受其它通道值。
         * @example {
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        SmsLogSearchRequest: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cursor?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 20
             */
            pageSize?: number;
            /** @description 敏感收件人精确条件，仅 POST 请求体。 */
            recipientQuery?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default sentAt
             * @enum {string}
             */
            sortBy?: "sentAt" | "deliveryId";
            sortOrder?: components["schemas"]["SortOrder"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            taskName?: string;
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 状态、信号单位、SIM与余额来自版本化厂商profile；标尺/查询能力未提供时为null，不能通用假定0至31。发送/送达计数分开，公开错误码归一化，原始供应商字段不直接透出。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "meta": {
         *         "asOf": null,
         *         "status": "READY",
         *         "unit": "mock-example",
         *         "refreshPolicy": "POLL"
         *       },
         *       "online": null,
         *       "uptimeSeconds": null,
         *       "networkRegistrationCount": null,
         *       "sentSuccessCount": null,
         *       "sentTotalCount": null
         *     }
         */
        SmsModemStatus: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            carrier?: string | null;
            meta: components["schemas"]["MetricMeta"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            networkRegistrationCount: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            online: boolean | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            sentSuccessCount: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            sentTotalCount: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            signalQuality?: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            signalStrength?: number | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            simBalanceDisplay?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            simStatus?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            uptimeSeconds: number | null;
        };
        /**
         * @description 敏感收件地址仅请求体，响应使用Delivery.recipientMasked，不回传明文。
         * @example {
         *       "channel": "SMS",
         *       "address": "+8613800000000"
         *     }
         */
        SmsNotificationTarget: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            address: string;
            /**
             * @description 判别字段固定为 SMS。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            channel: "SMS";
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example ASC
         * @enum {string}
         */
        SortOrder: "ASC" | "DESC";
        SplitProfileApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "splitProfile";
            pathParameters: {
                /** Format: uuid */
                profileId: string;
            };
            requestBody: components["schemas"]["ProfileSplitInput"];
        };
        /** @description 接口模型 SplitProfileResponse；具体业务范围以使用它的操作为准。 */
        SplitProfileResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 SystemComponentStatus；具体业务范围以使用它的操作为准。
         * @example {
         *       "componentCode": "mock-example",
         *       "label": "mock-example",
         *       "status": "UP",
         *       "checkedAt": "2026-10-07T02:00:00Z"
         *     }
         */
        SystemComponentStatus: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            checkedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            componentCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            status: "UP" | "DOWN" | "DEGRADED" | "UNKNOWN";
        };
        /**
         * @description 接口模型 SystemReportPreview；具体业务范围以使用它的操作为准。
         * @example {
         *       "reportType": "SYSTEM_COMPONENT_STATUS",
         *       "components": [
         *         {
         *           "componentCode": "mock-example",
         *           "label": "mock-example",
         *           "status": "UP",
         *           "checkedAt": "2026-10-07T02:00:00Z"
         *         }
         *       ],
         *       "asOf": "2026-10-07T02:00:00Z",
         *       "previewKind": "SYSTEM"
         *     }
         */
        SystemReportPreview: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            components: components["schemas"]["SystemComponentStatus"][];
            /**
             * @description 固定预览类型，与reportType细分报表类型分开。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            previewKind: "SYSTEM";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            reportType: "SYSTEM_COMPONENT_STATUS";
        };
        /**
         * @description TARGET按代理目标相等；CAPTURE按captureId相等，未解析目标时省略target，仍允许保留原始记录。
         * @example {
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "addedAt": "2026-10-07T02:00:00Z",
         *       "itemId": "00000000-0000-4000-8000-000000000001",
         *       "elementType": "TARGET",
         *       "displayTarget": "mock-example"
         *     }
         */
        TargetDatasetItem: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            addedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /**
             * @description 判别字段固定为 TARGET。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            elementType: "TARGET";
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            itemId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            sourceCaptureId?: string;
            target: components["schemas"]["ContextTarget"];
        };
        /**
         * @description TARGET只接受已授权代理目标。
         * @example {
         *       "elementType": "TARGET",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       }
         *     }
         */
        TargetDatasetItemInput: {
            /**
             * @description 判别字段固定为 TARGET。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            elementType: "TARGET";
            /** Format: uuid */
            sourceCaptureId?: string;
            target: components["schemas"]["ContextTarget"];
        };
        /**
         * @description 敏感精确标识只在请求体；FACE使用已有targetId的UUID字符串，不传人脸特征向量。；IMSI/IMEI精确值只在body；MAC规范化和PLATE省份/字符策略P-004。FACE value只登记模型产出的既有目标代理，不把照片自动确定为同一人，算法未确定返回REQUIREMENT_PENDING。；IMSI/IMEI精确值只在body；MAC规范化和PLATE省份/字符策略P-004。FACE value只登记模型产出的既有目标代理，不把照片自动确定为同一人，算法未确定返回REQUIREMENT_PENDING。
         * @example {
         *       "targetType": "IMSI",
         *       "value": "mock-example"
         *     }
         */
        TargetIdentifier: {
            targetType: components["schemas"]["TargetType"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            value: string;
        };
        /**
         * @description 成功必须返回target；NOT_FOUND/REJECTED省略target并返回errorCode（REJECTED）或无命中。禁止原样回传敏感输入。
         * @example {
         *       "index": 0,
         *       "status": "FOUND"
         *     }
         */
        TargetResolution: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget?: string;
            errorCode?: components["schemas"]["ErrorCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            index: number;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            status: "FOUND" | "REGISTERED" | "NOT_FOUND" | "REJECTED";
            target?: components["schemas"]["ContextTarget"];
        };
        /**
         * @example {
         *       "items": [
         *         {
         *           "index": 0,
         *           "status": "FOUND"
         *         }
         *       ]
         *     }
         */
        TargetResolutionData: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["TargetResolution"][];
        };
        /**
         * @description registerMissing=false仅查找；true可登记尚无采集的布控/库内人员目标。规范化规则未签认类型返回REQUIREMENT_PENDING。
         * @example {
         *       "items": [
         *         {
         *           "targetType": "IMSI",
         *           "value": "mock-example"
         *         }
         *       ]
         *     }
         */
        TargetResolutionInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["TargetIdentifier"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            purpose?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default false
             */
            registerMissing?: boolean;
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example IMSI
         * @enum {string}
         */
        TargetType: "IMSI" | "IMEI" | "MAC" | "PLATE" | "FACE";
        /**
         * @description 六态PENDING/QUEUED/RUNNING/SUCCESS/FAILED/CANCELLED。普通执行30分钟、复杂2小时从实际RUNNING开始，排队预算分别5/15分钟；类型分类见policy。SUCCESS仅说明本任务工作流，不代表外部短信已送达；保存一年（12个自然月）且保护引用。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "taskType": "INGESTION",
         *       "status": "PENDING",
         *       "progress": 0,
         *       "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "retryCount": 0,
         *       "parameterSnapshot": {
         *         "schemaVersion": "mock-example",
         *         "submittedAt": "2026-10-07T02:00:00Z",
         *         "parameterHash": "0000000000000000000000000000000000000000000000000000000000000000"
         *       },
         *       "traceId": "mock-example",
         *       "name": "mock-example"
         *     }
         */
        Task: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            algorithmVersion?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            cancelRequestedAt?: string | null;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            errorCode?: components["schemas"]["ErrorCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            errorMessage?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            finishedAt?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ownerUserId: string;
            parameterSnapshot: components["schemas"]["TaskParameterSnapshot"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            progress: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resourceId?: string;
            /** @enum {string} */
            resourceType?: "INGESTION_BATCH" | "ANALYSIS_TASK" | "SET_TASK" | "DATASET" | "PERSON_LIBRARY" | "DEVICE" | "PROFILE" | "MODEL_RUN" | "DEVICE_COMMAND" | "REPORT" | "NOTIFICATION_TASK" | "EXPORT";
            resultReference?: components["schemas"]["TaskResultReference"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            retryCount: number;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            startedAt?: string | null;
            status: components["schemas"]["TaskStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            taskType: "INGESTION" | "BACKFILL" | "EXPORT" | "FACE_SEARCH" | "ANALYSIS" | "DATASET_IMPORT" | "SET_OPERATION" | "PROFILE_MERGE" | "PROFILE_SPLIT" | "MODEL_RUN" | "DEVICE_COMMAND" | "REPORT" | "NOTIFICATION" | "PERSON_LIBRARY_IMPORT" | "DEVICE_IMPORT";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @description 接口模型 TaskAttempt；具体业务范围以使用它的操作为准。
         * @example {
         *       "attemptNo": 1,
         *       "startedAt": "2026-10-07T02:00:00Z"
         *     }
         */
        TaskAttempt: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            attemptNo: number;
            errorCode?: components["schemas"]["ErrorCode"];
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            finishedAt?: string | null;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            result?: "SUCCESS" | "FAILED" | "CANCELLED";
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            startedAt: string;
        };
        /**
         * @description 接口模型 TaskAttemptsData；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "attemptNo": 1,
         *           "startedAt": "2026-10-07T02:00:00Z"
         *         }
         *       ]
         *     }
         */
        TaskAttemptsData: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["TaskAttempt"][];
        };
        /**
         * @description 创建领域资源时同时返回 resourceId/resourceType；具体资源可用相应详情接口读取。
         * @example {
         *       "taskId": "00000000-0000-4000-8000-000000000001",
         *       "status": "PENDING",
         *       "createdAt": "2026-10-07T02:00:00Z"
         *     }
         */
        TaskCreated: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resourceId?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            resourceType?: "EXPORT" | "FACE_SEARCH" | "DATASET_IMPORT" | "PERSON_LIBRARY_IMPORT" | "DEVICE_IMPORT" | "PROFILE_MERGE" | "PROFILE_SPLIT" | "MODEL_RUN" | "DEVICE_COMMAND" | "REPORT" | "NOTIFICATION" | "INGESTION" | "BACKFILL";
            status: components["schemas"]["TaskStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            taskId: string;
        };
        /**
         * @description 接口模型 TaskPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "taskId": "00000000-0000-4000-8000-000000000001",
         *           "taskType": "INGESTION",
         *           "status": "PENDING",
         *           "progress": 0,
         *           "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "retryCount": 0,
         *           "parameterSnapshot": {
         *             "schemaVersion": "mock-example",
         *             "submittedAt": "2026-10-07T02:00:00Z",
         *             "parameterHash": "0000000000000000000000000000000000000000000000000000000000000000"
         *           },
         *           "traceId": "mock-example",
         *           "name": "mock-example"
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        TaskPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["Task"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 敏感参数详情由领域任务端点返回，不在通用任务列表泄露。
         * @example {
         *       "schemaVersion": "mock-example",
         *       "submittedAt": "2026-10-07T02:00:00Z",
         *       "parameterHash": "0000000000000000000000000000000000000000000000000000000000000000"
         *     }
         */
        TaskParameterSnapshot: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterHash: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            schemaVersion: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            submittedAt: string;
        };
        /**
         * @description SUCCESS时按resultType导航：ANALYSIS结果ID=analysisTaskId；SET_OPERATION=setTaskId；FACE_SEARCH=taskId；REPORT=reportId；DEVICE_COMMAND=taskId；MODEL_RUN=taskId，且modelId必须返回；INGESTION=batchId；NOTIFICATION=notificationTaskId。EXPORT必须fileAssetId。三个IMPORT调用/tasks/{taskId}/import-result；PROFILE_MERGE/SPLIT使用profileIds。
         * @example {
         *       "resultType": "ANALYSIS"
         *     }
         */
        TaskResultReference: {
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            expiresAt?: string | null;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            fileAssetId?: string;
            /**
             * Format: uuid
             * @description MODEL_RUN必返modelId，resultId为执行taskId；结果查询按modelId和taskId关联。
             */
            modelId?: string;
            profileIds?: string[];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            resultId?: string;
            /** @enum {string} */
            resultType: "ANALYSIS" | "SET_OPERATION" | "FACE_SEARCH" | "EXPORT" | "REPORT" | "DEVICE_COMMAND" | "MODEL_RUN" | "DATASET_IMPORT" | "PERSON_LIBRARY_IMPORT" | "DEVICE_IMPORT" | "PROFILE_MERGE" | "PROFILE_SPLIT" | "INGESTION" | "BACKFILL" | "NOTIFICATION";
        };
        /**
         * @description 文件导入类任务的逐行验证错误；不得返回敏感原始行。
         * @example {
         *       "rowNumber": 1,
         *       "code": "VALIDATION_ERROR",
         *       "reason": "mock-example"
         *     }
         */
        TaskRowError: {
            code: components["schemas"]["ErrorCode"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            field?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            rowNumber: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            sourceRecordKey?: string | null;
        };
        /**
         * @description nextCursor 是不透明游标；不默认返回全量 total。
         * @example {
         *       "items": [
         *         {
         *           "rowNumber": 1,
         *           "code": "VALIDATION_ERROR",
         *           "reason": "mock-example"
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        TaskRowErrorPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["TaskRowError"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
        };
        /**
         * @description 统一执行状态：PENDING→QUEUED→RUNNING→SUCCESS|FAILED|CANCELLED；运行取消先写cancelRequestedAt；FAILED重试同taskId增加attemptNo。只用于一次执行，不用于周期计划。
         * @example PENDING
         * @enum {string}
         */
        TaskStatus: "PENDING" | "QUEUED" | "RUNNING" | "SUCCESS" | "FAILED" | "CANCELLED";
        /**
         * @description 接口模型 TextDeviceParameter；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "label": "mock-example",
         *       "valueType": "TEXT",
         *       "textValue": "mock-example",
         *       "readOnly": true,
         *       "updatedAt": "2026-10-07T02:00:00Z"
         *     }
         */
        TextDeviceParameter: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            label: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            readOnly: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            textValue: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /**
             * @description 判别字段固定为 TEXT。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "TEXT";
        };
        /**
         * @description 接口模型 TextDeviceParameterChange；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "TEXT",
         *       "textValue": "mock-example"
         *     }
         */
        TextDeviceParameterChange: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            textValue: string;
            /**
             * @description 判别字段固定为 TEXT。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "TEXT";
        };
        /**
         * @description 接口模型 TextModelParameterValue；具体业务范围以使用它的操作为准。
         * @example {
         *       "parameterCode": "mock-example",
         *       "valueType": "TEXT",
         *       "textValue": "mock-example"
         *     }
         */
        TextModelParameterValue: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterCode: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            textValue: string;
            /**
             * @description 判别字段固定为 TEXT。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            valueType: "TEXT";
        };
        /**
         * @description ISO8601带时区，[from,to)；from<to，否则400 VALIDATION_ERROR。UTC存储返回Z，Asia/Shanghai显示。
         * @example {
         *       "from": "2026-10-07T02:00:00Z",
         *       "to": "2026-10-07T03:00:00Z"
         *     }
         */
        TimeRange: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            from: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            to: string;
        };
        /**
         * @description segmentGeometry 仅在当前页至少有两个有效点位时出现；全轨迹分页拼接按 captureTime、captureId 排序。；只有单目标且当前页至少两点才返回segmentGeometry；多目标按target分组由前端绘线，不能跨目标连线。
         * @example {
         *       "items": [
         *         {
         *           "captureId": "00000000-0000-4000-8000-000000000001",
         *           "captureTime": "2026-10-07T02:00:00Z",
         *           "location": {
         *             "type": "Point",
         *             "coordinates": [
         *               116.4,
         *               39.9
         *             ]
         *           },
         *           "dataType": "FENCE",
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           }
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1,
         *       "sortBy": "captureTime",
         *       "sortOrder": "ASC"
         *     }
         */
        TrajectoryPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["TrajectoryPoint"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            segmentGeometry?: components["schemas"]["GeoLineString"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            sortBy: "captureTime" | "captureId";
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default ASC
             * @enum {string}
             */
            sortOrder: "ASC";
        };
        /**
         * @description 接口模型 TrajectoryPoint；具体业务范围以使用它的操作为准。
         * @example {
         *       "captureId": "00000000-0000-4000-8000-000000000001",
         *       "captureTime": "2026-10-07T02:00:00Z",
         *       "location": {
         *         "type": "Point",
         *         "coordinates": [
         *           116.4,
         *           39.9
         *         ]
         *       },
         *       "dataType": "FENCE",
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       }
         *     }
         */
        TrajectoryPoint: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            captureId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            captureTime: string;
            dataType: components["schemas"]["DataType"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            deviceId?: string;
            location: components["schemas"]["GeoPoint"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            siteId?: string;
            target: components["schemas"]["ContextTarget"];
        };
        /**
         * @description 省略target查询Context内全部目标，点各带target；最多100条每页，超预算422 DATA_QUERY_TOO_BROAD。多目标点不得连为一条线。
         * @example {
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "timeRange": {
         *         "from": "2026-10-07T02:00:00Z",
         *         "to": "2026-10-07T03:00:00Z"
         *       }
         *     }
         */
        TrajectoryRequest: {
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            cursor?: string;
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default 20
             */
            pageSize?: number;
            target?: components["schemas"]["ContextTarget"];
            timeRange: components["schemas"]["TimeRange"];
        };
        /**
         * @description 启用模型按已确认matchingRules自动标签，worker记录parameterVersionId；未确认算法enabled必须false。前端从parameterDefinitions构建控件，不使用Mock天数/次数作生产算法。
         * @example {
         *       "modelId": "00000000-0000-4000-8000-000000000001",
         *       "code": "mock-example",
         *       "name": "mock-example",
         *       "modelType": "RESIDENCE",
         *       "enabled": true,
         *       "tagCodes": [
         *         "mock-example"
         *       ],
         *       "effectiveDataFilter": {
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "captureStatuses": [
         *           "VALID"
         *         ]
         *       },
         *       "matchingRules": [
         *         {
         *           "ruleCode": "mock-example",
         *           "label": "mock-example",
         *           "algorithmVersion": "mock-example",
         *           "outputTagCodes": [
         *             "mock-example"
         *           ],
         *           "description": "mock-example"
         *         }
         *       ],
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1,
         *       "parameterDefinitions": [
         *         {
         *           "parameterCode": "mock-example",
         *           "label": "mock-example",
         *           "valueType": "TEXT",
         *           "required": true,
         *           "description": "mock-example"
         *         }
         *       ]
         *     }
         */
        TrendModel: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            code: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            currentParameterVersionId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description?: string | null;
            effectiveDataFilter: components["schemas"]["ModelEffectiveDataFilter"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            enabled: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            matchingRules: components["schemas"]["ModelRuleDescriptor"][];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            modelId: string;
            /**
             * @description PDF只明确常驻模型，未加入无业务来源的模型类型。
             * @enum {string}
             */
            modelType: "RESIDENCE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            parameterDefinitions: components["schemas"]["ModelParameterDescriptor"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            tagCodes: string[];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 TrendModelInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "code": "mock-example",
         *       "name": "mock-example",
         *       "modelType": "RESIDENCE",
         *       "tagCodes": [
         *         "mock-example"
         *       ],
         *       "effectiveDataFilter": {
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "captureStatuses": [
         *           "VALID"
         *         ]
         *       }
         *     }
         */
        TrendModelInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            code: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            description?: string;
            effectiveDataFilter: components["schemas"]["ModelEffectiveDataFilter"];
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @default false
             */
            enabled?: boolean;
            /**
             * @description PDF只明确常驻模型，未加入无业务来源的模型类型。
             * @enum {string}
             */
            modelType: "RESIDENCE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            tagCodes: string[];
        };
        /**
         * @description 接口模型 TrendModelPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "modelId": "00000000-0000-4000-8000-000000000001",
         *           "code": "mock-example",
         *           "name": "mock-example",
         *           "modelType": "RESIDENCE",
         *           "enabled": true,
         *           "tagCodes": [
         *             "mock-example"
         *           ],
         *           "effectiveDataFilter": {
         *             "dataTypes": [
         *               "FENCE"
         *             ],
         *             "captureStatuses": [
         *               "VALID"
         *             ]
         *           },
         *           "matchingRules": [
         *             {
         *               "ruleCode": "mock-example",
         *               "label": "mock-example",
         *               "algorithmVersion": "mock-example",
         *               "outputTagCodes": [
         *                 "mock-example"
         *               ],
         *               "description": "mock-example"
         *             }
         *           ],
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "version": 1,
         *           "parameterDefinitions": [
         *             {
         *               "parameterCode": "mock-example",
         *               "label": "mock-example",
         *               "valueType": "TEXT",
         *               "required": true,
         *               "description": "mock-example"
         *             }
         *           ]
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        TrendModelPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["TrendModel"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @example {
         *       "dataType": "FENCE",
         *       "ranking": {
         *         "meta": {
         *           "asOf": null,
         *           "status": "READY",
         *           "unit": "mock-example",
         *           "refreshPolicy": "POLL"
         *         },
         *         "items": [
         *           {
         *             "key": "mock-example",
         *             "label": "mock-example",
         *             "value": 0
         *           }
         *         ]
         *       }
         *     }
         */
        TypedRankMetric: {
            dataType: components["schemas"]["DataType"];
            ranking: components["schemas"]["RankMetric"];
        };
        /** @description 接口模型 UpdateDatasetResponse；具体业务范围以使用它的操作为准。 */
        UpdateDatasetResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DatasetDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 UpdateDeviceGroupResponse；具体业务范围以使用它的操作为准。 */
        UpdateDeviceGroupResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceGroupDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        UpdateDeviceParametersApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "updateDeviceParameters";
            pathParameters: {
                /** Format: uuid */
                deviceId: string;
            };
            requestBody: components["schemas"]["DeviceParameterUpdateInput"];
        };
        /** @description 接口模型 UpdateDeviceParametersResponse；具体业务范围以使用它的操作为准。 */
        UpdateDeviceParametersResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TaskCreated"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 UpdateDeviceResponse；具体业务范围以使用它的操作为准。 */
        UpdateDeviceResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["DeviceDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        UpdateNotificationTaskResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["NotificationTaskDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 UpdatePersonLibraryResponse；具体业务范围以使用它的操作为准。 */
        UpdatePersonLibraryResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["PersonLibraryDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        UpdateRoleApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "updateRole";
            pathParameters: {
                /** Format: uuid */
                roleId: string;
            };
            requestBody: components["schemas"]["RoleInput"];
        };
        /** @description 接口模型 UpdateRoleResponse；具体业务范围以使用它的操作为准。 */
        UpdateRoleResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["RoleDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 UpdateSiteResponse；具体业务范围以使用它的操作为准。 */
        UpdateSiteResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["SiteDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 UpdateTrendModelResponse；具体业务范围以使用它的操作为准。 */
        UpdateTrendModelResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["TrendModel"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        UpdateUserApprovalProposal: {
            ifMatch: string;
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            operationId: "updateUser";
            pathParameters: {
                /** Format: uuid */
                userId: string;
            };
            requestBody: components["schemas"]["UserUpdateInput"];
        };
        /** @description 接口模型 UpdateUserResponse；具体业务范围以使用它的操作为准。 */
        UpdateUserResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["UserDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /** @description 接口模型 UpdateWatchRuleResponse；具体业务范围以使用它的操作为准。 */
        UpdateWatchRuleResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["WatchRuleDetail"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @example {
         *       "category": "FACE_SEARCH",
         *       "file": "binary-file"
         *     }
         */
        UploadFileRequest: {
            /** @enum {string} */
            category: "FACE_SEARCH" | "DATASET_IMPORT" | "DEVICE_IMPORT" | "PERSON_LIBRARY_IMPORT" | "PROFILE_PORTRAIT" | "RAW_INGESTION";
            /** Format: binary */
            file: string;
        };
        /** @description 接口模型 UploadFileResponse；具体业务范围以使用它的操作为准。 */
        UploadFileResponse: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            code: "0";
            data: components["schemas"]["FileUploadData"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            message: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            traceId: string;
        };
        /**
         * @example {
         *       "category": "FACE_SEARCH",
         *       "mimeTypes": [
         *         "image/jpeg",
         *         "image/png"
         *       ],
         *       "extensions": [
         *         "jpg",
         *         "jpeg",
         *         "png"
         *       ],
         *       "maxBytes": 5242880,
         *       "enabled": false
         *     }
         */
        UploadPolicy: {
            /**
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             * @enum {string}
             */
            category: "FACE_SEARCH" | "DATASET_IMPORT" | "DEVICE_IMPORT" | "PERSON_LIBRARY_IMPORT" | "PROFILE_PORTRAIT" | "REPORT" | "EXPORT" | "CAPTURE_IMAGE" | "RAW_INGESTION";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            enabled: boolean;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            extensions: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            maxBytes: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            mimeTypes: string[];
        };
        /**
         * @description 接口模型 UserCreateInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "userCode": "mock-example",
         *       "loginName": "mock-example",
         *       "displayName": "mock-example",
         *       "initialPassword": "Example-password-2026!",
         *       "roleIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "siteScope": {
         *         "siteIds": [],
         *         "allSites": true
         *       },
         *       "dataScope": {
         *         "siteIds": [],
         *         "allSites": true,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "ownOnly": true,
         *         "scopeVersion": 0
         *       }
         *     }
         */
        UserCreateInput: {
            dataScope: components["schemas"]["DataScope"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayName: string;
            /**
             * Format: password
             * @description 可选字段省略表示未提供；仅nullable字段允许null。
             */
            initialPassword: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            loginName: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            roleIds: string[];
            siteScope: components["schemas"]["SiteScope"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            userCode: string;
        };
        /**
         * @description 接口模型 UserDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "userId": "00000000-0000-4000-8000-000000000001",
         *       "userCode": "mock-example",
         *       "loginName": "mock-example",
         *       "displayName": "mock-example",
         *       "status": "ACTIVE",
         *       "roleIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "siteScope": {
         *         "siteIds": [],
         *         "allSites": true
         *       },
         *       "dataScope": {
         *         "siteIds": [],
         *         "allSites": true,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "ownOnly": true,
         *         "scopeVersion": 0
         *       },
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1
         *     }
         */
        UserDetail: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            dataScope: components["schemas"]["DataScope"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayName: string;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            lastLoginAt?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            loginName: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            roleIds: string[];
            siteScope: components["schemas"]["SiteScope"];
            status: components["schemas"]["UserStatus"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            userCode: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            userId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 UserListItem；具体业务范围以使用它的操作为准。
         * @example {
         *       "userId": "00000000-0000-4000-8000-000000000001",
         *       "userCode": "mock-example",
         *       "loginName": "mock-example",
         *       "displayName": "mock-example",
         *       "status": "ACTIVE",
         *       "roleIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1
         *     }
         */
        UserListItem: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayName: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            loginName: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            roleIds: string[];
            status: components["schemas"]["UserStatus"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            userCode: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            userId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 接口模型 UserPage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "userId": "00000000-0000-4000-8000-000000000001",
         *           "userCode": "mock-example",
         *           "loginName": "mock-example",
         *           "displayName": "mock-example",
         *           "status": "ACTIVE",
         *           "roleIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "version": 1
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        UserPage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["UserListItem"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 接口模型 UserStateInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "reason": "mock-example"
         *     }
         */
        UserStateInput: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example ACTIVE
         * @enum {string}
         */
        UserStatus: "ACTIVE" | "DISABLED";
        /**
         * @description 接口模型 UserSummary；具体业务范围以使用它的操作为准。
         * @example {
         *       "userId": "00000000-0000-4000-8000-000000000001",
         *       "loginName": "mock-example",
         *       "displayName": "mock-example",
         *       "status": "ACTIVE"
         *     }
         */
        UserSummary: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayName: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            loginName: string;
            status: components["schemas"]["UserStatus"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            userId: string;
        };
        /**
         * @description 接口模型 UserUpdateInput；具体业务范围以使用它的操作为准。
         * @example {
         *       "displayName": "mock-example",
         *       "roleIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "siteScope": {
         *         "siteIds": [],
         *         "allSites": true
         *       },
         *       "dataScope": {
         *         "siteIds": [],
         *         "allSites": true,
         *         "dataTypes": [
         *           "FENCE"
         *         ],
         *         "ownOnly": true,
         *         "scopeVersion": 0
         *       }
         *     }
         */
        UserUpdateInput: {
            dataScope: components["schemas"]["DataScope"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayName: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            roleIds: string[];
            siteScope: components["schemas"]["SiteScope"];
        };
        /**
         * @description 只通过受权请求体提交；不得覆盖原始采集证据。
         * @example {}
         */
        VehicleProfileCorrection: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            plateColor?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            plateNumber?: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            vehicleImageAssetId?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            vehicleType?: string;
        };
        /**
         * @description 仅现有白名单字段，reason/evidence必填；车牌修正不自动合档，不改写设备原始采集，权限与修订谱系按P-016。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "profileType": "VEHICLE",
         *       "vehicle": {},
         *       "supportingEvidenceIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "reason": "mock-example"
         *     }
         */
        VehicleProfileCorrectionInput: {
            /**
             * @description 判别字段固定为 VEHICLE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            profileType: "VEHICLE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            reason: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            supportingEvidenceIds: string[];
            vehicle: components["schemas"]["VehicleProfileCorrection"];
        };
        /**
         * @description 档案类型与详情字段一一对应；敏感身份值默认脱敏。
         * @example {
         *       "profileId": "00000000-0000-4000-8000-000000000001",
         *       "profileType": "VEHICLE",
         *       "displayLabel": "mock-example",
         *       "linkedTargets": [
         *         {
         *           "linkId": "00000000-0000-4000-8000-000000000001",
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "displayTarget": "mock-example",
         *           "confidence": null,
         *           "evidenceIds": [
         *             "00000000-0000-4000-8000-000000000001"
         *           ],
         *           "source": "mock-example",
         *           "reviewStatus": "UNREVIEWED"
         *         }
         *       ],
         *       "evidenceCount": 0,
         *       "captureCount": 0,
         *       "siteCount": 0,
         *       "confidence": null,
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "version": 1,
         *       "vehicle": {
         *         "plateMasked": "mock-example"
         *       },
         *       "regionCount": 0,
         *       "visitedRegionCodes": [
         *         "mock-example"
         *       ]
         *     }
         */
        VehicleProfileDetail: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            confidence: number | null;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayLabel: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            evidenceCount: number;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            firstSeenAt?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            lastSeenAt?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            linkedTargets: components["schemas"]["ProfileTargetLink"][];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            profileId: string;
            /**
             * @description 判别字段固定为 VEHICLE。OpenAPI 3.0以锚定pattern约束常量，避免Spring oneOf接口String与嵌套Enum返回类型不兼容；联合discriminator.mapping保留强类型分派。 (enum property replaced by openapi-typescript)
             * @enum {string}
             */
            profileType: "VEHICLE";
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteCount: number;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            vehicle: components["schemas"]["VehicleProfileInfo"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            visitedRegionCodes: string[];
        };
        /**
         * @description 接口模型 VehicleProfileInfo；具体业务范围以使用它的操作为准。
         * @example {
         *       "plateMasked": "mock-example"
         *     }
         */
        VehicleProfileInfo: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            plateColor?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            plateMasked: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            vehicleImageAssetId?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            vehicleType?: string | null;
        };
        /**
         * @description 激活 DRAFT 前须校验规则及时间区间；通知、匹配算法待 P-014/P-015。
         * @example {
         *       "effectiveFrom": "2026-10-07T02:00:00Z",
         *       "effectiveUntil": "2026-10-08T02:00:00Z"
         *     }
         */
        WatchActivateInput: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            effectiveFrom: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            effectiveUntil: string;
        };
        /**
         * @description 通道通知与平台消息同时产生；频控和模板策略待P-015，不暴露供应商字段。
         * @example {
         *       "targets": [
         *         {
         *           "channel": "SMS",
         *           "address": "+8613800000000"
         *         }
         *       ],
         *       "templateCode": "mock-example",
         *       "platformUserIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ]
         *     }
         */
        WatchNotificationPlan: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            platformUserIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targets: components["schemas"]["NotificationTarget"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            templateCode: string;
        };
        /**
         * @example {
         *       "targets": [
         *         {
         *           "channel": "SMS",
         *           "recipientMasked": "mock-example",
         *           "recipientId": "00000000-0000-4000-8000-000000000001"
         *         }
         *       ],
         *       "templateCode": "mock-example",
         *       "platformUserIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ]
         *     }
         */
        WatchNotificationSnapshot: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            platformUserIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targets: components["schemas"]["NotificationRecipientSummary"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            templateCode: string;
        };
        /**
         * @description EXACT_TARGET_V1匹配同型targetId；跨源只用已人工确认profile_link。同规则/修订/目标5分钟去重外发，全部命中事实保留；编辑新revision，撤销阻止未来未发送动作。 [PD-2026-10-19.v1，待甲方确认]
         * @example {
         *       "targets": [
         *         {
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           }
         *         }
         *       ],
         *       "siteIds": [
         *         "00000000-0000-4000-8000-000000000001"
         *       ],
         *       "dataTypes": [
         *         "FENCE"
         *       ]
         *     }
         */
        WatchRuleConditions: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            dataTypes: components["schemas"]["DataType"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            matchRuleCode?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            matchRuleVersion?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteIds: string[];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            targets: components["schemas"]["RuleTarget"][];
        };
        /**
         * @description 接口模型 WatchRuleDetail；具体业务范围以使用它的操作为准。
         * @example {
         *       "ruleId": "00000000-0000-4000-8000-000000000001",
         *       "name": "mock-example",
         *       "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *       "version": 0,
         *       "status": "DRAFT",
         *       "conditions": {
         *         "targets": [
         *           {
         *             "target": {
         *               "targetId": "00000000-0000-4000-8000-000000000001",
         *               "targetType": "IMSI"
         *             }
         *           }
         *         ],
         *         "siteIds": [
         *           "00000000-0000-4000-8000-000000000001"
         *         ],
         *         "dataTypes": [
         *           "FENCE"
         *         ]
         *       },
         *       "createdAt": "2026-10-07T02:00:00Z",
         *       "updatedAt": "2026-10-07T02:00:00Z",
         *       "hitCount": 0,
         *       "ownerLoginName": "mock-example"
         *     }
         */
        WatchRuleDetail: {
            conditions: components["schemas"]["WatchRuleConditions"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId?: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            createdAt: string;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            effectiveFrom?: string | null;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            effectiveUntil?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            hitCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            notification?: components["schemas"]["WatchNotificationSnapshot"];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            ownerLoginName: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ownerUserId: string;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ruleId: string;
            status: components["schemas"]["WatchStatus"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            updatedAt: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description 创建为 DRAFT；生效时间与激活条件由安全/业务签认。；notification省略代表不发送第三方通知；若提供全部通道收件人与平台用户。创建禁止RETAINED。
         * @example {
         *       "name": "mock-example",
         *       "contextId": "00000000-0000-4000-8000-000000000001",
         *       "conditions": {
         *         "targets": [
         *           {
         *             "target": {
         *               "targetId": "00000000-0000-4000-8000-000000000001",
         *               "targetType": "IMSI"
         *             }
         *           }
         *         ],
         *         "siteIds": [
         *           "00000000-0000-4000-8000-000000000001"
         *         ],
         *         "dataTypes": [
         *           "FENCE"
         *         ]
         *       }
         *     }
         */
        WatchRuleInput: {
            conditions: components["schemas"]["WatchRuleConditions"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            contextId: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            effectiveFrom?: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            effectiveUntil?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            notification?: components["schemas"]["WatchNotificationPlan"];
        };
        /**
         * @description 接口模型 WatchRulePage；具体业务范围以使用它的操作为准。
         * @example {
         *       "items": [
         *         {
         *           "ruleId": "00000000-0000-4000-8000-000000000001",
         *           "name": "mock-example",
         *           "ownerUserId": "00000000-0000-4000-8000-000000000001",
         *           "version": 0,
         *           "status": "DRAFT",
         *           "conditions": {
         *             "targets": [
         *               {
         *                 "target": {
         *                   "targetId": "00000000-0000-4000-8000-000000000001",
         *                   "targetType": "IMSI"
         *                 }
         *               }
         *             ],
         *             "siteIds": [
         *               "00000000-0000-4000-8000-000000000001"
         *             ],
         *             "dataTypes": [
         *               "FENCE"
         *             ]
         *           },
         *           "createdAt": "2026-10-07T02:00:00Z",
         *           "updatedAt": "2026-10-07T02:00:00Z",
         *           "hitCount": 0,
         *           "ownerLoginName": "mock-example"
         *         }
         *       ],
         *       "page": 1,
         *       "pageSize": 1,
         *       "total": 0
         *     }
         */
        WatchRulePage: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["WatchRuleDetail"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            page: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            total: number;
        };
        /**
         * @description 告警命中时的不可变规则版本。
         * @example {
         *       "ruleId": "00000000-0000-4000-8000-000000000001",
         *       "version": 0,
         *       "name": "mock-example",
         *       "conditions": {
         *         "targets": [
         *           {
         *             "target": {
         *               "targetId": "00000000-0000-4000-8000-000000000001",
         *               "targetType": "IMSI"
         *             }
         *           }
         *         ],
         *         "siteIds": [
         *           "00000000-0000-4000-8000-000000000001"
         *         ],
         *         "dataTypes": [
         *           "FENCE"
         *         ]
         *       },
         *       "effectiveFrom": "2026-10-07T02:00:00Z",
         *       "effectiveUntil": "2026-10-08T02:00:00Z"
         *     }
         */
        WatchRuleSnapshot: {
            conditions: components["schemas"]["WatchRuleConditions"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            effectiveFrom: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            effectiveUntil: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name: string;
            notification?: components["schemas"]["WatchNotificationSnapshot"];
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ruleId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            version: number;
        };
        /**
         * @description ACTIVE 规则编辑产生新版本；历史命中仍引用原版本。；未传notification保留既有设置，提供时全部替换，可用RETAINED逐一保留现有地址。
         * @example {}
         */
        WatchRuleUpdate: {
            conditions?: components["schemas"]["WatchRuleConditions"];
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            effectiveFrom?: string;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            effectiveUntil?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            name?: string;
            notification?: components["schemas"]["WatchNotificationPlan"];
        };
        /**
         * @description 实时命中单独由 /alerts 查询；最终统计仅在规则结束后 final=true。
         * @example {
         *       "ruleId": "00000000-0000-4000-8000-000000000001",
         *       "ruleVersion": 0,
         *       "final": true,
         *       "hitCount": 0,
         *       "distinctTargetCount": 0,
         *       "siteCount": 0,
         *       "regionCount": 0,
         *       "asOf": "2026-10-07T02:00:00Z",
         *       "items": [
         *         {
         *           "target": {
         *             "targetId": "00000000-0000-4000-8000-000000000001",
         *             "targetType": "IMSI"
         *           },
         *           "displayTarget": "mock-example",
         *           "captureCount": 0,
         *           "siteCount": 0,
         *           "regionCount": 0
         *         }
         *       ],
         *       "nextCursor": null,
         *       "pageSize": 1
         *     }
         */
        WatchStatistics: {
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            asOf: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            distinctTargetCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            final: boolean;
            /**
             * Format: date-time
             * @description RFC 3339，必须带时区偏移；服务端按 UTC 存储。
             */
            firstSeenAt?: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            hitCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            items: components["schemas"]["WatchTargetStatistics"][];
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            nextCursor: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            pageSize: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCount: number;
            /**
             * Format: uuid
             * @description 平台内部唯一UUID，不能使用业务号码代替。
             */
            ruleId: string;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            ruleVersion: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteCount: number;
        };
        /**
         * @description 有限状态/类型目录，英文值稳定，展示文本来自字典。
         * @example DRAFT
         * @enum {string}
         */
        WatchStatus: "DRAFT" | "ACTIVE" | "REVOKED" | "FINISHED";
        /**
         * @example {
         *       "target": {
         *         "targetId": "00000000-0000-4000-8000-000000000001",
         *         "targetType": "IMSI"
         *       },
         *       "displayTarget": "mock-example",
         *       "captureCount": 0,
         *       "siteCount": 0,
         *       "regionCount": 0
         *     }
         */
        WatchTargetStatistics: {
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            captureCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            displayTarget: string;
            /**
             * Format: date-time
             * @description ISO8601带时区，服务端UTC、前端Asia/Shanghai显示。
             */
            firstSeenAt?: string;
            /**
             * Format: uuid
             * @description 按有效采集频次、最近出现时刻、siteId稳定裁决；仅疑似场所代理，不是真实住址，未知为null；使用RESIDENCE候选时需>=5个夜晚等规则。
             */
            mostFrequentSiteId?: string | null;
            /** @description 仅授权展示的场所名称；无权/未确认null。 */
            mostFrequentSiteName?: string | null;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            regionCount: number;
            /** @description 可选字段省略表示未提供；仅nullable字段允许null。 */
            siteCount: number;
            target: components["schemas"]["ContextTarget"];
        };
    };
    responses: {
        /** @description VALIDATION_ERROR；完整错误码映射见error-codes.md，客户端按code判断。 */
        Http400: {
            headers: {
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "VALIDATION_ERROR",
                 *       "message": "VALIDATION_ERROR",
                 *       "traceId": "trace-example",
                 *       "details": [],
                 *       "data": null
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description AUTH_REQUIRED；完整错误码映射见error-codes.md，客户端按code判断。 */
        Http401: {
            headers: {
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "AUTH_REQUIRED",
                 *       "message": "AUTH_REQUIRED",
                 *       "traceId": "trace-example",
                 *       "details": [],
                 *       "data": null
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description PERMISSION_DENIED；完整错误码映射见error-codes.md，客户端按code判断。 */
        Http403: {
            headers: {
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "PERMISSION_DENIED",
                 *       "message": "PERMISSION_DENIED",
                 *       "traceId": "trace-example",
                 *       "details": [],
                 *       "data": null
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description DATA_NOT_FOUND；完整错误码映射见error-codes.md，客户端按code判断。 */
        Http404: {
            headers: {
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "DATA_NOT_FOUND",
                 *       "message": "DATA_NOT_FOUND",
                 *       "traceId": "trace-example",
                 *       "details": [],
                 *       "data": null
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description RESOURCE_CONFLICT；完整错误码映射见error-codes.md，客户端按code判断。 */
        Http409: {
            headers: {
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "RESOURCE_CONFLICT",
                 *       "message": "RESOURCE_CONFLICT",
                 *       "traceId": "trace-example",
                 *       "details": [],
                 *       "data": null
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description FILE_EXPIRED；完整错误码映射见error-codes.md，客户端按code判断。 */
        Http410: {
            headers: {
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "FILE_EXPIRED",
                 *       "message": "FILE_EXPIRED",
                 *       "traceId": "trace-example",
                 *       "details": [],
                 *       "data": null
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description 文件大小超限 */
        Http413: {
            headers: {
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "FILE_TOO_LARGE",
                 *       "message": "上传文件不符合要求",
                 *       "data": null,
                 *       "traceId": "mock-trace",
                 *       "details": []
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description 不支持的媒体类型 */
        Http415: {
            headers: {
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "FILE_MEDIA_UNSUPPORTED",
                 *       "message": "上传文件不符合要求",
                 *       "data": null,
                 *       "traceId": "mock-trace",
                 *       "details": []
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description DATA_QUERY_TOO_BROAD；完整错误码映射见error-codes.md，客户端按code判断。 */
        Http422: {
            headers: {
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "DATA_QUERY_TOO_BROAD",
                 *       "message": "DATA_QUERY_TOO_BROAD",
                 *       "traceId": "trace-example",
                 *       "details": [],
                 *       "data": null
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description RATE_LIMITED；完整错误码映射见error-codes.md，客户端按code判断。 */
        Http429: {
            headers: {
                /** @description 再次尝试前等待的秒数；不自动重试非幂等请求。 */
                "Retry-After"?: number;
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "RATE_LIMITED",
                 *       "message": "RATE_LIMITED",
                 *       "traceId": "trace-example",
                 *       "details": [],
                 *       "data": null
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description SYSTEM_INTERNAL；完整错误码映射见error-codes.md，客户端按code判断。 */
        Http500: {
            headers: {
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "SYSTEM_INTERNAL",
                 *       "message": "SYSTEM_INTERNAL",
                 *       "traceId": "trace-example",
                 *       "details": [],
                 *       "data": null
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description SYSTEM_UNAVAILABLE；完整错误码映射见error-codes.md，客户端按code判断。 */
        Http503: {
            headers: {
                /** @description 再次尝试前等待的秒数；不自动重试非幂等请求。 */
                "Retry-After"?: number;
                "X-Trace-Id"?: string;
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "code": "SYSTEM_UNAVAILABLE",
                 *       "message": "SYSTEM_UNAVAILABLE",
                 *       "traceId": "trace-example",
                 *       "details": [],
                 *       "data": null
                 *     }
                 */
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
    };
    parameters: {
        /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
        ApprovalId: string;
        /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
        CsrfToken: string;
        /** @description 不透明游标，不自行解析。 */
        Cursor: string;
        /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
        CursorPageSize: number;
        /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
        IdempotencyKey: string;
        /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
        IfMatch: string;
        /** @description 管理列表页码，从 1 开始。 */
        Page: number;
        /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
        PageSize: number;
        /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
        SortOrder: components["schemas"]["SortOrder"];
    };
    requestBodies: never;
    headers: never;
    pathItems: never;
};
export type $defs = Record<string, never>;
export interface operations {
    listActionApprovals: {
        parameters: {
            query?: {
                cursor?: string;
                pageSize?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 与JSON traceId一致 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ActionApprovalPageResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            410: components["responses"]["Http410"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
            503: components["responses"]["Http503"];
        };
    };
    createActionApproval: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                /**
                 * @example {
                 *       "proposal": {
                 *         "operationId": "readDecryptionResult",
                 *         "pathParameters": {
                 *           "requestId": "00000000-0000-4000-8000-000000000001"
                 *         }
                 *       },
                 *       "reason": "契约合成示例：对已存在申请执行独立复核"
                 *     }
                 */
                "application/json": components["schemas"]["ActionApprovalInput"];
            };
        };
        responses: {
            /** @description 已创建；不代表动作已执行 */
            201: {
                headers: {
                    /** @description 与JSON traceId一致 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ActionApprovalResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            410: components["responses"]["Http410"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
            503: components["responses"]["Http503"];
        };
    };
    getActionApproval: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                approvalId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 与JSON traceId一致 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ActionApprovalResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            410: components["responses"]["Http410"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
            503: components["responses"]["Http503"];
        };
    };
    decideActionApproval: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                approvalId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                /**
                 * @example {
                 *       "decision": "APPROVE",
                 *       "reason": "契约合成示例：已核对用途、目标与当前范围",
                 *       "currentPassword": "Example-password-2026!"
                 *     }
                 */
                "application/json": components["schemas"]["ActionApprovalDecisionInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 与JSON traceId一致 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ActionApprovalResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            410: components["responses"]["Http410"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
            503: components["responses"]["Http503"];
        };
    };
    listAlerts: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                from?: string;
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                ruleId?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                severity?: "LOW" | "MEDIUM" | "HIGH";
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "occurredAt" | "alertId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: "OPEN" | "ACKNOWLEDGED" | "CLOSED";
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListAlertsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    getAlert: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                alertId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetAlertResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    listAnalysisTasks: {
        parameters: {
            query?: {
                /** @description 分析输入区间开始≥此时间，不是创建时间。 */
                analysisFrom?: string;
                /** @description 分析输入区间结束≤此时间。 */
                analysisTo?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                createdFrom?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                createdTo?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                kind?: "COMPANION" | "COLLISION" | "REGION_STATISTICS";
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                name?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "createdAt" | "name" | "status";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["TaskStatus"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListAnalysisTasksResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    createAnalysisTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AnalysisTaskInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateAnalysisTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getAnalysisTask: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                analysisTaskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetAnalysisTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    deleteAnalysisTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                analysisTaskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteAnalysisTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    listAnalysisResults: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                dataType?: components["schemas"]["DataType"];
                /** @description 在对应算法评分标尺签认后过滤，区域统计为coverageRate。 */
                minScore?: number;
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "resultId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                analysisTaskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListAnalysisResultsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getAnalysisResult: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                analysisTaskId: string;
                /** @description 平台内部代理UUID，不等于业务号码。 */
                resultId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetAnalysisResultResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    exportAnalysisResults: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                analysisTaskId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ResultExportInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExportAnalysisResultsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    reviseAnalysisTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                analysisTaskId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AnalysisTaskRevisionInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReviseAnalysisTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    exportAnalysisTasks: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AnalysisTasksExportInput"];
            };
        };
        responses: {
            /** @description 合并导出选定分析任务结果 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExportAnalysisTasksResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getAuditEvent: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                eventId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetAuditEventResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    exportAuditEvents: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AuditExportInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExportAuditEventsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    searchAuditEvents: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AuditSearchRequest"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SearchAuditEventsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getCsrfToken: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description CSRFID=<opaque>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1 */
                    "Set-Cookie"?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetCsrfTokenResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    login: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginRequest"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 设置 HttpOnly; Secure; SameSite=Lax 的 SID；浏览器自动携带。 */
                    "Set-Cookie"?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LoginResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    logout: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LogoutResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    renewSession: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RenewSessionResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    deleteResourcesBatch: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BatchDeletionInput"];
            };
        };
        responses: {
            /** @description 批量软删选定记录 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteResourcesBatchResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    revokeResourcesBatch: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BatchRevocationInput"];
            };
        };
        responses: {
            /** @description 批量软删选定记录 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RevokeResourcesBatchResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getCapture: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                captureId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetCaptureResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    searchCaptures: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CaptureSearchRequest"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SearchCapturesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getCaptureStatistics: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CaptureStatisticsRequest"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetCaptureStatisticsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getClientConfig: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetClientConfigResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    createContext: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AnalysisContextInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateContextResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getContext: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                contextId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetContextResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            /** @description CONTEXT_EXPIRED */
            410: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            500: components["responses"]["Http500"];
        };
    };
    getDashboardSummary: {
        parameters: {
            query?: {
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                dataType?: components["schemas"]["DataType"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                siteId?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDashboardSummaryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listDatasets: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                datasetKind?: components["schemas"]["DatasetKind"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                name?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "createdAt" | "name" | "version";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListDatasetsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    createDataset: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DatasetInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateDatasetResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getDataset: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                datasetId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDatasetResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    deleteDataset: {
        parameters: {
            query?: never;
            header: {
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                datasetId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteDatasetResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    updateDataset: {
        parameters: {
            query?: never;
            header: {
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                datasetId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DatasetUpdate"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdateDatasetResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    exportDataset: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                datasetId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ResultExportInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExportDatasetResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    importDatasetItems: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                datasetId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DatasetImportInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ImportDatasetItemsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listDatasetItems: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "addedAt" | "itemId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                datasetId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListDatasetItemsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    addDatasetItems: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                datasetId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DatasetItemsInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AddDatasetItemsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    removeDatasetItem: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                datasetId: string;
                /** @description 平台内部代理UUID，不等于业务号码。 */
                itemId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RemoveDatasetItemResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    listDatasetVersions: {
        parameters: {
            query?: {
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "version" | "createdAt";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                datasetId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListDatasetVersionsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    requestDecryption: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DecryptionInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RequestDecryptionResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getDecryptionRequest: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                requestId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDecryptionRequestResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    readDecryptionResult: {
        parameters: {
            query?: never;
            header: {
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                requestId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 禁止缓存敏感结果 */
                    "Cache-Control"?: "no-store";
                    /** @description 与JSON traceId一致 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DecryptionResultResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            410: components["responses"]["Http410"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
            503: components["responses"]["Http503"];
        };
    };
    listDeliveries: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                alertId?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                channel?: components["schemas"]["NotificationChannel"];
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                from?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                notificationTaskId?: string;
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "createdAt" | "deliveryId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["DeliveryStatus"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListDeliveriesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    getDelivery: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                deliveryId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDeliveryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    exportSmsLogs: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeliveryExportInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExportSmsLogsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    searchSmsLogs: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SmsLogSearchRequest"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SearchSmsLogsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listDeviceCommands: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                commandType?: "RESET" | "APPLY_PARAMETERS";
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                createdFrom?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                createdTo?: string;
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                deviceName?: string;
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                groupName?: string;
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                name?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                regionCode?: string;
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "createdAt" | "taskId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["TaskStatus"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListDeviceCommandsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    createDeviceCommand: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceCommandInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateDeviceCommandResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getDeviceCommand: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDeviceCommandResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    deleteDeviceCommand: {
        parameters: {
            query?: never;
            header: {
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 软删终态配置任务 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteDeviceCommandResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listDeviceCommandResults: {
        parameters: {
            query?: {
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "status" | "deviceId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListDeviceCommandResultsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    retryDeviceCommand: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceCommandRetryInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RetryDeviceCommandResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    reviseDeviceCommand: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceCommandInput"];
            };
        };
        responses: {
            /** @description 重建参数配置任务 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReviseDeviceCommandResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listDeviceGroups: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                name?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                regionCode?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                siteId?: string;
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "name" | "updatedAt" | "groupId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListDeviceGroupsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    createDeviceGroup: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceGroupInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateDeviceGroupResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getDeviceGroup: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                groupId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDeviceGroupResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    updateDeviceGroup: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                groupId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceGroupInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdateDeviceGroupResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    deleteDeviceGroup: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                groupId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteDeviceGroupResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listDeviceGroupMembers: {
        parameters: {
            query?: {
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "name" | "serialNumber" | "deviceId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                groupId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListDeviceGroupMembersResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    addDeviceGroupMembers: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                groupId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceGroupMembersInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AddDeviceGroupMembersResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    removeDeviceGroupMember: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                deviceId: string;
                /** @description 平台内部代理UUID，不等于业务号码。 */
                groupId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RemoveDeviceGroupMemberResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listDevices: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                category?: string;
                /** @description 地图选中对象ID集合，同组OR；与其他查询条件AND。 */
                ids?: string[];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                nameOrSerial?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                siteId?: string;
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "name" | "serialNumber" | "updatedAt" | "deviceId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["DeviceStatus"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListDevicesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    createDevice: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateDeviceResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getDevice: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                deviceId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDeviceResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    updateDevice: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                deviceId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdateDeviceResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    deleteDevice: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                deviceId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteDeviceResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getDeviceCaptureStatistics: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                from?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                granularity?: "HOUR" | "DAY" | "MONTH";
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                to?: string;
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                deviceId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDeviceCaptureStatisticsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    updateDeviceParameters: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                deviceId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceParameterUpdateInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdateDeviceParametersResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getDeviceParameters: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                deviceId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDeviceParametersResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getDeviceStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                deviceId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDeviceStatusResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    listDeviceStatusHistory: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                from?: string;
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "occurredAt" | "eventId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                to?: string;
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                deviceId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListDeviceStatusHistoryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    exportDevices: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceExportInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExportDevicesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    importDevices: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DeviceImportInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ImportDevicesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getDictionary: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                dictionaryCode: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDictionaryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    createExport: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ExportRequest"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateExportResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    createFaceSearchTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["FaceSearchInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateFaceSearchTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listFaceSearchResults: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "similarity" | "captureTime";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListFaceSearchResultsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            410: components["responses"]["Http410"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getFileAsset: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                fileAssetId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetFileAssetResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            410: components["responses"]["Http410"];
            500: components["responses"]["Http500"];
        };
    };
    downloadFile: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                fileAssetId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 受控文件流；服务端在下载时重校当前权限。 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/octet-stream": string;
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            410: components["responses"]["Http410"];
            500: components["responses"]["Http500"];
        };
    };
    uploadFile: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "multipart/form-data": components["schemas"]["UploadFileRequest"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UploadFileResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            413: components["responses"]["Http413"];
            415: components["responses"]["Http415"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getHealth: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetHealthResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
            503: components["responses"]["Http503"];
        };
    };
    getImportTemplate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                templateCode: "DATASET_ITEMS_V1" | "DEVICES_V1" | "PERSON_LIBRARIES_V1";
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 读取导入模板列与文件约定 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetImportTemplateResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listIngestionBatches: {
        parameters: {
            query?: {
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "createdAt" | "status";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                source?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["IngestionStatus"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListIngestionBatchesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    createIngestionBatch: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["IngestionBatchInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateIngestionBatchResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getIngestionBatch: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                batchId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetIngestionBatchResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    listIngestionErrors: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                batchId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListIngestionErrorsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    replayIngestionBatch: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                batchId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReplayInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReplayIngestionBatchResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    createIngestionBackfill: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BackfillInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateIngestionBackfillResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getIngestionQualitySummary: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                from?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                source?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetIngestionQualitySummaryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    getMapAreaSummary: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AreaSummaryRequest"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetMapAreaSummaryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getDeviceMapFeatures: {
        parameters: {
            query: {
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                aggregation?: "AUTO" | "NONE" | "CLUSTER";
                /** @description minLon,minLat,maxLon,maxLat；WGS84；经纬度范围与顺序由服务端校验。 */
                bbox: string;
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                deviceIds?: string[];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                keyword?: string;
                /** @description PD-2026-10-19.v1暂定最大2000个点/聚合要素，超量聚合或瓦片；超上限不静默截断。 */
                limit?: number;
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                regionCode?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                siteId?: string;
                /** @description 重复query键siteIds=...&siteIds=...；同组OR，与siteId同时给出取交集。 */
                siteIds?: string[];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["DeviceStatus"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                zoom: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetDeviceMapFeaturesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getMapFeatures: {
        parameters: {
            query: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                aggregation?: "AUTO" | "POINT" | "CLUSTER";
                /** @description minLon,minLat,maxLon,maxLat；WGS84；经纬度范围与顺序由服务端校验。 */
                bbox: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                contextId?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                dataType?: components["schemas"]["DataType"];
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                deviceIds?: string[];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                from?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                layer?: "CAPTURE" | "SITE";
                /** @description PD-2026-10-19.v1暂定最大2000个点/聚合要素，超量聚合或瓦片；超上限不静默截断。 */
                limit?: number;
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                regionCode?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                siteId?: string;
                /** @description 重复query键siteIds=...&siteIds=...；同组OR，与siteId同时给出取交集。 */
                siteIds?: string[];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                to?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                zoom: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetMapFeaturesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getMapHeatmap: {
        parameters: {
            query: {
                /** @description minLon,minLat,maxLon,maxLat；WGS84；经纬度范围与顺序由服务端校验。 */
                bbox: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                dataType?: components["schemas"]["DataType"];
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                deviceIds?: string[];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                from: string;
                /** @description PD-2026-10-19.v1暂定最大2000个点/聚合要素，超量聚合或瓦片；超上限不静默截断。 */
                limit?: number;
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                regionCode?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                siteId?: string;
                /** @description 重复query键siteIds=...&siteIds=...；同组OR，与siteId同时给出取交集。 */
                siteIds?: string[];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                to: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                zoom: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetMapHeatmapResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getMe: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetMeResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    changeMyPassword: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PasswordChangeInput"];
            };
        };
        responses: {
            /** @description 修改本人密码 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ChangeMyPasswordResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listMenuCatalog: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 读取可分配菜单目录 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListMenuCatalogResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listPlatformMessages: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 查询本人平台消息 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListPlatformMessagesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    exportMetrics: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MetricExportInput"];
            };
        };
        responses: {
            /** @description 导出首页或运维指标图表 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExportMetricsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listModelTags: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListModelTagsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listNotificationTasks: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                name?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "createdAt" | "nextRunAt" | "status";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["NotificationPlanStatus"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListNotificationTasksResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    createNotificationTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["NotificationTaskInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateNotificationTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getNotificationTask: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                notificationTaskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetNotificationTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    updateNotificationTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                notificationTaskId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["NotificationTaskInput"];
            };
        };
        responses: {
            /** @description 更新未终结通知计划 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdateNotificationTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    deleteNotificationTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                notificationTaskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteNotificationTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    cancelNotificationTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                notificationTaskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CancelNotificationTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    retryNotificationTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                notificationTaskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RetryNotificationTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listNotificationTemplates: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 查询已批准通知模板 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListNotificationTemplatesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getOperationSummary: {
        parameters: {
            query?: {
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                dataType?: components["schemas"]["DataType"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                granularity?: "WEEK" | "MONTH" | "QUARTER" | "YEAR";
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                regionCode?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                siteId?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetOperationSummaryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listPermissionCatalog: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 读取可分配权限目录 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListPermissionCatalogResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listPersonLibraries: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                category?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                name?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "name" | "createdAt" | "libraryId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListPersonLibrariesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    createPersonLibrary: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PersonLibraryInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreatePersonLibraryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getPersonLibrary: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                libraryId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetPersonLibraryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    updatePersonLibrary: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                libraryId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PersonLibraryInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdatePersonLibraryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    deletePersonLibrary: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                libraryId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeletePersonLibraryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    exportPersonLibrary: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                libraryId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PersonLibraryExportInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExportPersonLibraryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    importPersonLibraryMembers: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                libraryId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PersonLibraryImportInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ImportPersonLibraryMembersResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listPersonLibraryMembers: {
        parameters: {
            query?: {
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                name?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "addedAt" | "memberId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                libraryId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListPersonLibraryMembersResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    addPersonLibraryMembers: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                libraryId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PersonLibraryMembersInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AddPersonLibraryMembersResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    removePersonLibraryMember: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                libraryId: string;
                /** @description 平台内部代理UUID，不等于业务号码。 */
                memberId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RemovePersonLibraryMemberResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    createLibraryPerson: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                libraryId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PersonLibraryPersonInput"];
            };
        };
        responses: {
            /** @description 新增库内人员及手工档案 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateLibraryPersonResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    exportPersonLibraries: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PersonLibrariesExportInput"];
            };
        };
        responses: {
            /** @description 导出选定多个人员库 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExportPersonLibrariesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    importPersonLibraries: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PersonLibrariesImportInput"];
            };
        };
        responses: {
            /** @description 批量导入库列表和库内人员 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ImportPersonLibrariesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getProfile: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                profileId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetProfileResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listProfileConflicts: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                profileId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 读取可复核档案冲突 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListProfileConflictsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    resolveProfileConflict: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                conflictId: string;
                /** @description 平台内部代理UUID，不等于业务号码。 */
                profileId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProfileConflictResolutionInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ResolveProfileConflictResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    createProfileCorrection: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                profileId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProfileCorrectionInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateProfileCorrectionResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listProfileEvidence: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "collectedAt" | "evidenceId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                profileId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListProfileEvidenceResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    exportProfile: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                profileId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProfileExportInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExportProfileResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listProfileHistory: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "occurredAt" | "revisionId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                profileId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListProfileHistoryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    splitProfile: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                profileId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProfileSplitInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SplitProfileResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listProfileTargets: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                dataType?: components["schemas"]["DataType"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "firstSeenAt" | "linkId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                profileId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListProfileTargetsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listProfileTrajectories: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 筛选四类采集轨迹。 */
                dataType?: components["schemas"]["DataType"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                from?: string;
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "captureTime" | "captureId";
                /** @description 轨迹固定升序；同captureTime按captureId稳定排序。 */
                sortOrder?: "ASC";
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                to?: string;
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                profileId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListProfileTrajectoriesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    mergeProfiles: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProfileMergeInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MergeProfilesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    searchProfiles: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProfileSearchRequest"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SearchProfilesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getReadiness: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetReadinessResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
            503: components["responses"]["Http503"];
        };
    };
    listRegions: {
        parameters: {
            query?: {
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                parentRegionCode?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 读取授权区域目录 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListRegionsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listReports: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                createdFrom?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                createdTo?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                name?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "createdAt" | "name" | "reportId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["TaskStatus"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListReportsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    createReport: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReportCreateInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateReportResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getReport: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                reportId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetReportResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    deleteReport: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                reportId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteReportResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    downloadReport: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                reportId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 受控文件流；服务端在下载时重校当前权限。 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/octet-stream": string;
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            410: components["responses"]["Http410"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    getReportPreview: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                reportId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetReportPreviewResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    retryReport: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                reportId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RetryReportResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listRoles: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                name?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                roleCode?: string;
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "roleCode" | "name" | "updatedAt" | "roleId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListRolesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    createRole: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RoleInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateRoleResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getRole: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                roleId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetRoleResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    updateRole: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                roleId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RoleInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdateRoleResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    deleteRole: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                roleId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteRoleResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    setRolePermissions: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                roleId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RolePermissionsInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SetRolePermissionsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    setRoleScopes: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                roleId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RoleScopesInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SetRoleScopesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listSetTasks: {
        parameters: {
            query?: {
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "createdAt" | "status";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["TaskStatus"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListSetTasksResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    createSetTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SetTaskInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateSetTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getSetTask: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                setTaskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetSetTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    listSetTaskResults: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                setTaskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListSetTaskResultsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    previewSetTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SetTaskInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PreviewSetTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listSites: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                enabled?: boolean;
                /** @description 地图选中对象ID集合，同组OR；与其他查询条件AND。 */
                ids?: string[];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                name?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                regionCode?: string;
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "name" | "updatedAt" | "siteId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListSitesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    createSite: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SiteInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateSiteResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getSite: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                siteId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetSiteResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    updateSite: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                siteId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SiteInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdateSiteResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    deleteSite: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                siteId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteSiteResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    resolveTargets: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TargetResolutionInput"];
            };
        };
        responses: {
            /** @description 批量解析或登记目标代理ID */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ResolveTargetsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            429: components["responses"]["Http429"];
            500: components["responses"]["Http500"];
        };
    };
    listTasks: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                createdFrom?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                createdTo?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "createdAt" | "status";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["TaskStatus"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                taskType?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListTasksResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getTask: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    listTaskAttempts: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListTaskAttemptsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    cancelTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CancelTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    listTaskRowErrors: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListTaskRowErrorsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    getImportTaskResult: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetImportTaskResultResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            410: components["responses"]["Http410"];
            500: components["responses"]["Http500"];
        };
    };
    retryTask: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RetryTaskResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    queryTrajectory: {
        parameters: {
            query?: never;
            header: {
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TrajectoryRequest"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["QueryTrajectoryResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listTrendModels: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                enabled?: boolean;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                modelType?: "RESIDENCE" | "TRAJECTORY" | "FREQUENCY";
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "name" | "updatedAt" | "modelId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListTrendModelsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    createTrendModel: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TrendModelInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateTrendModelResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getTrendModel: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                modelId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetTrendModelResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    updateTrendModel: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                modelId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TrendModelInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdateTrendModelResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listModelParameterVersions: {
        parameters: {
            query?: {
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "version" | "createdAt";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                modelId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListModelParameterVersionsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    createModelParameterVersion: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                modelId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ModelParameterVersionInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateModelParameterVersionResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getModelParameterVersion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                modelId: string;
                /** @description 平台内部代理UUID，不等于业务号码。 */
                parameterVersionId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetModelParameterVersionResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    listTrendModelResults: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                dataType?: components["schemas"]["DataType"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "generatedAt" | "resultId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                tagCode?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                taskId?: string;
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                modelId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListTrendModelResultsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getTrendModelResult: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                modelId: string;
                /** @description 平台内部代理UUID，不等于业务号码。 */
                resultId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetTrendModelResultResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    runTrendModel: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                modelId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ModelRunInput"];
            };
        };
        responses: {
            /** @description 成功 */
            202: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RunTrendModelResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listUsers: {
        parameters: {
            query?: {
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                displayName?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                loginName?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "userCode" | "loginName" | "createdAt" | "userId";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["UserStatus"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                userCode?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListUsersResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    createUser: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserCreateInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateUserResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getUser: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                userId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetUserResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    updateUser: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserUpdateInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdateUserResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    deleteUser: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                userId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteUserResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    disableUser: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserStateInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DisableUserResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    enableUser: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UserStateInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EnableUserResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    changeUserPassword: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 命中PD-2026-10-19.v1高风险规则时必填。服务端绑定operationId、路径资源、版本及typed请求体摘要；只可消费一次，不替代动作权限/当前范围校验。 */
                "X-Approval-Id"?: components["parameters"]["ApprovalId"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PasswordChangeInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ChangeUserPasswordResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    listWatchRules: {
        parameters: {
            query?: {
                /** @description 规则开始时间下界 */
                effectiveFrom?: string;
                /** @description 规则结束时间上界 */
                effectiveUntil?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                name?: string;
                /** @description 授权范围内筛选，取值和界限见schema；改变条件需重置cursor。 */
                ownerLoginName?: string;
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                ownerUserId?: string;
                /** @description 管理列表页码，从 1 开始。 */
                page?: components["parameters"]["Page"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["PageSize"];
                /** @description 按英文稳定字段名排序；同值追加资源ID为次键。 */
                sortBy?: "createdAt" | "name" | "status";
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                sortOrder?: components["parameters"]["SortOrder"];
                /** @description 同名多值OR，不同条件AND；仅当前授权范围。 */
                status?: components["schemas"]["WatchStatus"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ListWatchRulesResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            500: components["responses"]["Http500"];
        };
    };
    createWatchRule: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WatchRuleInput"];
            };
        };
        responses: {
            /** @description 成功 */
            201: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateWatchRuleResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    getWatchRule: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                ruleId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetWatchRuleResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            500: components["responses"]["Http500"];
        };
    };
    deleteWatchRule: {
        parameters: {
            query?: never;
            header: {
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                ruleId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DeleteWatchRuleResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    updateWatchRule: {
        parameters: {
            query?: never;
            header: {
                /** @description 强ETag，格式为双引号包裹的资源version（如 "1"）；详情成功响应的ETag可直接回传。不匹配返回409 RESOURCE_VERSION_CONFLICT，缺失/格式错误返回400 VALIDATION_ERROR。 */
                "If-Match": components["parameters"]["IfMatch"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                ruleId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WatchRuleUpdate"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description 双引号包裹的资源version，直接用于If-Match。 */
                    ETag?: string;
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UpdateWatchRuleResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    activateWatchRule: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                ruleId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WatchActivateInput"];
            };
        };
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ActivateWatchRuleResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            422: components["responses"]["Http422"];
            500: components["responses"]["Http500"];
        };
    };
    revokeWatchRule: {
        parameters: {
            query?: never;
            header: {
                /** @description 同用户+资源类型+键防重；同键不同参数返回 409 IDEMPOTENCY_CONFLICT。；键至少在任务/资源存活期间保留，已清理返回409 RESOURCE_CONFLICT，不把旧键当新提交；失败重试使用任务重试入口。 */
                "Idempotency-Key": components["parameters"]["IdempotencyKey"];
                /** @description 状态变更请求必填；Origin 同时由服务端校验。 */
                "X-CSRF-Token": components["parameters"]["CsrfToken"];
            };
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                ruleId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RevokeWatchRuleResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
    getWatchStatistics: {
        parameters: {
            query?: {
                /** @description 不透明游标，不自行解析。 */
                cursor?: components["parameters"]["Cursor"];
                /** @description 统一参数，管理page或事件cursor分页；默认值见schema。 */
                pageSize?: components["parameters"]["CursorPageSize"];
            };
            header?: never;
            path: {
                /** @description 平台内部代理UUID，不等于业务号码。 */
                ruleId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description 成功 */
            200: {
                headers: {
                    /** @description JSON响应体traceId相同；文件流通过此Header追踪。 */
                    "X-Trace-Id"?: string;
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GetWatchStatisticsResponse"];
                };
            };
            400: components["responses"]["Http400"];
            401: components["responses"]["Http401"];
            403: components["responses"]["Http403"];
            404: components["responses"]["Http404"];
            409: components["responses"]["Http409"];
            500: components["responses"]["Http500"];
        };
    };
}
