package com.lumen.common.security.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String userName;
    private String nickName;
    private Long tenantId;
    private String tenantCode;
    private Long deptId;
    private Set<String> roles;
    private Set<String> permissions;
    private Integer dataScope;
    private String tokenId;
}