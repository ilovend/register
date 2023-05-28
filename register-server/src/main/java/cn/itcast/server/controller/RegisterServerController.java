package cn.itcast.server.controller;

import cn.itcast.server.pojo.*;
import cn.itcast.server.registry.Registry;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/server")
public class RegisterServerController {
    /**
     * 注册请求
     */
    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request){
        //1. 把请求对象转换为ServiceInstance 对象
        ServiceInstance serviceInstance = new ServiceInstance();
        BeanUtils.copyProperties(request, serviceInstance);

        //2. 获取单例的Registry注册中心对象
        Registry registry = Registry.getInstance();

        //3. 向注册中心中添加一个新的实列
        registry.register(serviceInstance);

        //4. 构建返回值
        RegisterResponse response = new RegisterResponse();
        response.setStatus(RegisterResponse.SUCCESS);
        response.setMsg("注册成功");

        return response;
    }

    /**
     * 心跳续约
     */
    @PostMapping("/heartBeat")
    public HeartBeatResponse heartBeat(@RequestBody HeartBeatRequest request){
        //1. 获取参数中的服务名称和id
        String serviceName = request.getServiceName();
        String serviceInstanceId = request.getServiceInstanceId();

        //2. 获取单例的Registry注册中心对象
        Registry registry = Registry.getInstance();

        //3. 从注册中心中获取一个服务的实列
        ServiceInstance serviceInstance = registry.getServiceInstance(serviceName, serviceInstanceId);

        //4. 判断获取的实列是否为空 如果不为空则续约
        if(null != serviceInstance){
            serviceInstance.renew();
        }

        //5. 构建返回的结果
        HeartBeatResponse response = new HeartBeatResponse();
        response.setStatus(HeartBeatResponse.SUCCESS);
        response.setMsg("续约成功");
        return response;
    }
}
