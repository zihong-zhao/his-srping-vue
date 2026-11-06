package com.his.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String passwordHash;

    private String displayName;

    private Long deptId;

    private String role;

    private Boolean enabled;

    private LocalDateTime createdAt;
}
