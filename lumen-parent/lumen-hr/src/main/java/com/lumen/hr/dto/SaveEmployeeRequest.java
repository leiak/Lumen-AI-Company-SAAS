package com.lumen.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveEmployeeRequest {

    /** null = create; non-null = update. */
    private Long id;

    private Long userId;

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    /** 身份证号（明文传入，按字段级处理器透明加密）。 */
    private String idCardEnc;

    /** 手机号（明文传入，按字段级处理器透明加密）。 */
    private String mobileEnc;

    @NotNull(message = "deptId is required")
    private Long deptId;

    @NotNull(message = "postId is required")
    private Long postId;

    private Long levelId;

    /** 0=在职 1=试用期 2=离职 3=停薪留职 */
    private Integer status;
}
