package cn.itcast.server.pojo;

import lombok.Data;

@Data
public class HeartBeatResponse {

    public static final Integer SUCCESS = 200;
    public static final Integer FAIL = 500;

    private Integer status;

    private String msg;
}