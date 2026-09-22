package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 考生登录命令
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Getter
@Setter
public class StudentLoginCmd {

    private String username;
    private String password;
}
