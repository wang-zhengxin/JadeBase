# 用户管理

JadeBase 的基础工作区用户管理面向单工作区部署，支持所有者与成员两种账号类型。

## 功能

- 用户列表、邮箱与显示名称搜索、账号类型和状态筛选
- 邀请链接创建与撤销，邀请默认在七天后过期
- 所有者与成员角色调整
- 用户停用与恢复，停用时立即撤销该用户的全部登录会话
- 限制开放注册；开启后只有持有效邀请令牌的邮箱可以注册
- 当前所有者不能停用或降级自己，工作区始终保留至少一个有效所有者

邀请令牌只会在创建响应中返回一次。数据库仅保存 SHA-256 哈希，管理员应通过页面复制并安全发送邀请链接。

## API

所有管理接口均要求当前用户为 `OWNER`：

- `GET /api/v1/admin/users`：分页查询用户、待接受邀请和汇总数据
- `POST /api/v1/admin/users/invitations`：创建或刷新指定邮箱的邀请
- `DELETE /api/v1/admin/users/invitations/{id}`：撤销邀请
- `PATCH /api/v1/admin/users/{id}`：调整账号类型或状态
- `PUT /api/v1/admin/users/registration-policy`：更新开放注册策略

公开的 `GET /api/v1/auth/registration-policy` 用于登录页判断注册策略和邀请有效性。注册时可在 `POST /api/v1/auth/register` 的请求体中携带 `inviteToken`。

## 企业能力边界

当前版本不包含用户组、细粒度资源授权、SSO、SCIM 或多租户隔离。这些能力仍属于企业就绪阶段，基础用户管理不会替代完整 RBAC。
