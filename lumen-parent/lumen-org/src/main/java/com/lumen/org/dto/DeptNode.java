package com.lumen.org.dto;

import com.lumen.org.entity.SysDept;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DeptNode {
    private Long deptId;
    private Long parentId;
    private String ancestors;
    private String deptName;
    private Integer orderNum;
    private String leaderName;
    private String phone;
    private String email;
    private String status;
    private List<DeptNode> children = new ArrayList<>();

    public static DeptNode from(SysDept d) {
        DeptNode n = new DeptNode();
        n.deptId = d.getDeptId();
        n.parentId = d.getParentId();
        n.ancestors = d.getAncestors();
        n.deptName = d.getDeptName();
        n.orderNum = d.getOrderNum();
        n.leaderName = d.getLeaderName();
        n.phone = d.getPhone();
        n.email = d.getEmail();
        n.status = d.getStatus();
        return n;
    }
}