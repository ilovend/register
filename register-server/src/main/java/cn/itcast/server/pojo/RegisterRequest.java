package cn.itcast.server.pojo;

import lombok.Data;

import java.io.Serializable;

/**
 * 注册请求
 */
@Data
public class RegisterRequest implements Serializable {

    private String serviceName;
    private String ip;
    private Integer port;
    private String serviceInstanceId;
    /**
     * 服务所在机器的主机名
     */
    private String hostname;

}