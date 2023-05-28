package cn.itcast.server.pojo;

import lombok.Data;

/**
 * 注册响应
 */
@Data
public class RegisterResponse {

    public static final Integer SUCCESS = 200;
    public static final Integer FAIL = 500;

    private Integer status;

    private String msg;
}