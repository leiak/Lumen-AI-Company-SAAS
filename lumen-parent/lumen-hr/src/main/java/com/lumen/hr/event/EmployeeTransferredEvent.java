package com.lumen.hr.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Domain event raised after an employee transfer has taken effect.
 *
 * <p>TODO: publish via the platform application event bus once the cross-service
 * eventing contract (Nacos + RocketMQ / Spring Cloud Bus) is wired in P4.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeTransferredEvent {
    private Long employeeId;
    private Long fromDeptId;
    private Long toDeptId;
    private Long fromPostId;
    private Long toPostId;
    private LocalDate effectiveAt;
    private Long operatorUserId;
    private Long tenantId;
}
