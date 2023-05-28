package cn.itcast.server.pojo;

import lombok.Data;

/**
 * 心跳的请求
 */
@Data
public class HeartBeatRequest {
    private String serviceInstanceId;

    private String serviceName;
}