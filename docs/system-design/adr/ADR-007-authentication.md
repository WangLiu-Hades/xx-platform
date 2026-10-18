> 2026-10-18当前开发基线：[PD-2026-10-18.v1](../../project-governance/暂定方案与修改记录.md)。本文件原有未决值、四人编号及“未签认统一422/禁用”说明由本版暂定规则覆盖；甲方仍待确认，真实资源/实现就绪仍独立校验。原文保留作追踪。

# ADR 007 身份认证

**状态：拟议。** Context：用户禁用和权限范围变更须及时生效，首版一个 Web/API 边界。Decision：服务端会话、随机 SID 的哈希存库、同源 HttpOnly Secure Cookie，CSRF/Origin 校验。Alternatives：自包含 JWT、Access+Refresh Token。Consequences：撤销明确、敏感凭据不落 JS 存储；多 API 实例需共享会话库，超时/多端策略与网络拓扑待 Q-002/Q-006。

## DB-DESIGN-2字段同步

当前契约仍采用单一SID Cookie Session边界；P-001最终生命周期待签认，数据库结构采用不等于认证业务决策已签认。user_session使用sid_hash、idle_expires_at、absolute_expires_at、revoked_at；csrf_session单独绑定。user_scope与role范围分别保留输入，P-002未确认前不合并扩大权限；禁止新增相矛盾JWT表/接口。
