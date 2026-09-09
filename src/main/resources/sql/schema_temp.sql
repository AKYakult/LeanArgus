-- 用户表
CREATE TABLE IF NOT EXISTS users
(
    id                   BIGSERIAL PRIMARY KEY,
    user_code            VARCHAR(64)  NOT NULL,
    username             VARCHAR(64)  NOT NULL,
    email                VARCHAR(128) NOT NULL,
    display_name         VARCHAR(128) NOT NULL,
    password_hash        VARCHAR(256) NOT NULL,
    system_role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at        TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- Refresh Token 表
CREATE TABLE IF NOT EXISTS user_refresh_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_id   VARCHAR(64)  NOT NULL,
    token_hash VARCHAR(256) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_token_id
    ON user_refresh_tokens (token_id);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user_active
    ON user_refresh_tokens (user_id, revoked_at, expires_at);



-- ===================================================
-- 群组与成员管理
-- ===================================================

-- 群组表
CREATE TABLE IF NOT EXISTS groups
(
    id            BIGSERIAL PRIMARY KEY,
    group_code    VARCHAR(64)  NOT NULL,
    group_name    VARCHAR(128) NOT NULL,
    description   TEXT         NOT NULL DEFAULT '',
    owner_user_id BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uq_groups_group_code UNIQUE (group_code),
    CONSTRAINT fk_groups_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
);

COMMENT ON TABLE groups IS '群组表（知识库）';
COMMENT ON COLUMN groups.id IS '主键';
COMMENT ON COLUMN groups.group_code IS '群组编码，唯一，前端展示用';
COMMENT ON COLUMN groups.group_name IS '群组名称';
COMMENT ON COLUMN groups.description IS '群组描述';
COMMENT ON COLUMN groups.owner_user_id IS '群组创建者（所有者）用户 ID';
COMMENT ON COLUMN groups.status IS '群组状态：ACTIVE | ARCHIVED';
COMMENT ON COLUMN groups.created_at IS '创建时间';
COMMENT ON COLUMN groups.updated_at IS '更新时间';

-- 索引：按所有者查询
CREATE INDEX IF NOT EXISTS idx_groups_owner ON groups (owner_user_id);


-- 群组成员表
CREATE TABLE IF NOT EXISTS group_memberships
(
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT fk_membership_group FOREIGN KEY (group_id) REFERENCES groups (id),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_membership_group_user UNIQUE (group_id, user_id)
);

COMMENT ON TABLE group_memberships IS '群组成员关系表';
COMMENT ON COLUMN group_memberships.id IS '主键';
COMMENT ON COLUMN group_memberships.group_id IS '群组 ID';
COMMENT ON COLUMN group_memberships.user_id IS '用户 ID';
COMMENT ON COLUMN group_memberships.role IS '群组内角色：OWNER | MEMBER';
COMMENT ON COLUMN group_memberships.created_at IS '加入时间';
COMMENT ON COLUMN group_memberships.updated_at IS '更新时间';

-- 索引：按用户查询所属群组
CREATE INDEX IF NOT EXISTS idx_membership_user ON group_memberships (user_id);
-- 索引：按群组+角色查询（文档可读性判断用到）
CREATE INDEX IF NOT EXISTS idx_membership_group_role ON group_memberships (group_id, role);

-- 群组邀请表
CREATE TABLE IF NOT EXISTS group_invitations
(
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT      NOT NULL,
    inviter_user_id BIGINT      NOT NULL,
    invitee_user_id BIGINT      NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decided_at      TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT fk_invitation_group FOREIGN KEY (group_id) REFERENCES groups (id),
    CONSTRAINT fk_invitation_inviter FOREIGN KEY (inviter_user_id) REFERENCES users (id),
    CONSTRAINT fk_invitation_invitee FOREIGN KEY (invitee_user_id) REFERENCES users (id)
);

COMMENT ON TABLE group_invitations IS '群组邀请表';
COMMENT ON COLUMN group_invitations.id IS '主键';
COMMENT ON COLUMN group_invitations.group_id IS '群组 ID';
COMMENT ON COLUMN group_invitations.inviter_user_id IS '邀请人用户 ID';
COMMENT ON COLUMN group_invitations.invitee_user_id IS '被邀请人用户 ID';
COMMENT ON COLUMN group_invitations.status IS '邀请状态：PENDING | ACCEPTED | REFUSED | CANCELLED';
COMMENT ON COLUMN group_invitations.decided_at IS '决定时间（接受/拒绝时设置）';
COMMENT ON COLUMN group_invitations.created_at IS '创建时间';
COMMENT ON COLUMN group_invitations.updated_at IS '更新时间';

-- 索引：查询用户收到的待处理邀请
CREATE INDEX IF NOT EXISTS idx_invitation_invitee_status
    ON group_invitations (invitee_user_id, status);

-- 群组加入申请表
CREATE TABLE IF NOT EXISTS group_join_requests
(
    id                 BIGSERIAL PRIMARY KEY,
    group_id           BIGINT      NOT NULL,
    applicant_user_id  BIGINT      NOT NULL,
    status             VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decided_by_user_id BIGINT,
    decided_at         TIMESTAMP,
    created_at         TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT fk_join_request_group FOREIGN KEY (group_id) REFERENCES groups (id),
    CONSTRAINT fk_join_request_applicant FOREIGN KEY (applicant_user_id) REFERENCES users (id),
    CONSTRAINT fk_join_request_decider FOREIGN KEY (decided_by_user_id) REFERENCES users (id)
);

COMMENT ON TABLE group_join_requests IS '群组加入申请表';
COMMENT ON COLUMN group_join_requests.id IS '主键';
COMMENT ON COLUMN group_join_requests.group_id IS '群组 ID';
COMMENT ON COLUMN group_join_requests.applicant_user_id IS '申请人用户 ID';
COMMENT ON COLUMN group_join_requests.status IS '申请状态：PENDING | APPROVED | REJECTED | CANCELLED';
COMMENT ON COLUMN group_join_requests.decided_by_user_id IS '审批人用户 ID';
COMMENT ON COLUMN group_join_requests.decided_at IS '审批时间';
COMMENT ON COLUMN group_join_requests.created_at IS '创建时间';
COMMENT ON COLUMN group_join_requests.updated_at IS '更新时间';

-- 索引：查询某群组的待处理申请
CREATE INDEX IF NOT EXISTS idx_join_request_group_status
    ON group_join_requests (group_id, status);
-- 索引：查询某用户发起的申请
CREATE INDEX IF NOT EXISTS idx_join_request_applicant
    ON group_join_requests (applicant_user_id);