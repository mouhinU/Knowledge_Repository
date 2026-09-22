package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 考生注册命令
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Getter
@Setter
public class StudentRegisterCmd {

    private String username;
    private String password;
    private String displayName;
    private String studentNo;
}
