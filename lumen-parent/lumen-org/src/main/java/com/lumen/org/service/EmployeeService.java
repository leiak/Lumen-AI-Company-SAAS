package com.lumen.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.org.entity.SysEmployee;
import com.lumen.org.mapper.SysEmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final SysEmployeeMapper employeeMapper;

    public IPage<SysEmployee> list(int pageNum, int pageSize,
                                   String keyword, Long deptId, String status) {
        var w = new LambdaQueryWrapper<SysEmployee>().orderByDesc(SysEmployee::getEmployeeId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SysEmployee::getName, keyword)
                .or().like(SysEmployee::getEmployeeNo, keyword));
        }
        if (deptId != null) w.eq(SysEmployee::getDeptId, deptId);
        if (status != null && !status.isBlank()) w.eq(SysEmployee::getEmploymentStatus, status);
        return employeeMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public SysEmployee getById(Long id) {
        SysEmployee e = employeeMapper.selectById(id);
        if (e == null) throw new ServiceException(404, "Employee not found: " + id);
        return e;
    }

    public SysEmployee getByUserId(Long userId) {
        SysEmployee e = employeeMapper.findByUserId(userId);
        if (e == null) throw new ServiceException(404, "Employee not found for user: " + userId);
        return e;
    }

    @Transactional
    public SysEmployee create(SysEmployee employee) {
        if (employee.getEmployeeNo() == null || employee.getEmployeeNo().isBlank()) {
            throw new ServiceException(400, "employeeNo is required");
        }
        if (employee.getName() == null || employee.getName().isBlank()) {
            throw new ServiceException(400, "name is required");
        }
        if (employee.getEmployeeType() == null) employee.setEmployeeType("REGULAR");
        if (employee.getEmploymentStatus() == null) employee.setEmploymentStatus("ACTIVE");
        if (employee.getHireDate() == null) employee.setHireDate(LocalDate.now());
        // Strip client-controlled fields (id, createTime, etc.) — let BaseEntity handle them
        SysEmployee toCreate = new SysEmployee();
        toCreate.setUserId(employee.getUserId());
        toCreate.setEmployeeNo(employee.getEmployeeNo());
        toCreate.setName(employee.getName());
        toCreate.setNamePinyin(employee.getNamePinyin());
        toCreate.setGender(employee.getGender());
        toCreate.setMobileEnc(employee.getMobileEnc());
        toCreate.setEmailEnc(employee.getEmailEnc());
        toCreate.setIdCardEnc(employee.getIdCardEnc());
        toCreate.setBirthDate(employee.getBirthDate());
        toCreate.setHireDate(employee.getHireDate());
        toCreate.setLeaveDate(employee.getLeaveDate());
        toCreate.setDeptId(employee.getDeptId());
        toCreate.setPostId(employee.getPostId());
        toCreate.setDirectLeaderId(employee.getDirectLeaderId());
        toCreate.setEmployeeType(employee.getEmployeeType());
        toCreate.setEmploymentStatus(employee.getEmploymentStatus());
        toCreate.setRemark(employee.getRemark());
        employeeMapper.insert(toCreate);
        return toCreate;
    }

    @Transactional
    public SysEmployee update(SysEmployee employee) {
        SysEmployee existing = getById(employee.getEmployeeId());
        if (existing.getIsBuiltin() != null && existing.getIsBuiltin() == 1) {
            throw new ServiceException(403, "Cannot modify builtin employee");
        }
        if (employee.getName() != null) existing.setName(employee.getName());
        if (employee.getNamePinyin() != null) existing.setNamePinyin(employee.getNamePinyin());
        if (employee.getGender() != null) existing.setGender(employee.getGender());
        if (employee.getMobileEnc() != null) existing.setMobileEnc(employee.getMobileEnc());
        if (employee.getEmailEnc() != null) existing.setEmailEnc(employee.getEmailEnc());
        if (employee.getIdCardEnc() != null) existing.setIdCardEnc(employee.getIdCardEnc());
        if (employee.getDeptId() != null) existing.setDeptId(employee.getDeptId());
        if (employee.getPostId() != null) existing.setPostId(employee.getPostId());
        if (employee.getDirectLeaderId() != null) existing.setDirectLeaderId(employee.getDirectLeaderId());
        if (employee.getEmployeeType() != null) existing.setEmployeeType(employee.getEmployeeType());
        if (employee.getEmploymentStatus() != null) existing.setEmploymentStatus(employee.getEmploymentStatus());
        if (employee.getBirthDate() != null) existing.setBirthDate(employee.getBirthDate());
        if (employee.getHireDate() != null) existing.setHireDate(employee.getHireDate());
        if (employee.getLeaveDate() != null) existing.setLeaveDate(employee.getLeaveDate());
        if (employee.getEmployeeNo() != null) existing.setEmployeeNo(employee.getEmployeeNo());
        if (employee.getRemark() != null) existing.setRemark(employee.getRemark());
        employeeMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        SysEmployee existing = getById(id);
        if (existing.getIsBuiltin() != null && existing.getIsBuiltin() == 1) {
            throw new ServiceException(403, "Cannot delete builtin employee");
        }
        employeeMapper.deleteById(id);
    }
}