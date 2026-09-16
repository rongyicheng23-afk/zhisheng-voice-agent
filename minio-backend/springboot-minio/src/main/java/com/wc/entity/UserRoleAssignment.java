package com.wc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

@TableName("t_user_role_assignment")
public class UserRoleAssignment {
    @TableId(value = "id", type = IdType.AUTO) private Long id;
    @TableField("user_id") private Integer userId;
    @TableField("role_code") private String roleCode;
    @TableField("create_time") private Date createTime;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer value) { userId = value; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String value) { roleCode = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
}
