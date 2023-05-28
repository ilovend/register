package cn.itcast.server.pojo;

import lombok.Data;

@Data
public class ServiceInstance {
    private String serviceName;
    private Integer port;
    private String ip;
    private String host;
    private String serviceInstanceId;

    private Lease lease = new Lease();

    public void renew(){
        this.lease.lastHeartBeatTime = System.currentTimeMillis();

        System.out.println(this.serviceName + " " + this.serviceInstanceId + " 续约成功");
    }

    /**
     * 契约
     *
     */
    private class Lease {
        private Long lastHeartBeatTime = System.currentTimeMillis();
    }
}